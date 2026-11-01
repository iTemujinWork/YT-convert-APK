package io.github.itemujinwork.dalma.core

import kotlinx.serialization.Serializable

/** Why something did not work, in words a person (and the retry logic) can act on. */
@Serializable
enum class FailureKind {
    NO_INTERNET,
    THROTTLED,
    BOT_CHECK,
    FORBIDDEN,
    FORMAT_UNAVAILABLE,
    ENGINE_OUTDATED,
    PROCESSING,
    ENGINE_BROKEN,
    DISK_FULL,
    UNAVAILABLE,
    PRIVATE,
    AGE_RESTRICTED,
    MEMBERS_ONLY,
    REGION_BLOCKED,
    LIVE,
    NOT_YOUTUBE,
    UNKNOWN,
    ;

    /** Nothing we could do on our side changes these. */
    val isFinal: Boolean
        get() = this in setOf(DISK_FULL, UNAVAILABLE, PRIVATE, AGE_RESTRICTED, MEMBERS_ONLY, REGION_BLOCKED, LIVE, NOT_YOUTUBE)
}

data class Failure(val kind: FailureKind, val detail: String)

object ErrorClassifier {
    private class Rule(val kind: FailureKind, vararg needles: String) {
        val needles = needles.map { it.lowercase() }
    }

    // Order matters: the first matching rule wins, most specific first.
    private val rules = listOf(
        Rule(FailureKind.DISK_FULL, "no space left on device", "enospc", "errno 28", "disk full"),
        Rule(
            FailureKind.ENGINE_BROKEN,
            "modulenotfounderror", "no module named", "importerror", "syntaxerror", "zipimport",
            "badzipfile", "is not a zip file", "can't find '__main__'", "fatal python error",
            "failed to initialize", "cannot run program", "error=13, permission denied", "exec format error",
        ),
        Rule(FailureKind.BOT_CHECK, "not a bot", "confirm you’re not", "confirm you're not", "captcha"),
        Rule(FailureKind.AGE_RESTRICTED, "confirm your age", "age-restricted", "age restricted", "inappropriate for some users"),
        Rule(
            FailureKind.MEMBERS_ONLY,
            "members-only", "members only", "join this channel", "channel's members", "requires payment", "youtube premium",
        ),
        Rule(FailureKind.PRIVATE, "private video", "video is private", "this video has been made private", "private playlist", "playlist does not exist"),
        Rule(
            FailureKind.REGION_BLOCKED,
            "not available in your country", "available in your country", "geo restriction", "geo-restrict",
            "blocked it in your country", "not made this video available in your country",
        ),
        Rule(
            FailureKind.LIVE,
            "live event will begin", "premieres in", "is live", "live stream", "this live event", "is_upcoming",
            "live streams are not supported", "currently live",
        ),
        Rule(
            FailureKind.THROTTLED,
            "http error 429", "too many requests", "try again later", "rate-limit", "rate limit",
            // Server side trouble: waiting is the cure.
            "http error 500", "http error 502", "http error 503", "http error 504", "service unavailable", "bad gateway",
        ),
        Rule(
            FailureKind.NO_INTERNET,
            "no address associated with hostname", "temporary failure in name resolution", "name or service not known",
            "failed to resolve", "nodename nor servname", "network is unreachable", "errno 101", "errno 7]",
            "unable to resolve host", "connection refused", "connection reset", "connection aborted",
            "remote end closed connection", "incompleteread", "timed out", "read timed out", "software caused connection abort",
            "network unreachable", "transporterror", "sslerror", "ssl: ", "certificate verify failed",
        ),
        Rule(
            FailureKind.UNAVAILABLE,
            "video unavailable", "video is unavailable", "has been removed", "has been terminated",
            "does not exist", "incomplete youtube id", "copyright claim", "removed by the uploader",
            "this video isn't available", "this video is no longer available",
        ),
        // The stream address was refused or expired: extracting it again usually helps.
        Rule(FailureKind.FORBIDDEN, "http error 403", "403: forbidden", "forbidden", "http error 404", "http error 410"),
        Rule(
            FailureKind.FORMAT_UNAVAILABLE,
            "requested format is not available", "no video formats found", "only images are available",
            "requested formats are incompatible",
        ),
        Rule(
            FailureKind.ENGINE_OUTDATED,
            "unable to extract", "failed to extract", "signature extraction failed", "nsig extraction failed",
            "n challenge", "js challenge", "jsc", "player response", "please report this issue",
            "unable to download api page", "some formats may be missing", "unsupported url",
        ),
        Rule(
            FailureKind.PROCESSING,
            "postprocessing", "ffmpeg", "ffprobe", "conversion failed", "embedthumbnail", "unable to embed",
            "error opening output", "invalid data found when processing input", "audio conversion failed", "mutagen",
        ),
    )

    fun classify(output: String?): Failure {
        val text = output.orEmpty()
        val haystack = relevantLines(text).joinToString("\n").lowercase()
        val kind = rules.firstOrNull { rule -> rule.needles.any { haystack.contains(it) } }?.kind
            ?: if (text.contains("Traceback (most recent call last)")) FailureKind.ENGINE_BROKEN else FailureKind.UNKNOWN
        return Failure(kind, shortDetail(text))
    }

    /**
     * Prefer what yt-dlp itself called an error; failing that, skip its warnings and `[debug]`
     * chatter (a verbose run mentions ffmpeg and formats on every song) and read the rest,
     * typically a traceback.
     */
    private fun relevantLines(text: String): List<String> {
        val lines = text.lines()
        val errors = lines.filter { it.contains("ERROR") }
        if (errors.isNotEmpty()) return errors
        val quiet = lines.filterNot { line ->
            val t = line.trimStart()
            t.startsWith("WARNING") || t.startsWith("[debug]") || t.startsWith("Deleting original file")
        }
        return quiet.ifEmpty { lines }
    }

    /** One readable line for the hidden details, without the "ERROR: [youtube] id:" prefix. */
    fun shortDetail(text: String): String {
        val line = text.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("ERROR:") }
            ?: text.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() }
            ?: return ""
        return line.removePrefix("ERROR:")
            .replace(Regex("""^\s*\[[^\]]+]\s*[A-Za-z0-9_-]{11}:\s*"""), "")
            .trim()
            .take(300)
    }
}

/** A fix that can be tried before the next attempt. */
enum class Remedy {
    /** Same thing again in a few seconds (a blip on the network). */
    RETRY_SOON,

    /** Wait longer, the server asked us to slow down. */
    WAIT_A_BIT,

    /** Download the newest yt-dlp: YouTube changes, yt-dlp follows within days. */
    UPDATE_ENGINE,

    /** Go back to the yt-dlp copy shipped inside the app. */
    RESET_ENGINE,

    /** Ask YouTube with other player clients, over IPv4. */
    OTHER_CLIENTS,

    /** Take whatever audio exists. */
    ANY_FORMAT,

    /** Skip cover art and tags. */
    SIMPLE_PROCESSING,
}

sealed interface Decision {
    data class Apply(val remedy: Remedy) : Decision

    /** Hand the job back to the system: it reruns once the phone is online again. */
    data object WaitForNetwork : Decision

    data object GiveUp : Decision
}

/** Decides, after a failed attempt, what to try next. Pure, so it is easy to test. */
object RetryPlanner {
    const val MAX_ATTEMPTS = 6

    fun decide(
        failure: FailureKind,
        attemptsSoFar: Int,
        applied: Set<Remedy>,
        engineRecentlyUpdated: Boolean,
    ): Decision {
        if (failure.isFinal || attemptsSoFar >= MAX_ATTEMPTS) return Decision.GiveUp
        val ladder = when (failure) {
            FailureKind.NO_INTERNET -> listOf(Remedy.RETRY_SOON)
            FailureKind.THROTTLED -> listOf(Remedy.WAIT_A_BIT, Remedy.OTHER_CLIENTS)
            FailureKind.BOT_CHECK -> listOf(Remedy.UPDATE_ENGINE, Remedy.OTHER_CLIENTS, Remedy.WAIT_A_BIT)
            FailureKind.FORBIDDEN, FailureKind.ENGINE_OUTDATED, FailureKind.UNKNOWN ->
                listOf(Remedy.UPDATE_ENGINE, Remedy.OTHER_CLIENTS, Remedy.ANY_FORMAT)
            FailureKind.FORMAT_UNAVAILABLE -> listOf(Remedy.UPDATE_ENGINE, Remedy.ANY_FORMAT, Remedy.OTHER_CLIENTS)
            FailureKind.PROCESSING -> listOf(Remedy.SIMPLE_PROCESSING)
            FailureKind.ENGINE_BROKEN -> listOf(Remedy.RESET_ENGINE)
            else -> emptyList()
        }
        val next = ladder.firstOrNull { remedy ->
            remedy !in applied && !(remedy == Remedy.UPDATE_ENGINE && engineRecentlyUpdated)
        }
        return when {
            next != null -> Decision.Apply(next)
            failure == FailureKind.NO_INTERNET -> Decision.WaitForNetwork
            else -> Decision.GiveUp
        }
    }

    /** The strategy to use after [remedy]. */
    fun adjust(strategy: Strategy, remedy: Remedy): Strategy = when (remedy) {
        Remedy.OTHER_CLIENTS -> strategy.copy(playerClients = Strategy.OTHER_CLIENTS, forceIpv4 = true)
        Remedy.ANY_FORMAT -> strategy.copy(anyFormat = true)
        Remedy.SIMPLE_PROCESSING -> strategy.copy(simpleProcessing = true)
        Remedy.RETRY_SOON, Remedy.WAIT_A_BIT, Remedy.UPDATE_ENGINE, Remedy.RESET_ENGINE -> strategy
    }

    /** How long to wait before the next attempt. */
    fun delayMillis(remedy: Remedy, timesApplied: Int): Long = when (remedy) {
        Remedy.RETRY_SOON -> 3_000L * (timesApplied + 1)
        Remedy.WAIT_A_BIT -> 20_000L * (timesApplied + 1)
        else -> 0L
    }
}
