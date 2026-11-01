package io.github.itemujinwork.dalma.core

/** The time under the line while a finger is on it: "1:48", and "1:01:48" for a long recording. */
object TimeText {
    fun clock(millis: Long): String {
        val total = millis.coerceAtLeast(0L) / 1000
        val hours = total / 3600
        val minutes = total % 3600 / 60
        val seconds = total % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }

    /** What is left, with the minus sign the mock-up shows: "-2:07". */
    fun remaining(positionMillis: Long, durationMillis: Long): String = "-" + clock(durationMillis - positionMillis)
}
