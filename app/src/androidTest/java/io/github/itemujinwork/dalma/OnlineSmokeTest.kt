package io.github.itemujinwork.dalma

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.itemujinwork.dalma.engine.Engine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Talks to the real YouTube with the packaged engine and QuickJS. Run separately: a CI
 * runner's address may be refused by YouTube, which says nothing about the app.
 */
@RunWith(AndroidJUnit4::class)
class OnlineSmokeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun readsATitleAndAnAudioFormatFromYouTube() = runBlocking {
        val engine = Engine(context)
        engine.prepare()
        val lines = mutableListOf<String>()
        val result = engine.run(
            listOf(
                "--ignore-config", "--no-playlist", "--simulate", "--no-warnings",
                "-f", "251/140/ba",
                "--print", "%(title)s|%(format_id)s|%(acodec)s",
                "--", "https://www.youtube.com/watch?v=jNQXAC9IVRw",
            ),
        ) { lines += it }
        assertEquals("stdout: $lines\nstderr: ${result.stderr}", 0, result.exitCode)
        val line = lines.last()
        assertTrue(line, line.contains("zoo", ignoreCase = true))
        assertTrue(line, line.contains("|251|") || line.contains("|140|"))
    }
}
