package io.github.itemujinwork.dalma.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FailuresTest {
    private fun kind(stderr: String) = ErrorClassifier.classify(stderr).kind

    @Test
    fun `classifies real yt-dlp messages`() {
        val cases = mapOf(
            "ERROR: [youtube] dQw4w9WgXcQ: Sign in to confirm you’re not a bot. Use --cookies-from-browser" to FailureKind.BOT_CHECK,
            "ERROR: [youtube] dQw4w9WgXcQ: Sign in to confirm your age. This video may be inappropriate for some users." to FailureKind.AGE_RESTRICTED,
            "ERROR: [youtube] dQw4w9WgXcQ: Private video. Sign in if you've been granted access to this video" to FailureKind.PRIVATE,
            "ERROR: [youtube:tab] PLabc: The playlist does not exist." to FailureKind.PRIVATE,
            "ERROR: [youtube] dQw4w9WgXcQ: Join this channel to get access to members-only content like this video" to FailureKind.MEMBERS_ONLY,
            "ERROR: [youtube] dQw4w9WgXcQ: The uploader has not made this video available in your country" to FailureKind.REGION_BLOCKED,
            "ERROR: [youtube] dQw4w9WgXcQ: Video unavailable. This video contains content from SME, who has blocked it in your country on copyright grounds" to FailureKind.REGION_BLOCKED,
            "ERROR: [youtube] dQw4w9WgXcQ: This live event will begin in 3 hours." to FailureKind.LIVE,
            "ERROR: [youtube] dQw4w9WgXcQ: Video unavailable. This video has been removed by the uploader" to FailureKind.UNAVAILABLE,
            "ERROR: [youtube] dQw4w9WgXcQ: Video unavailable. This content isn't available, try again later." to FailureKind.THROTTLED,
            "ERROR: unable to download video data: HTTP Error 403: Forbidden" to FailureKind.FORBIDDEN,
            "ERROR: unable to download video data: HTTP Error 404: Not Found" to FailureKind.FORBIDDEN,
            "ERROR: unable to download video data: HTTP Error 429: Too Many Requests" to FailureKind.THROTTLED,
            "ERROR: unable to download video data: HTTP Error 503: Service Unavailable" to FailureKind.THROTTLED,
            "ERROR: [youtube] dQw4w9WgXcQ: Requested format is not available. Use --list-formats" to FailureKind.FORMAT_UNAVAILABLE,
            "ERROR: [youtube] dQw4w9WgXcQ: Unable to extract yt initial data; please report this issue on https://github.com/yt-dlp/yt-dlp/issues" to FailureKind.ENGINE_OUTDATED,
            "ERROR: [youtube] dQw4w9WgXcQ: Unable to download API page: <urlopen error [Errno 7] No address associated with hostname> (caused by TransportError)" to FailureKind.NO_INTERNET,
            "ERROR: Postprocessing: Conversion failed!" to FailureKind.PROCESSING,
            "ERROR: unable to write data: [Errno 28] No space left on device" to FailureKind.DISK_FULL,
            "Traceback (most recent call last):\n  File \"yt_dlp/__main__.py\"\nModuleNotFoundError: No module named 'yt_dlp.compat'" to FailureKind.ENGINE_BROKEN,
            "something nobody has seen before" to FailureKind.UNKNOWN,
        )
        for ((stderr, expected) in cases) assertEquals(stderr, expected, kind(stderr))
    }

    @Test
    fun `warnings do not outvote the error line`() {
        val stderr = """
            WARNING: [youtube] Some formats may be missing
            WARNING: ffmpeg not found
            ERROR: [youtube] dQw4w9WgXcQ: Private video. Sign in if you've been granted access
        """.trimIndent()
        assertEquals(FailureKind.PRIVATE, kind(stderr))
    }

    @Test
    fun `detail is short and readable`() {
        val failure = ErrorClassifier.classify("WARNING: x\nERROR: [youtube] dQw4w9WgXcQ: Private video. Sign in if you've been granted access")
        assertEquals("Private video. Sign in if you've been granted access", failure.detail)
    }

    @Test
    fun `final failures are never retried`() {
        for (kind in FailureKind.entries.filter { it.isFinal }) {
            assertEquals(Decision.GiveUp, RetryPlanner.decide(kind, 1, emptySet(), engineRecentlyUpdated = false))
        }
    }

    @Test
    fun `a broken extractor first updates the engine, then asks other clients`() {
        val applied = mutableSetOf<Remedy>()
        val first = RetryPlanner.decide(FailureKind.ENGINE_OUTDATED, 1, applied, engineRecentlyUpdated = false)
        assertEquals(Decision.Apply(Remedy.UPDATE_ENGINE), first)
        applied += Remedy.UPDATE_ENGINE
        assertEquals(Decision.Apply(Remedy.OTHER_CLIENTS), RetryPlanner.decide(FailureKind.FORBIDDEN, 2, applied, false))
        applied += Remedy.OTHER_CLIENTS
        assertEquals(Decision.Apply(Remedy.ANY_FORMAT), RetryPlanner.decide(FailureKind.FORBIDDEN, 3, applied, false))
        applied += Remedy.ANY_FORMAT
        assertEquals(Decision.GiveUp, RetryPlanner.decide(FailureKind.FORBIDDEN, 4, applied, false))
    }

    @Test
    fun `a freshly updated engine is not updated again`() {
        assertEquals(
            Decision.Apply(Remedy.OTHER_CLIENTS),
            RetryPlanner.decide(FailureKind.ENGINE_OUTDATED, 1, emptySet(), engineRecentlyUpdated = true),
        )
    }

    @Test
    fun `no internet retries once, then waits for the network`() {
        assertEquals(Decision.Apply(Remedy.RETRY_SOON), RetryPlanner.decide(FailureKind.NO_INTERNET, 1, emptySet(), false))
        assertEquals(Decision.WaitForNetwork, RetryPlanner.decide(FailureKind.NO_INTERNET, 2, setOf(Remedy.RETRY_SOON), false))
    }

    @Test
    fun `processing trouble falls back to simple processing, broken engine to a reset`() {
        assertEquals(Decision.Apply(Remedy.SIMPLE_PROCESSING), RetryPlanner.decide(FailureKind.PROCESSING, 1, emptySet(), false))
        assertEquals(Decision.Apply(Remedy.RESET_ENGINE), RetryPlanner.decide(FailureKind.ENGINE_BROKEN, 1, emptySet(), false))
        assertEquals(Decision.GiveUp, RetryPlanner.decide(FailureKind.ENGINE_BROKEN, 2, setOf(Remedy.RESET_ENGINE), false))
    }

    @Test
    fun `attempts are capped`() {
        assertEquals(Decision.GiveUp, RetryPlanner.decide(FailureKind.UNKNOWN, RetryPlanner.MAX_ATTEMPTS, emptySet(), false))
    }
}
