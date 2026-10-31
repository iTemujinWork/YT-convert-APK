package io.github.itemujinwork.dalma.core

/** How a share ended. The app turns this into Dalma's one sentence. */
sealed interface Outcome {
    /** One song, saved. */
    data class Saved(val song: SavedSong) : Outcome

    /** A playlist: what was saved, what we already had, what could not be done. */
    data class SavedMany(val saved: Int, val alreadyHad: Int, val failed: Int, val capped: Boolean) : Outcome

    data object AlreadyHave : Outcome

    data class Failed(val kind: FailureKind, val detail: String?) : Outcome
}

/** The space a download needs, with room for the cover and the Music folder copy. */
object SpaceCheck {
    const val MARGIN_BYTES = 50L * 1024 * 1024

    fun fits(neededBytes: Long?, freeBytes: Long): Boolean {
        if (neededBytes == null) return freeBytes > MARGIN_BYTES
        // The engine keeps the download, writes the tagged copy, then we copy to Music.
        return freeBytes > neededBytes * 3 + MARGIN_BYTES
    }
}
