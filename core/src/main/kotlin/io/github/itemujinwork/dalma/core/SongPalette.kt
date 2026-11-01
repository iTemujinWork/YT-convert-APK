package io.github.itemujinwork.dalma.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A song's two colours, as 0xAARRGGBB: the dominant one tints the background, the vivid one
 * the walked part of the line. From the cover when there is one (the app's Palette), from the
 * artist's name when there is none. Dalma's mint never follows them.
 */
data class SongPalette(val dominant: Int, val vivid: Int) {
    /** The top of the background: a little of the dominant colour in the night, less in the evening. */
    fun top(evening: Boolean): Int = tint(if (evening) 0.22f else 0.32f)

    /** [amount] of the dominant colour in the night: 0.32 behind the cover, 0.10 behind the list. */
    fun tint(amount: Float): Int = mix(NIGHT, dominant, amount)

    /**
     * The walked part of the line: the vivid colour lightened with a quarter of white. (The
     * sheet's numbers say so: the sunrise cover's sun #F7C15C becomes the line's #F9D085.)
     */
    fun walkedLine(): Int = mix(vivid, WHITE, 0.25f)

    /** The square that stands in for a missing cover: light, dominant, dark, from the top-left corner. */
    fun coverLight(): Int = mix(WHITE, dominant, 0.86f)
    fun coverDark(): Int = mix(NIGHT, dominant, 0.70f)

    companion object {
        const val NIGHT = 0xFF0E1B19.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()

        /** Without a cover: a hue from the artist's name (the same name, the same colour, on every phone). */
        fun fromArtist(artist: String?): SongPalette {
            val hue = ((artist.orEmpty().hashCode() % 360) + 360) % 360
            return SongPalette(dominant = hsl(hue.toFloat(), 0.38f, 0.30f), vivid = hsl((hue + 22) % 360f, 0.60f, 0.70f))
        }

        /**
         * Palette can hand back two near-identical colours on a dark cover; the line would vanish.
         * The vivid colour is lightened with white until it stands apart from the dominant one.
         */
        fun readable(dominant: Int, vivid: Int): SongPalette {
            var bright = vivid
            var rounds = 0
            while (abs(luminance(bright) - luminance(dominant)) < 0.25f && rounds < 6) {
                bright = mix(bright, WHITE, 0.2f)
                rounds++
            }
            return SongPalette(dominant, bright)
        }

        /** [amount] of [colour] over [base], per channel, alpha kept opaque. */
        fun mix(base: Int, colour: Int, amount: Float): Int {
            val t = amount.coerceIn(0f, 1f)
            fun channel(shift: Int): Int {
                val a = (base shr shift) and 0xFF
                val b = (colour shr shift) and 0xFF
                return (a + (b - a) * t).roundToInt().coerceIn(0, 255)
            }
            return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }

        /** 0 (black) to 1 (white), the way the eye weighs red, green and blue. */
        fun luminance(colour: Int): Float {
            val r = ((colour shr 16) and 0xFF) / 255f
            val g = ((colour shr 8) and 0xFF) / 255f
            val b = (colour and 0xFF) / 255f
            return 0.2126f * r + 0.7152f * g + 0.0722f * b
        }

        /** Hue in degrees, saturation and lightness 0..1, to an opaque colour. */
        fun hsl(hue: Float, saturation: Float, lightness: Float): Int {
            val c = (1f - abs(2f * lightness - 1f)) * saturation
            val h = ((hue % 360f) + 360f) % 360f / 60f
            val x = c * (1f - abs(h % 2f - 1f))
            val (r1, g1, b1) = when {
                h < 1f -> Triple(c, x, 0f)
                h < 2f -> Triple(x, c, 0f)
                h < 3f -> Triple(0f, c, x)
                h < 4f -> Triple(0f, x, c)
                h < 5f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            val m = lightness - c / 2f
            fun byte(v: Float) = ((v + m) * 255f).roundToInt().coerceIn(0, 255)
            return (0xFF shl 24) or (byte(r1) shl 16) or (byte(g1) shl 8) or byte(b1)
        }
    }
}
