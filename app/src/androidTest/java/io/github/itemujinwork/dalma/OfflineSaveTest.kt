package io.github.itemujinwork.dalma

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.itemujinwork.dalma.core.EngineEvent
import io.github.itemujinwork.dalma.core.EngineOutput
import io.github.itemujinwork.dalma.core.EngineRequest
import io.github.itemujinwork.dalma.core.FileNames
import io.github.itemujinwork.dalma.core.ProgressTracker
import io.github.itemujinwork.dalma.core.SavedSong
import io.github.itemujinwork.dalma.core.Target
import io.github.itemujinwork.dalma.core.YtDlpArgs
import io.github.itemujinwork.dalma.data.Library
import io.github.itemujinwork.dalma.data.MusicStore
import io.github.itemujinwork.dalma.engine.Engine
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The whole chain on a real Android, without YouTube: the packaged yt-dlp and ffmpeg
 * download from a local server, keep the Opus stream, write tags and a square cover,
 * and the file lands in the Music folder where the system reads the tags back.
 */
@RunWith(AndroidJUnit4::class)
class OfflineSaveTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val videoId = "dQw4w9WgXcQ"

    private fun mediaFolder(): File {
        val folder = File(context.cacheDir, "test-media").apply { mkdirs() }
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        for (name in listOf("audio_opus.webm", "audio_aac.m4a", "thumb.webp")) {
            assets.open(name).use { input -> File(folder, name).outputStream().use { input.copyTo(it) } }
        }
        return folder
    }

    private fun infoJson(dir: File, media: File, port: Int, withOpus: Boolean): File {
        fun format(id: String, file: String, ext: String, acodec: String, abr: Int) = JSONObject().apply {
            put("format_id", id)
            put("url", "http://127.0.0.1:$port/$file")
            put("ext", ext)
            put("vcodec", "none")
            put("acodec", acodec)
            put("protocol", "http")
            put("filesize", File(media, file).length())
            put("abr", abr)
        }
        val formats = JSONArray().apply {
            if (withOpus) put(format("251", "audio_opus.webm", "webm", "opus", 160))
            put(format("140", "audio_aac.m4a", "m4a", "mp4a.40.2", 128))
        }
        val info = JSONObject().apply {
            put("id", videoId)
            put("title", "Árvíztűrő Teszt Dal (Official Video)")
            put("uploader", "Teszt Előadó - Topic")
            put("channel", "Teszt Előadó - Topic")
            put("album", "Teszt Album")
            put("duration", 2)
            put("live_status", "not_live")
            put("upload_date", "20240101")
            put("webpage_url", "https://www.youtube.com/watch?v=$videoId")
            put("extractor", "youtube")
            put("extractor_key", "Youtube")
            put("_type", "video")
            put("formats", formats)
            put("thumbnails", JSONArray().put(JSONObject().apply { put("id", "0"); put("url", "http://127.0.0.1:$port/thumb.webp"); put("preference", 0) }))
        }
        return File(dir, "info.json").apply { writeText(info.toString()) }
    }

    private fun run(withOpus: Boolean): Pair<File, EngineEvent.Meta> = runBlocking {
        val engine = Engine(context)
        engine.prepare()
        engine.cleanWorkDir(keepPartials = false)
        val media = mediaFolder()
        TinyHttpServer(media).use { server ->
            val request = EngineRequest(Target.Video(videoId), engine.workDir.absolutePath, null)
            val args = YtDlpArgs.build(request).dropLast(2) + listOf("--load-info-json", infoJson(engine.workDir, media, server.port, withOpus).absolutePath)
            val events = mutableListOf<EngineEvent>()
            val lines = mutableListOf<String>()
            val result = engine.run(args) { line ->
                lines += line
                EngineOutput.parse(line)?.let(events::add)
            }
            assertEquals("stdout: $lines\nstderr: ${result.stderr}", 0, result.exitCode)
            val tracker = ProgressTracker()
            events.forEach(tracker::onEvent)
            assertEquals(1f, tracker.fraction!!, 0.001f)
            val output = File(events.filterIsInstance<EngineEvent.Output>().single().path)
            assertTrue(output.path, output.isFile)
            output to events.filterIsInstance<EngineEvent.Meta>().single()
        }
    }

    @Test
    fun opusWithTagsAndCoverLandsInMusic() = runBlocking {
        val (output, meta) = run(withOpus = true)
        assertEquals("opus", output.extension)
        assertEquals("Árvíztűrő Teszt Dal", meta.title)
        assertEquals("Teszt Előadó", meta.artist)

        val store = MusicStore(context)
        val name = FileNames.forTrack(meta.artist, meta.title, videoId, output.extension)
        assertEquals("Teszt Előadó – Árvíztűrő Teszt Dal.opus", name)
        val uri = store.save(output, name)
        try {
            assertTrue(store.exists(uri))
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                assertEquals("Árvíztűrő Teszt Dal", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
                assertEquals("Teszt Előadó", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
                assertEquals("Teszt Album", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM))
                assertNotNull("cover art readable by the system", retriever.embeddedPicture)
            }

            val library = Library(context)
            library.add(SavedSong(videoId = videoId, uri = uri.toString(), fileName = name, title = meta.title, artist = meta.artist, savedAtMillis = 1))
            assertNotNull(library.findExisting(videoId))
            val archive = File(context.cacheDir, "archive.txt")
            library.writeArchive(archive)
            assertEquals("youtube $videoId\n", archive.readText())
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
        // The file is gone now, so Dalma must save it again next time.
        assertNull(Library(context).findExisting(videoId))
        output.delete()
    }

    @Test
    fun withoutOpusTheAacStreamIsKept() {
        val (output, _) = run(withOpus = false)
        assertEquals("m4a", output.extension)
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(output.absolutePath)
            assertEquals("Árvíztűrő Teszt Dal", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
            assertNotNull(retriever.embeddedPicture)
        }
        output.delete()
    }
}
