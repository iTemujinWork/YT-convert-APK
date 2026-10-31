package io.github.itemujinwork.dalma

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.itemujinwork.dalma.engine.Engine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** The first thing to prove on a phone: the packaged Python, yt-dlp and QuickJS actually run. */
@RunWith(AndroidJUnit4::class)
class EngineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun unpacksAndReportsItsVersion() = runBlocking {
        val engine = Engine(context)
        val steps = mutableListOf<Float>()
        engine.prepare { steps += it }
        assertTrue(engine.isUnpacked)
        assertTrue("progress reaches 1: $steps", steps.last() >= 1f)
        assertEquals("progress never goes back: $steps", steps, steps.sorted())

        val lines = mutableListOf<String>()
        val result = engine.run(listOf("--version")) { lines += it }
        assertEquals("stdout: $lines\nstderr: ${result.stderr}", 0, result.exitCode)
        val version = lines.last().trim()
        assertTrue(version, version.matches(Regex("""\d{4}\.\d{2}\.\d{2}(\.\d+)?""")))
        assertEquals(version, engine.version())
    }

    @Test
    fun quickJsRuns() {
        val qjs = File(context.applicationInfo.nativeLibraryDir, "libqjs.so")
        assertTrue(qjs.path, qjs.canExecute())
        val process = ProcessBuilder(qjs.path, "-e", "console.log(6*7)").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS))
        assertEquals(output, 0, process.exitValue())
        assertTrue(output, output.trim().endsWith("42"))
    }

    @Test
    fun cancellingKillsTheProcessTree() = runBlocking {
        val engine = Engine(context)
        engine.prepare()
        val job = launch {
            // A download that never ends: yt-dlp waits on a port nobody answers on.
            engine.run(listOf("--ignore-config", "--socket-timeout", "120", "--retries", "0", "--", "http://127.0.0.1:9/never")) {}
        }
        delay(2_500)
        job.cancel()
        job.join()
        delay(500)
        val python = File("/proc").listFiles().orEmpty().mapNotNull { it.name.toIntOrNull() }.count { pid ->
            runCatching { File("/proc/$pid/cmdline").readText() }.getOrDefault("").contains("libpython.so")
        }
        assertEquals("no python left running", 0, python)
    }
}
