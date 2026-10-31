package io.github.itemujinwork.hanga.core

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
 *   gradle -p core test -Dhanga.engineTests=true -Dhanga.ytdlp=/path/to/yt-dlp
 */
class EngineIntegrationTest {
    companion object {
        private lateinit var root: File
        private lateinit var media: File
        private var server: HttpServer? = null
        private var port = 0

        private val enabled get() = System.getProperty("hanga.engineTests") == "true"
        private val ytDlp get() = System.getProperty("hanga.ytdlp") ?: "yt-dlp"

        @BeforeClass
        @JvmStatic
        fun setUp() {
            if (!enabled) return
            root = Files.createTempDirectory("hanga-engine").toFile()
            media = File(root, "media").apply { mkdirs() }
            ffmpeg("-f", "lavfi", "-i", "testsrc=size=1280x720:rate=25", "-t", "3", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-an", "video_h264_720.mp4")
            ffmpeg("-f", "lavfi", "-i", "testsrc=size=1920x1080:rate=25", "-t", "3", "-c:v", "libvpx-vp9", "-deadline", "realtime", "-cpu-used", "8", "-an", "video_vp9_1080.webm")
            ffmpeg("-f", "lavfi", "-i", "sine=frequency=440:duration=3", "-c:a", "aac", "-b:a", "128k", "audio.m4a")
            ffmpeg("-f", "lavfi", "-i", "sine=frequency=660:duration=3", "-c:a", "libopus", "-b:a", "96k", "audio.webm")
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

    private fun infoJson(dir: File): File {
        fun format(id: String, file: String, ext: String, vcodec: String, acodec: String, width: Int? = null, height: Int? = null, abr: Int? = null) =
            buildJsonObject {
                put("format_id", id)
                put("url", url(file))
                put("ext", ext)
                put("vcodec", vcodec)
                put("acodec", acodec)
                put("protocol", "http")
                put("filesize", File(media, file).length())
                width?.let { put("width", it) }
                height?.let { put("height", it) }
                abr?.let { put("abr", it) }
            }
        val info = buildJsonObject {
            put("id", "dQw4w9WgXcQ")
            put("title", "Árvíztűrő Teszt Dal (Official Video)")
            put("uploader", "Teszt Előadó - Topic")
            put("channel", "Teszt Előadó")
            put("duration", 3)
            put("live_status", "not_live")
            put("upload_date", "20240101")
            put("webpage_url", "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
            put("extractor", "youtube")
            put("extractor_key", "Youtube")
            put("_type", "video")
            put("formats", buildJsonArray {
                add(format("136", "video_h264_720.mp4", "mp4", "avc1.4d401f", "none", 1280, 720))
                add(format("248", "video_vp9_1080.webm", "webm", "vp9", "none", 1920, 1080))
                add(format("140", "audio.m4a", "m4a", "none", "mp4a.40.2", abr = 128))
                add(format("251", "audio.webm", "webm", "none", "opus", abr = 96))
            })
            put("thumbnails", buildJsonArray {
                add(buildJsonObject { put("id", "0"); put("url", url("thumb.webp")); put("preference", 0) })
            })
        }
        return File(dir, "info.json").apply { writeText(info.toString()) }
    }

    private class Run(val exitCode: Int, val stdout: List<String>, val stderr: String)

    private fun run(kind: MediaKind, strategy: Strategy = Strategy.DEFAULT): Pair<File, Run> {
        val work = Files.createTempDirectory(root.toPath(), kind.name.lowercase()).toFile()
        val args = YtDlpArgs.build(EngineRequest(VideoLink("dQw4w9WgXcQ"), kind, work.absolutePath), strategy)
        // Same command line, but the "download" comes from our info JSON instead of YouTube.
        val offline = args.dropLast(2) + listOf("--load-info-json", infoJson(work).absolutePath)
        val process = ProcessBuilder(listOf(ytDlp) + offline).start()
        val stderr = StringBuilder()
        val errThread = Thread { stderr.append(process.errorStream.bufferedReader().readText()) }.apply { start() }
        val stdout = process.inputStream.bufferedReader().readLines()
        check(process.waitFor(180, TimeUnit.SECONDS)) { "yt-dlp hung" }
        errThread.join()
        return work to Run(process.exitValue(), stdout, stderr.toString())
    }

    private fun probe(file: File): JsonObject {
        val process = ProcessBuilder("ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", file.absolutePath).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor(60, TimeUnit.SECONDS)
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun events(run: Run) = run.stdout.mapNotNull { EngineOutput.parse(it) }

    @Test
    fun `music becomes a tagged mp3 with a square cover`() {
        assumeTrue("engine tests are opt-in", enabled)
        val (_, result) = run(MediaKind.AUDIO)
        assertEquals("stderr: ${result.stderr}\nstdout: ${result.stdout}", 0, result.exitCode)

        val events = events(result)
        val meta = events.filterIsInstance<EngineEvent.Meta>().single()
        assertEquals("Árvíztűrő Teszt Dal (Official Video)", meta.title)
        assertEquals("Teszt Előadó", meta.author)
        assertTrue(events.any { it is EngineEvent.Plan })
        assertTrue(events.any { it == EngineEvent.Processing })

        val tracker = ProgressTracker()
        events.forEach { if (it is EngineEvent.Plan) tracker.onPlan(it) else if (it is EngineEvent.Progress) tracker.onProgress(it) }
        assertEquals(1f, tracker.fraction!!, 0.001f)

        val output = File(events.filterIsInstance<EngineEvent.Output>().single().path)
        assertTrue(output.isFile)
        assertEquals("mp3", output.extension)
        val info = probe(output)
        val streams = info["streams"]!!.jsonArray.map { it.jsonObject }
        assertTrue(streams.any { it["codec_name"]!!.jsonPrimitive.content == "mp3" })
        val cover = streams.firstOrNull { it["codec_type"]!!.jsonPrimitive.content == "video" }
        assertNotNull("cover art is embedded", cover)
        assertEquals("cover is square", cover!!["width"]!!.jsonPrimitive.int, cover["height"]!!.jsonPrimitive.int)
        val tags = info["format"]!!.jsonObject["tags"]!!.jsonObject
        assertEquals("Árvíztűrő Teszt Dal (Official Video)", tags["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun `video prefers h264 plus aac and merges into mp4`() {
        assumeTrue("engine tests are opt-in", enabled)
        val (_, result) = run(MediaKind.VIDEO)
        assertEquals("stderr: ${result.stderr}\nstdout: ${result.stdout}", 0, result.exitCode)
        val events = events(result)
        val plan = events.filterIsInstance<EngineEvent.Plan>().single()
        assertEquals(listOf("136", "140"), plan.formatIds)

        val tracker = ProgressTracker()
        val seen = mutableListOf<Float>()
        events.forEach { event ->
            if (event is EngineEvent.Plan) tracker.onPlan(event)
            if (event is EngineEvent.Progress) tracker.onProgress(event)?.let(seen::add)
        }
        assertEquals("the bar never goes back", seen, seen.sorted())
        assertEquals(1f, seen.last(), 0.001f)

        val output = File(events.filterIsInstance<EngineEvent.Output>().single().path)
        assertEquals("mp4", output.extension)
        val codecs = (probe(output)["streams"] as JsonArray).map { it.jsonObject["codec_name"]!!.jsonPrimitive.content }
        assertTrue(codecs.toString(), "h264" in codecs && "aac" in codecs)
    }

    @Test
    fun `simple processing still produces a playable mp3`() {
        assumeTrue("engine tests are opt-in", enabled)
        val (_, result) = run(MediaKind.AUDIO, Strategy(simpleProcessing = true))
        assertEquals(result.stderr, 0, result.exitCode)
        val output = File(events(result).filterIsInstance<EngineEvent.Output>().single().path)
        assertEquals("mp3", output.extension)
    }

    @Test
    fun `fallback strategies are accepted by yt-dlp`() {
        assumeTrue("engine tests are opt-in", enabled)
        val strategy = Strategy(playerClients = Strategy.OTHER_CLIENTS, forceIpv4 = true, anyFormat = true)
        val (_, result) = run(MediaKind.VIDEO, strategy)
        assertEquals(result.stderr, 0, result.exitCode)
    }

    @Test
    fun `classifier understands a real engine error`() {
        assumeTrue("engine tests are opt-in", enabled)
        // Point the stream at a missing file: yt-dlp reports an HTTP error.
        val work = Files.createTempDirectory(root.toPath(), "broken").toFile()
        val info = infoJson(work).let { Json.parseToJsonElement(it.readText()).jsonObject }
        val broken = JsonObject(info + ("formats" to buildJsonArray {
            add(buildJsonObject {
                put("format_id", "251"); put("url", url("missing.webm")); put("ext", "webm")
                put("vcodec", "none"); put("acodec", "opus"); put("protocol", "http")
            })
        }))
        val file = File(work, "broken.json").apply { writeText(broken.toString()) }
        val args = YtDlpArgs.build(EngineRequest(VideoLink("dQw4w9WgXcQ"), MediaKind.AUDIO, work.absolutePath))
            .dropLast(2) + listOf("--retries", "0", "--load-info-json", file.absolutePath)
        val process = ProcessBuilder(listOf(ytDlp) + args).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor(120, TimeUnit.SECONDS)
        assertTrue(text, process.exitValue() != 0)
        assertEquals(text, FailureKind.FORBIDDEN, ErrorClassifier.classify(text).kind)
    }
}
