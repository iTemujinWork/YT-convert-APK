package io.github.itemujinwork.dalma.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongPaletteTest {
    private fun hex(colour: Int) = "#%08X".format(colour)

    @Test
    fun mixingIsPerChannel() {
        assertEquals("#FF000000", hex(SongPalette.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0f)))
        assertEquals("#FFFFFFFF", hex(SongPalette.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 1f)))
        assertEquals("#FF808080", hex(SongPalette.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f)))
        assertEquals("#FF400000", hex(SongPalette.mix(0xFF000000.toInt(), 0xFFFF0000.toInt(), 0.25f)))
    }

    @Test
    fun hslHitsTheCorners() {
        assertEquals("#FFFF0000", hex(SongPalette.hsl(0f, 1f, 0.5f)))
        assertEquals("#FF00FF00", hex(SongPalette.hsl(120f, 1f, 0.5f)))
        assertEquals("#FF0000FF", hex(SongPalette.hsl(240f, 1f, 0.5f)))
        assertEquals("#FF808080", hex(SongPalette.hsl(0f, 0f, 0.5f)))
        assertEquals(hex(SongPalette.hsl(30f, 0.5f, 0.5f)), hex(SongPalette.hsl(390f, 0.5f, 0.5f)))
    }

    @Test
    fun theSheetsMixes() {
        // The sunrise cover of the sheet: its sky is the dominant colour, its sun the vivid one.
        val palette = SongPalette(dominant = 0xFFC66E2C.toInt(), vivid = 0xFFF7C15C.toInt())
        // 32 % of the dominant colour in the night, 22 % in the evening.
        assertEquals("#FF49361F", hex(palette.top(evening = false))) // the sheet's "háttér #49361F"
        assertEquals("#FF362D1D", hex(palette.top(evening = true)))
        // Behind the list: a tenth.
        assertEquals("#FF20231B", hex(palette.tint(0.10f)))
        // The vivid colour with a quarter of white: the sheet's "csík #F9D085", one step of rounding apart.
        assertEquals("#FFF9D185", hex(palette.walkedLine()))
        assertEquals("#FFCE824A", hex(palette.coverLight()))
        assertEquals("#FF8F5526", hex(palette.coverDark()))
    }

    @Test
    fun anArtistAlwaysGetsTheSameColoursAndNoArtistGetsSome() {
        val a = SongPalette.fromArtist("Kóbor Lámpás")
        assertEquals(a, SongPalette.fromArtist("Kóbor Lámpás"))
        assertNotEquals(a, SongPalette.fromArtist("Ibolya"))
        val none = SongPalette.fromArtist(null)
        assertEquals(none, SongPalette.fromArtist(""))
        assertTrue(SongPalette.luminance(a.vivid) > SongPalette.luminance(a.dominant))
    }

    @Test
    fun aVividColourTooCloseToTheDominantOneIsLightened() {
        val dark = 0xFF202830.toInt()
        val nearlyTheSame = 0xFF283038.toInt()
        val fixed = SongPalette.readable(dark, nearlyTheSame)
        assertEquals(dark, fixed.dominant)
        assertTrue(SongPalette.luminance(fixed.vivid) - SongPalette.luminance(dark) >= 0.25f)
        // Already apart: untouched.
        assertEquals(0xFFF7C15C.toInt(), SongPalette.readable(0xFFC66E2C.toInt(), 0xFFF7C15C.toInt()).vivid)
    }
}
