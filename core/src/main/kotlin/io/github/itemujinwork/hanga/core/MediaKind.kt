package io.github.itemujinwork.hanga.core

import kotlinx.serialization.Serializable

/** What the user wants to keep from a video. */
@Serializable
enum class MediaKind(val extension: String, val mimeType: String) {
    /** Music: the best audio track, converted to MP3 with cover art. */
    AUDIO("mp3", "audio/mpeg"),

    /** Video: up to 1080p, H.264 + AAC in MP4, so every player can open it. */
    VIDEO("mp4", "video/mp4"),
}
