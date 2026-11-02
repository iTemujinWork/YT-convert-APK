package io.github.itemujinwork.dalma.core

/**
 * The opening of the app, in milliseconds from the first frame, as the brief's table has it.
 * Dalma appears big, greets, has a breath of air, then steps into the corner while the player
 * comes in piece by piece. With no music the first two seconds happen and that is all.
 */
object StartTimeline {
    data class Span(val startMillis: Int, val endMillis: Int) {
        val durationMillis: Int get() = endMillis - startMillis
    }

    /** 0 → 1 opacity, 0.96 → 1 scale, Dalma at 440 dp: exactly as on the empty home screen. */
    val appear = Span(0, 600)

    /** The greeting as motion: the head tilts, the hair follows, a wink, and back. */
    val greet = Span(800, 2000)

    /** She stands and looks at you: air between two motions. */
    val air = Span(2000, 3000)

    /** Into the bottom-left corner, shrinking to 190 dp about that corner; the sky takes the song's colour. */
    val corner = Span(3000, 3700)

    /** The cover: 0 → 1 opacity, 0.92 → 1 scale. */
    val cover = Span(3300, 3900)

    /** Title and artist: 0 → 1, rising 8 dp. */
    val texts = Span(3600, 4100)

    /** The line draws itself from left to right. */
    val line = Span(3900, 4500)

    /** In the corner the greeting figure becomes the listening one (eyes open by day, closed at night). */
    val listen = Span(4200, 4800)

    /** Without music the show ends here. */
    const val WITHOUT_MUSIC_MILLIS = 2000

    /** The figure's size on the empty screen and in the player's corner, in dp. */
    const val BIG_DP = 440
    const val SMALL_DP = 190
}
