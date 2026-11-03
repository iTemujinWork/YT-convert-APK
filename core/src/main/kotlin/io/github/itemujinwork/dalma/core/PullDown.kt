package io.github.itemujinwork.dalma.core

/**
 * The pull-down's arithmetic (the brief's "Lehúzás"): one gesture, two zones, one threshold.
 * The content follows the finger one to one up to the mark, then with resistance; what letting
 * go does depends on how far the finger went. All in the same unit, dp or px.
 */
object PullDown {
    /** The threshold: past it the search opens. */
    const val MARK_DP = 96f

    /** Shorter than this is nothing at all, so a scroll's overshoot never closes a list. */
    const val DEAD_ZONE_DP = 16f

    /** Past the mark the content moves this much per finger: "from here there is nowhere to go". */
    const val RESISTANCE = 0.4f

    enum class Outcome { Nothing, Close, Search }

    /** Where the content is under a finger that has travelled [finger]: 150 dp of finger is 118 dp of content. */
    fun content(finger: Float, mark: Float): Float {
        val f = finger.coerceAtLeast(0f)
        return if (f <= mark) f else mark + (f - mark) * RESISTANCE
    }

    /** What letting go at [finger] does; [canClose] is false on "Most szól", where a short pull just springs back. */
    fun outcome(finger: Float, mark: Float, deadZone: Float, canClose: Boolean): Outcome = when {
        finger >= mark -> Outcome.Search
        canClose && finger >= deadZone -> Outcome.Close
        else -> Outcome.Nothing
    }
}
