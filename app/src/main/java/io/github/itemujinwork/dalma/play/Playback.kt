package io.github.itemujinwork.dalma.play

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.itemujinwork.dalma.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** What a screen needs to know: which song, whether it plays, and how long it is. */
data class PlaybackState(
    val songId: Long? = null,
    val playing: Boolean = false,
    val durationMillis: Long = 0L,
)

/**
 * The player as the screens see it: one controller talking to [PlaybackService]. Every call
 * belongs on the main thread, like the controller itself.
 */
class Playback private constructor(private val controller: MediaController) {
    private val mutableState = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = mutableState

    init {
        controller.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = refresh()
        })
        refresh()
    }

    private fun refresh() {
        mutableState.value = PlaybackState(
            songId = controller.currentMediaItem?.mediaId?.toLongOrNull(),
            playing = controller.isPlaying,
            durationMillis = controller.duration.takeIf { it > 0 } ?: 0L,
        )
    }

    /** Plays [songs] from [index]; the whole list becomes the queue, in this order. */
    fun play(songs: List<Song>, index: Int) {
        if (songs.isEmpty()) return
        controller.setMediaItems(songs.map(::item), index.coerceIn(0, songs.size - 1), 0L)
        controller.prepare()
        controller.play()
    }

    fun playPause() {
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun next() = controller.seekToNextMediaItem()

    fun previous() = controller.seekToPreviousMediaItem()

    fun seekTo(positionMillis: Long) = controller.seekTo(positionMillis.coerceAtLeast(0L))

    /** Where the current song is, right now; polled by the screen while the line is visible. */
    val positionMillis: Long get() = controller.currentPosition.coerceAtLeast(0L)

    fun release() = controller.release()

    companion object {
        /** Connects to the service (starting it when needed); release the result when the screen goes. */
        suspend fun connect(context: Context): Playback {
            val app = context.applicationContext
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            val future = MediaController.Builder(app, token).buildAsync()
            val controller = suspendCancellableCoroutine { continuation ->
                future.addListener(
                    { runCatching { future.get() }.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) }) },
                    ContextCompat.getMainExecutor(app),
                )
                continuation.invokeOnCancellation { MediaController.releaseFuture(future) }
            }
            return Playback(controller)
        }

        /** The file as a queue entry; the player reads the rest (cover, length) from the file. */
        fun item(song: Song): MediaItem = MediaItem.Builder()
            .setUri(song.uri)
            .setMediaId(song.id.toString())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).build())
            .build()
    }
}
