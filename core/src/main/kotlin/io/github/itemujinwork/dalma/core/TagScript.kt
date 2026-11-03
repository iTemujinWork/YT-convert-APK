package io.github.itemujinwork.dalma.core

/**
 * The few lines of Python that write a song's tags into its own file with mutagen, the library
 * yt-dlp itself uses for the cover of an .opus and which ships with the engine's Python. An Opus
 * gets Vorbis comments and a METADATA_BLOCK_PICTURE (mutagen.readthedocs.io/en/latest/user/vcomment.html),
 * an AAC its iTunes tags (mutagen.readthedocs.io/en/latest/api/mp4.html). The arguments: the
 * file, the title, the artist (or nothing), the cover's JPEG (or nothing). Exit 0 once written.
 */
object TagScript {
    val SOURCE: String = """
        import base64, sys
        from mutagen.flac import Picture
        from mutagen.mp4 import MP4, MP4Cover
        from mutagen.oggopus import OggOpus

        path, title, artist, cover = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
        image = open(cover, 'rb').read() if cover else None
        if path.lower().endswith('.opus'):
            tags = OggOpus(path)
            tags['title'] = [title]
            if artist:
                tags['artist'] = [artist]
            else:
                tags.pop('artist', None)
            if image is not None:
                picture = Picture()
                picture.data = image
                picture.type = 3
                picture.mime = 'image/jpeg'
                tags['metadata_block_picture'] = [base64.b64encode(picture.write()).decode('ascii')]
            tags.save()
        else:
            tags = MP4(path)
            tags['\xa9nam'] = [title]
            if artist:
                tags['\xa9ART'] = [artist]
            else:
                tags.pop('\xa9ART', None)
            if image is not None:
                tags['covr'] = [MP4Cover(image, imageformat=MP4Cover.FORMAT_JPEG)]
            tags.save()
    """.trimIndent()

    /** The arguments after the source: a blank artist takes the tag away, no cover leaves the picture as it is. */
    fun arguments(path: String, title: String, artist: String?, coverPath: String?): List<String> =
        listOf(path, title, artist?.trim().orEmpty(), coverPath.orEmpty())
}
