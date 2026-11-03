package io.github.itemujinwork.dalma.core

/**
 * One of the idle motions the designer wrote into the pose files as SMIL animations, in the
 * points of the 200-point drawing: the whole figure rising (or, against the wall, sinking) and,
 * while she listens, the head rocking with the back hair and the fringe following it a little
 * late. Every value runs from one end to the other and back over one period, each half along
 * the files' cubic-bezier(.45, 0, .55, 1); a lag is a phase, so nothing jumps when it starts.
 */
class IdleMotion(
    /** How far the whole figure moves by half a period, in points: negative is up. */
    val rise: Float,
    val periodMillis: Int,
    /** The head's swing about the chin, in degrees, from -head to +head. */
    val head: Float = 0f,
    /** The back hair's swing about the nape, and how far behind the head it runs. */
    val hair: Float = 0f,
    val hairLagMillis: Int = 0,
    /** The fringe's swing about the top of the head, and its lag. */
    val fringe: Float = 0f,
    val fringeLagMillis: Int = 0,
) {
    /** 0 → 1 → 0 over one period, [lagMillis] behind the clock. */
    fun wave(millis: Long, lagMillis: Int = 0): Float {
        val period = periodMillis.toLong()
        val t = ((millis - lagMillis) % period + period) % period
        val half = period / 2f
        return if (t < half) ease(t / half) else 1f - ease((t - half) / half)
    }

    /** -[amplitude] → +[amplitude] → -[amplitude] over one period: the swings. */
    fun swing(amplitude: Float, millis: Long, lagMillis: Int = 0): Float = amplitude * (2f * wave(millis, lagMillis) - 1f)

    companion object {
        /** hallgat.svg: 3 points up and back in 2.4 s; the head ±2°, the back hair ±2.8° 0.3 s behind it, the fringe ±2.4° 0.42 s behind. */
        val LISTEN_DAY = IdleMotion(rise = -3f, periodMillis = 2400, head = 2f, hair = 2.8f, hairLagMillis = 300, fringe = 2.4f, fringeLagMillis = 420)

        /** hallgat-este.svg: 1.2 points in 4.2 s; the head ±1.2°, the hair ±1.7° 0.5 s behind, the fringe ±1.4° 0.7 s behind. */
        val LISTEN_NIGHT = IdleMotion(rise = -1.2f, periodMillis = 4200, head = 1.2f, hair = 1.7f, hairLagMillis = 500, fringe = 1.4f, fringeLagMillis = 700)

        /** alszik-fal.svg: against the wall she sinks 1.8 points and comes back, in 5.5 s. */
        val ASLEEP = IdleMotion(rise = 1.8f, periodMillis = 5500)

        /** Standing: she breathes, 1.2 points up and down in 4.5 s (the brief's "1,2 dp fel-le, 4–5 s"). */
        val BREATHE = IdleMotion(rise = -1.2f, periodMillis = 4500)

        /** The files' keySplines 0.45 0 0.55 1 as a timing function: the fraction of the time in, the fraction of the way out. */
        fun ease(x: Float): Float {
            if (x <= 0f) return 0f
            if (x >= 1f) return 1f
            // The curve's x(s) for the control points' x at 0.45 and 0.55, solved for s by Newton's method.
            var s = x.toDouble()
            repeat(6) {
                val u = 1 - s
                val xs = 3 * X1 * u * u * s + 3 * X2 * u * s * s + s * s * s
                val slope = 3 * X1 * u * (1 - 3 * s) + 3 * X2 * s * (2 - 3 * s) + 3 * s * s
                if (slope > 1e-6) s -= (xs - x) / slope
            }
            s = s.coerceIn(0.0, 1.0)
            // y(s), with the control points' y at 0 and 1.
            return (3 * s * s * (1 - s) + s * s * s).toFloat()
        }

        private const val X1 = 0.45
        private const val X2 = 0.55
    }
}
