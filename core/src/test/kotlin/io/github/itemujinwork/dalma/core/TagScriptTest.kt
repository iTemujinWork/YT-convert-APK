package io.github.itemujinwork.dalma.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The tag script on a Python that has mutagen (-Ddalma.python=...), on files ffmpeg makes, read
 * back with ffprobe: what another player sees. Skipped where either tool is missing.
 */
class TagScriptTest {
    private val python: String? = System.getProperty("dalma.python")?.takeIf { it.isNotBlank() }
    private val ffmpeg: String? = listOf("ffmpeg", "/opt/homebrew/bin/ffmpeg", "/usr/local/bin/ffmpeg").firstOrNull { works(it, "-version") }
    private val ffprobe: String? = listOf("ffprobe", "/opt/homebrew/bin/ffprobe", "/usr/local/bin/ffprobe").firstOrNull { works(it, "-version") }
    private lateinit var dir: File

    @Before
    fun gate() {
        assumeTrue("needs -Ddalma.python (with mutagen), ffmpeg and ffprobe", python != null && ffmpeg != null && ffprobe != null && works(python!!, "-c", "import mutagen"))
        dir = Files.createTempDirectory("dalma-tags").toFile()
    }

    @Test
    fun `an opus gets its title, artist and a square cover, as vorbis comments`() {
        val song = sine("dal.opus", "libopus")
        write(song, "Hajnali vonat", "Kóbor Lámpás", cover())
        val info = probe(song)
        val tags = tags(info)
        assertEquals("Hajnali vonat", tags["title"])
        assertEquals("Kóbor Lámpás", tags["artist"])
        val picture = info["streams"]!!.jsonArray.map { it.jsonObject }.firstOrNull { it["codec_type"]!!.jsonPrimitive.content == "video" }
        assertNotNull("the cover is in the file", picture)
        assertEquals(64, picture!!["width"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `an aac gets its itunes tags`() {
        val song = sine("dal.m4a", "aac")
        write(song, "Negyedik emelet", "Ibolya", cover())
        val info = probe(song)
        assertEquals("Negyedik emelet", tags(info)["title"])
        assertEquals("Ibolya", tags(info)["artist"])
        assertNotNull("the cover is in the file", info["streams"]!!.jsonArray.map { it.jsonObject }.firstOrNull { it["codec_type"]!!.jsonPrimitive.content == "video" })
    }

    @Test
    fun `a blank artist takes the tag away and no cover leaves the picture alone`() {
        val song = sine("dal.opus", "libopus")
        write(song, "Első", "Valaki", cover())
        write(song, "Második", "", null)
        val info = probe(song)
        assertEquals("Második", tags(info)["title"])
        assertNull(tags(info)["artist"])
        assertNotNull("the cover stayed", info["streams"]!!.jsonArray.map { it.jsonObject }.firstOrNull { it["codec_type"]!!.jsonPrimitive.content == "video" })
    }

    private fun write(song: File, title: String, artist: String, cover: File?) {
        val process = ProcessBuilder(listOf(python!!, "-c", TagScript.SOURCE) + TagScript.arguments(song.absolutePath, title, artist, cover?.absolutePath)).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
    }

    /** A second of a sine, the way ffmpeg writes it. */
    private fun sine(name: String, codec: String): File {
        val file = File(dir, name)
        run(ffmpeg!!, "-y", "-v", "error", "-f", "lavfi", "-i", "sine=frequency=440:duration=1", "-c:a", codec, file.absolutePath)
        return file
    }

    private fun cover(): File {
        val file = File(dir, "borito.jpg")
        run(ffmpeg!!, "-y", "-v", "error", "-f", "lavfi", "-i", "color=c=red:s=64x64", "-frames:v", "1", file.absolutePath)
        return file
    }

    private fun probe(file: File): JsonObject {
        val process = ProcessBuilder(ffprobe!!, "-v", "error", "-show_streams", "-show_format", "-of", "json", file.absolutePath).start()
        val text = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor())
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun tags(info: JsonObject): Map<String, String> {
        val all = HashMap<String, String>()
        info["streams"]!!.jsonArray.forEach { s -> (s.jsonObject["tags"] as? JsonObject)?.forEach { (k, v) -> all[k.lowercase()] = v.jsonPrimitive.content } }
        (info["format"]?.jsonObject?.get("tags") as? JsonObject)?.forEach { (k, v) -> all[k.lowercase()] = v.jsonPrimitive.content }
        return all
    }

    private fun run(vararg command: String) {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
    }

    private fun works(vararg command: String): Boolean = try {
        ProcessBuilder(*command).redirectErrorStream(true).start().let { it.inputStream.readBytes(); it.waitFor() == 0 }
    } catch (_: Exception) {
        false
    }
}
