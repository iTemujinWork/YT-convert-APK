package io.github.itemujinwork.dalma.play

import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import io.github.itemujinwork.dalma.core.SongPalette

/** A song's two colours: Palette reads them off the cover; without a cover, the artist's name decides. */
object SongColors {
    fun of(cover: Bitmap?, artist: String?): SongPalette {
        if (cover == null) return SongPalette.fromArtist(artist)
        val palette = runCatching { Palette.from(cover).maximumColorCount(16).generate() }.getOrNull()
        val dominant = palette?.dominantSwatch?.rgb ?: return SongPalette.fromArtist(artist)
        val vivid = (palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.lightMutedSwatch ?: palette.mutedSwatch)?.rgb ?: dominant
        return SongPalette.readable(dominant, vivid)
    }
}
