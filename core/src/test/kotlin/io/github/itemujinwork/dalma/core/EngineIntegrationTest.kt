package io.github.itemujinwork.dalma.core

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Runs the exact command line the app uses against a real yt-dlp and ffmpeg, with a
 * YouTube-like info JSON whose streams are served from localhost. Opt-in:
 *
 *   gradle -p core test -Ddalma.engineTests=true -Ddalma.ytdlp=/path/to/yt-dlp
 */
class EngineIntegrationTest {
    companion object {
        private lateinit var root: File
        private lateinit var media: File
        private var server: HttpServer? = null
        private var port = 0

        private val enabled get() = System.getProperty("dalma.engineTests") == "true"
        private val ytDlp get() = System.getProperty("dalma.ytdlp") ?: "yt-dlp"

        @BeforeClass
        @JvmStatic
        fun setUp() {
            if (!enabled) return
            root = Files.createTempDirectory("dalma-engine").toFile()
            media = File(root, "media").apply { mkdirs() }
            ffmpeg("-f", "lavfi", "-i", "sine=frequency=660:duration=3", "-c:a", "libopus", "-b:a", "128k", "audio_opus.webm")
            ffmpeg("-f", "lavfi", "-i", "sine=frequency=440:duration=3", "-c:a", "aac", "-b:a", "128k", "audio_aac.m4a")
            ffmpeg("-f", "lavfi", "-i", "testsrc=size=1280x720", "-frames:v", "1", "thumb.webp")
            server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/") { exchange ->
                    val file = File(media, exchange.requestURI.path.trimStart('/'))
                    if (!file.isFile || file.parentFile != media) {
                        exchange.sendResponseHeaders(404, -1)
                    } else {
                        exchange.responseHeaders.add("Content-Type", "application/octet-stream")
                        exchange.sendResponseHeaders(200, file.length())
                        exchange.responseBody.use { out -> file.inputStream().use { it.copyTo(out) } }
                    }
                    exchange.close()
                }
                start()
            }
            port = server!!.address.port
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            server?.stop(0)
            if (::root.isInitialized) root.deleteRecursively()
        }

        private fun ffmpeg(vararg args: String) {
            val process = ProcessBuilder(listOf("ffmpeg", "-y", "-loglevel", "error") + args)
                .directory(media).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor(120, TimeUnit.SECONDS) && process.exitValue() == 0) { "ffmpeg failed: $output" }
        }
    }

    private fun url(name: String) = "http://127.0.0.1:$port/$name"

    private fun format(id: String, file: String, ext: String, acodec: String, abr: Int) = buildJsonObject {
        put("format_id", id)
        put("url", url(file))
        put("ext", ext)
        put("vcodec", "none")
        put("acodec", acodec)
        put("protocol", "http")
        put("filesize", File(media, file).length())
        put("abr", abr)
    }

    private fun infoJson(dir: File, formats: List<JsonObject>, title: String, uploader: String, artist: String? = null): File {
        val info = buildJsonObject {
            put("id", "dQw4w9WgXcQ")
            put("title", title)
            put("uploader", uploader)
            put("channel", uploader)
            artist?.let { put("artist", it) }
            put("album", "Teszt Album")
            put("duration", 3)
            put("live_status", "not_live")
            put("upload_date", "20240101")
            put("webpage_url", "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
            put("extractor", "youtube")
            put("extractor_key", "Youtube")
            put("_type", "video")
            put("formats", buildJsonArray { formats.forEach { add(it) } })
            put("thumbnails", buildJsonArray {
                add(buildJsonObject { put("id", "0"); put("url", url("thumb.webp")); put("preference", 0) })
            })
        }
        return File(dir, "info.json").apply { writeText(info.toString()) }
    }

    private class Run(val exitCode: Int, val stdout: List<String>, val stderr: String) {
        val events get() = stdout.mapNotNull { EngineOutput.parse(it) }
        val output get() = File(events.filterIsInstance<EngineEvent.Output>().single().path)
        val meta get() = events.filterIsInstance<EngineEvent.Meta>().single()
    }

    private fun run(
        formats: List<JsonObject> = listOf(format("251", "audio_opus.webm", "webm", "opus", 160), format("140", "audio_aac.m4a", "m4a", "mp4a.40.2", 128)),
        title: String = "Árvíztűrő Teszt Dal (Official Video)",
        uploader: String = "Teszt Előadó - Topic",
        artist: String? = null,
        strategy: Strategy = Strategy.DEFAULT,
        extra: List<String> = emptyList(),
    ): Run {
        val work = Files.createTempDirectory(root.toPath(), "work").toFile()
        val archive = File(work, "archive.txt")
        val args = YtDlpArgs.build(EngineRequest(Target.Video("dQw4w9WgXcQ"), work.absolutePath, archive.absolutePath), strategy)
        // Same command line, but the "download" comes from our info JSON instead of YouTube.
        val offline = args.dropLast(2) + extra + listOf("--load-info-json", infoJson(work, formats, title, uploader, artist).absolutePath)
        val process = ProcessBuilder(listOf(ytDlp) + offline).start()
        val stderr = StringBuilder()
        val errThread = Thread { stderr.append(process.errorStream.bufferedReader().readText()) }.apply { start() }
        val stdout = process.inputStream.bufferedReader().readLines()
        check(process.waitFor(180, TimeUnit.SECONDS)) { "yt-dlp hung" }
        errThread.join()
        return Run(process.exitValue(), stdout, stderr.toString())
    }

    private fun probe(file: File): JsonObject {
        val process = ProcessBuilder("ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", file.absolutePath).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor(60, TimeUnit.SECONDS)
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun tags(info: JsonObject): Map<String, String> {
        val all = LinkedHashMap<String, String>()
        (info["format"]?.jsonObject?.get("tags") as? JsonObject)?.forEach { (k, v) -> all[k.lowercase()] = v.jsonPrimitive.content }
        info["streams"]!!.jsonArray.forEach { s -> (s.jsonObject["tags"] as? JsonObject)?.forEach { (k, v) -> all[k.lowercase()] = v.jsonPrimitive.content } }
        return all
    }

    @Test
    fun `opus stays opus, tagged, with a square cover`() {
        assumeTrue("engine tests are opt-in", enabled)
        val result = run()
        assertEquals("stderr: ${result.stderr}\nstdout: ${result.stdout}", 0, result.exitCode)

        assertEquals("Árvíztűrő Teszt Dal", result.meta.title)
        assertEquals("Teszt Előadó", result.meta.artist)
        assertEquals("Teszt Album", result.meta.album)
        assertTrue(result.events.any { it is EngineEvent.Plan && it.formatId == "251" })
        assertTrue(result.events.any { it is EngineEvent.Processing })

        val tracker = ProgressTracker()
        result.events.forEach(tracker::onEvent)
        assertEquals(1f, tracker.fraction!!, 0.001f)

        val output = result.output
        assertTrue(output.isFile)
        assertEquals("opus", output.extension)
        val info = probe(output)
        val streams = info["streams"]!!.jsonArray.map { it.jsonObject }
        assertTrue(streams.any { it["codec_name"]!!.jsonPrimitive.content == "opus" })
        val cover = streams.firstOrNull { it["codec_type"]!!.jsonPrimitive.content == "video" }
        assertNotNull("cover art is embedded", cover)
        assertEquals("cover is square", cover!!["width"]!!.jsonPrimitive.int, cover["height"]!!.jsonPrimitive.int)
        val tags = tags(info)
        assertEquals(tags.toString(), "Árvíztűrő Teszt Dal", tags["title"])
        assertEquals(tags.toString(), "Teszt Előadó", tags["artist"])
        assertEquals(tags.toString(), "Teszt Album", tags["album"])
        assertEquals(tags.toString(), "2024", tags["date"])
    }

    @Test
    fun `a real artist field wins over the channel name`() {
        assumeTrue("engine tests are opt-in", enabled)
        val result = run(title = "Rakpart", uploader = "WellHello Official", artist = "Wellhello")
        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("Wellhello", result.meta.artist)
        assertEquals("Wellhello", tags(probe(result.output))["artist"])
    }

    @Test
    fun `without opus the aac stream is kept as m4a`() {
        assumeTrue("engine tests are opt-in", enabled)
        val result = run(formats = listOf(format("140", "audio_aac.m4a", "m4a", "mp4a.40.2", 128)))
        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("m4a", result.output.extension)
        val info = probe(result.output)
        assertTrue(info["streams"]!!.jsonArray.any { it.jsonObject["codec_name"]!!.jsonPrimitive.content == "aac" })
        assertEquals("Árvíztűrő Teszt Dal", tags(info)["title"])
    }

    @Test
    fun `an unusual opus format id is still taken as opus`() {
        assumeTrue("engine tests are opt-in", enabled)
        val result = run(formats = listOf(format("999", "audio_opus.webm", "webm", "opus", 128), format("140", "audio_aac.m4a", "m4a", "mp4a.40.2", 128)))
        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("m4a", result.output.extension) // 140 is listed before the generic opus rule on purpose
    }

    @Test
    fun `simple processing still produces a playable file`() {
        assumeTrue("engine tests are opt-in", enabled)
        val result = run(strategy = Strategy(simpleProcessing = true))
        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals("opus", result.output.extension)
    }

    @Test
    fun `fallback strategies are accepted by yt-dlp`() {
        assumeTrue("engine tests are opt-in", enabled)
        val strategy = Strategy(playerClients = Strategy.OTHER_CLIENTS, forceIpv4 = true, anyFormat = true)
        val result = run(strategy = strategy)
        assertEquals(result.stderr, 0, result.exitCode)
    }

    @Test
    fun `a song in the archive is skipped and reported`() {
        assumeTrue("engine tests are opt-in", enabled)
        val work = Files.createTempDirectory(root.toPath(), "archived").toFile()
        val archive = File(work, "archive.txt").apply { writeText("youtube dQw4w9WgXcQ\n") }
        val info = infoJson(work, listOf(format("251", "audio_opus.webm", "webm", "opus", 160)), "T", "U")
        val args = YtDlpArgs.build(EngineRequest(Target.Video("dQw4w9WgXcQ"), work.absolutePath, archive.absolutePath))
            .dropLast(2) + listOf("--load-info-json", info.absolutePath)
        val process = ProcessBuilder(listOf(ytDlp) + args).redirectErrorStream(true).start()
        val lines = process.inputStream.bufferedReader().readLines()
        process.waitFor(120, TimeUnit.SECONDS)
        val events = lines.mapNotNull { EngineOutput.parse(it) }
        assertTrue(lines.toString(), events.any { it is EngineEvent.AlreadyHave })
        assertTrue(events.none { it is EngineEvent.Output })
    }

    @Test
    fun `a playlist walks every song, skips what we have, and reports the total`() {
        assumeTrue("engine tests are opt-in", enabled)
        val work = Files.createTempDirectory(root.toPath(), "playlist").toFile()
        val ids = listOf("aaaaaaaaaa1", "bbbbbbbbbb2", "ccccccccccc")
        val archive = File(work, "archive.txt").apply { writeText("youtube ${ids[1]}\n") }
        val entries = buildJsonArray {
            ids.forEachIndexed { index, id ->
                add(
                    buildJsonObject {
                        put("id", id)
                        put("title", "Dal ${index + 1}")
                        put("uploader", "Lista Előadó")
                        put("duration", 3)
                        put("live_status", "not_live")
                        put("webpage_url", "https://www.youtube.com/watch?v=$id")
                        put("extractor", "youtube")
                        put("extractor_key", "Youtube")
                        put("_type", "video")
                        put("formats", buildJsonArray { add(format("251", "audio_opus.webm", "webm", "opus", 160)) })
                        put("thumbnails", buildJsonArray { add(buildJsonObject { put("id", "0"); put("url", url("thumb.webp")); put("preference", 0) }) })
                    },
                )
            }
        }
        val playlist = buildJsonObject {
            put("_type", "playlist")
            put("id", "PLteszt")
            put("title", "Teszt lista")
            put("extractor", "youtube:tab")
            put("extractor_key", "YoutubeTab")
            put("webpage_url", "https://www.youtube.com/playlist?list=PLteszt")
            put("entries", entries)
        }
        val info = File(work, "playlist.json").apply { writeText(playlist.toString()) }
        // --no-clean-infojson keeps the "entries" of the loaded playlist (a test-only concern).
        val args = YtDlpArgs.build(EngineRequest(Target.Playlist("PLteszt"), work.absolutePath, archive.absolutePath))
            .dropLast(2) + listOf("--no-clean-infojson", "--load-info-json", info.absolutePath)
        val process = ProcessBuilder(listOf(ytDlp) + args).redirectErrorStream(true).start()
        val lines = process.inputStream.bufferedReader().readLines()
        process.waitFor(180, TimeUnit.SECONDS)
        assertEquals(lines.joinToString("\n"), 0, process.exitValue())
        val events = lines.mapNotNull { EngineOutput.parse(it) }

        val metas = events.filterIsInstance<EngineEvent.Meta>()
        assertEquals(lines.joinToString("\n"), listOf(ids[0], ids[2]), metas.map { it.id })
        assertEquals(listOf(1, 3), metas.map { it.playlistIndex })
        assertEquals(listOf(3, 3), metas.map { it.playlistCount })
        assertEquals("Lista Előadó", metas[0].artist)
        assertEquals(listOf(ids[1]), events.filterIsInstance<EngineEvent.AlreadyHave>().map { it.id })
        assertEquals(listOf(ids[0], ids[2]), events.filterIsInstance<EngineEvent.Output>().map { it.id })
        assertTrue(events.filterIsInstance<EngineEvent.Output>().all { File(it.path).isFile && it.path.endsWith(".opus") })
        assertEquals(3, (events.last { it is EngineEvent.PlaylistDone } as EngineEvent.PlaylistDone).count)

        val tracker = ProgressTracker()
        val seen = mutableListOf<Float>()
        events.forEach { event -> tracker.onEvent(event)?.let(seen::add) }
        assertEquals("the bar never goes back", seen, seen.sorted())
        assertTrue(seen.toString(), seen.first() < 0.5f)
    }

    @Test
    fun `classifier understands a real engine error`() {
        assumeTrue("engine tests are opt-in", enabled)
        val result = run(formats = listOf(format("251", "missing.webm", "webm", "opus", 160)), extra = listOf("--retries", "0"))
        assertTrue(result.stderr, result.exitCode != 0)
        assertEquals(result.stderr, FailureKind.FORBIDDEN, ErrorClassifier.classify(result.stderr).kind)
    }
}
