package io.github.itemujinwork.dalma.data

import android.content.Context
import android.os.Build
import io.github.itemujinwork.dalma.BuildConfig
import io.github.itemujinwork.dalma.core.Failure
import io.github.itemujinwork.dalma.core.Outcome
import io.github.itemujinwork.dalma.core.Strategy
import io.github.itemujinwork.dalma.core.Target
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * The story of the last save, for the hidden about card. Dalma says one sentence; this file
 * keeps the reasons (every attempt, what yt-dlp said, which remedies were tried), so a
 * failure can be understood without a computer and a cable. Rewritten by every save.
 */
class Diagnostics(context: Context) {
    private val app = context.applicationContext
    private val log = File(app.filesDir, "last-save.log")

    /** One line, present only while the last save failed; the about card shows it. */
    private val summary = File(app.filesDir, "last-failure.txt")

    fun start(target: Target, engineVersion: String?, engineUpdated: Boolean) {
        val text = buildString {
            appendLine("Dalma ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.SUPPORTED_ABIS.firstOrNull()}")
            appendLine("yt-dlp ${engineVersion ?: "?"} (${if (engineUpdated) "updated" else "shipped"})")
            appendLine("target: ${target.canonicalUrl}")
            appendLine("started: ${now()}")
        }
        runCatching { log.writeText(text) }
    }

    fun note(text: String) = append("${now()} $text\n")

    fun attempt(number: Int, strategy: Strategy, exitCode: Int, failure: Failure, stderr: String) = append(
        buildString {
            appendLine("--- attempt $number failed at ${now()}: ${failure.kind} (${failure.detail})")
            appendLine("exit code $exitCode, $strategy")
            appendLine(stderr.trimEnd().takeLast(MAX_STDERR_CHARS))
        },
    )

    fun crash(error: Throwable) = append("--- unexpected at ${now()}\n${error.stackTraceToString().take(MAX_STDERR_CHARS)}\n")

    fun finish(outcome: Outcome) {
        append("=== ${now()} $outcome\n")
        runCatching {
            if (outcome is Outcome.Failed) summary.writeText(outcome.kind.name + (outcome.detail?.let { ": $it" } ?: ""))
            else summary.delete()
        }
    }

    fun lastFailure(): String? = summary.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }

    /** The log, from the end, small enough to travel in a share intent. */
    fun read(): String? = log.takeIf { it.isFile }?.readText()?.takeLast(MAX_SHARE_CHARS)?.takeIf { it.isNotBlank() }

    private fun append(text: String) {
        runCatching {
            if (log.length() < MAX_LOG_BYTES) log.appendText(text)
        }
    }

    private fun now(): String = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private companion object {
        const val MAX_STDERR_CHARS = 40_000
        const val MAX_LOG_BYTES = 400_000L
        const val MAX_SHARE_CHARS = 150_000
    }
}
