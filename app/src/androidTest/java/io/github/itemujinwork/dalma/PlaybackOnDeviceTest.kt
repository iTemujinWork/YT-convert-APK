package io.github.itemujinwork.dalma

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.itemujinwork.dalma.data.Song
import io.github.itemujinwork.dalma.play.Playback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The real service on a real Android: a controller connects, a stream from the test assets
 * plays through the session, and pausing pauses. Runs on an emulator or a phone.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackOnDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun playsAndPausesThroughTheService() = runBlocking {
        val file = File(context.cacheDir, "play.webm")
        InstrumentationRegistry.getInstrumentation().context.assets.open("audio_opus.webm").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        val song = Song(id = 1, uri = Uri.fromFile(file), fileName = file.name, title = "Teszt", artist = null, durationMillis = 0, addedAtSeconds = 0)
        val playback = withContext(Dispatchers.Main) { Playback.connect(context) }
        try {
            withContext(Dispatchers.Main) { playback.play(listOf(song), 0) }
            assertNotNull("it plays", withTimeoutOrNull(10_000) { playback.state.first { it.playing } })
            withContext(Dispatchers.Main) { playback.playPause() }
            assertNotNull("it pauses", withTimeoutOrNull(5_000) { playback.state.first { !it.playing } })
        } finally {
            withContext(Dispatchers.Main) { playback.release() }
            file.delete()
        }
    }
}
