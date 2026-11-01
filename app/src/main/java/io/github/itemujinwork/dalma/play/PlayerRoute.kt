package io.github.itemujinwork.dalma.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.itemujinwork.dalma.core.SongPalette
import io.github.itemujinwork.dalma.data.MusicFolder
import io.github.itemujinwork.dalma.data.Song
import io.github.itemujinwork.dalma.home.AboutCard
import io.github.itemujinwork.dalma.ui.mascot.rememberMascotState
import io.github.itemujinwork.dalma.ui.rememberSkyState
import io.github.itemujinwork.dalma.update.AppUpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalTime

/** The player's evening, 20:00–06:00: less colour in the sky (Dalma's night mood comes in step 9). */
fun isPlayerEvening(now: LocalTime = LocalTime.now()): Boolean = now.hour >= 20 || now.hour < 6

/** The cover and the colours of one song, loaded together so they arrive together. */
private class Art(val songId: Long, val cover: ImageBitmap?, val palette: SongPalette)

/**
 * The player screen tied to the folder and the service. Before anything plays, the newest
 * song is shown and a tap starts it; from then on the screen follows what plays.
 */
@Composable
fun PlayerRoute(songs: List<Song>) {
    val context = LocalContext.current
    val folder = remember { MusicFolder(context) }
    val playback by produceState<Playback?>(initialValue = null) { value = Playback.connect(context) }
    DisposableEffect(playback) { onDispose { playback?.release() } }
    val state = playback?.state?.collectAsStateWithLifecycle()?.value ?: PlaybackState()

    var shownIndex by rememberSaveable { mutableIntStateOf(0) }
    val queued = state.songId != null && songs.any { it.id == state.songId }
    val song = if (queued) songs.first { it.id == state.songId } else songs[shownIndex.coerceIn(0, songs.lastIndex)]

    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playback, queued, state.playing, state.songId) {
        position = if (queued) playback?.positionMillis ?: 0L else 0L
        while (state.playing) {
            delay(250)
            position = playback?.positionMillis ?: 0L
        }
    }

    // The previous song's cover and colours stay until the new ones are read, so the colours cross-fade.
    var art by remember { mutableStateOf<Art?>(null) }
    LaunchedEffect(song.id) {
        art = withContext(Dispatchers.IO) {
            val bitmap = folder.cover(song, COVER_PX)
            Art(song.id, bitmap?.asImageBitmap(), SongColors.of(bitmap, song.artist))
        }
    }
    val shownArt = art?.takeIf { it.songId == song.id }

    val mascot = rememberMascotState()
    val sky = rememberSkyState()
    LaunchedEffect(Unit) {
        mascot.closeness.snapTo(1f)
        mascot.live(sleepy = false)
    }
    val evening = remember { isPlayerEvening() }
    val haptic = LocalHapticFeedback.current
    var showAbout by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<AppUpdateCheck.Available?>(null) }
    LaunchedEffect(Unit) { update = AppUpdateCheck.check(context) }

    PlayerScreen(
        song = song,
        cover = shownArt?.cover ?: art?.cover,
        palette = (shownArt ?: art)?.palette ?: SongPalette.fromArtist(song.artist),
        playing = state.playing,
        positionMillis = position,
        durationMillis = state.durationMillis.takeIf { queued && it > 0 } ?: song.durationMillis,
        mascot = mascot,
        sky = sky,
        evening = evening,
        onTap = {
            val player = playback ?: return@PlayerScreen
            if (queued) player.playPause() else player.play(songs, songs.indexOf(song))
        },
        onSwipe = { direction ->
            val player = playback
            if (queued && player != null) {
                if (direction < 0) player.next() else player.previous()
            } else {
                shownIndex = (songs.indexOf(song) - direction).coerceIn(0, songs.lastIndex)
            }
        },
        onSwipeUp = { /* the list, step 5 */ },
        onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) /* shuffle, step 6 */ },
        onSeek = { millis ->
            if (queued) {
                playback?.seekTo(millis)
                position = millis
            }
        },
        onMascotLongPress = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            showAbout = true
        },
    )
    if (showAbout) AboutCard(update = update, onDismiss = { showAbout = false })
}

/** Covers are drawn at 330 dp; this is plenty on a 3x screen. */
private const val COVER_PX = 990
