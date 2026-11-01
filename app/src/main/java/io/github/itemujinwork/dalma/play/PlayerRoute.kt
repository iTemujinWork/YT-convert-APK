package io.github.itemujinwork.dalma.play

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

/** The player's evening, 20:00–06:00: less colour in the sky (Dalma's night mood comes in step 9). */
fun isPlayerEvening(now: LocalTime = LocalTime.now()): Boolean = now.hour >= 20 || now.hour < 6

/** The cover and the colours of one song, loaded together so they arrive together. */
private class Art(val songId: Long, val cover: ImageBitmap?, val palette: SongPalette)

/**
 * The player screen tied to the folder and the service. Before anything plays, the newest
 * song is shown and a tap starts it; from then on the screen follows what plays. The list is
 * the same screen opened up: rows, a search pulled down, a row swiped away.
 */
@Composable
fun PlayerRoute(songs: List<Song>, onFolderChanged: () -> Unit) {
    val context = LocalContext.current
    val folder = remember { MusicFolder(context) }
    val scope = rememberCoroutineScope()
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

    // Small covers for the rows, read once each.
    val thumbs = remember { HashMap<Long, ImageBitmap?>() }
    val thumbnail: @Composable (Song) -> ImageBitmap? = { row ->
        produceState(thumbs[row.id], row.id) {
            if (!thumbs.containsKey(row.id)) {
                val bitmap = withContext(Dispatchers.IO) { folder.cover(row, THUMB_PX)?.asImageBitmap() }
                thumbs[row.id] = bitmap
                value = bitmap
            }
        }.value
    }

    // The list: open, searched, and a row on its way out.
    var listOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Song?>(null) }
    val rows = remember(songs, query) {
        val needle = query.trim()
        if (needle.isEmpty()) songs else songs.filter { it.title.contains(needle, ignoreCase = true) || it.artist?.contains(needle, ignoreCase = true) == true }
    }
    fun closeSearch() {
        searchOpen = false
        query = ""
    }
    BackHandler(enabled = listOpen) {
        when {
            deleting != null -> deleting = null
            searchOpen -> closeSearch()
            else -> listOpen = false
        }
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        deleting = null
        onFolderChanged()
    }

    val mascot = rememberMascotState()
    val sorryMascot = rememberMascotState()
    val sky = rememberSkyState()
    LaunchedEffect(Unit) {
        mascot.closeness.snapTo(1f)
        mascot.live(sleepy = false)
    }
    LaunchedEffect(deleting != null) {
        if (deleting != null) {
            sorryMascot.closeness.snapTo(1f)
            sorryMascot.sorry()
        }
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
        list = ListUi(open = listOpen, songs = rows, searchOpen = searchOpen, query = query, deleting = deleting),
        actions = ListActions(
            onClose = {
                closeSearch()
                listOpen = false
            },
            onPick = { picked -> playback?.play(songs, songs.indexOf(picked)) },
            onSearchOpen = { searchOpen = true },
            onQueryChange = { query = it },
            onDeleteRequest = { deleting = it },
            onDeleteConfirm = { doomed ->
                scope.launch {
                    when (val removal = withContext(Dispatchers.IO) { folder.remove(doomed) }) {
                        MusicFolder.Removal.Removed -> {
                            deleting = null
                            thumbs.remove(doomed.id)
                            onFolderChanged()
                        }
                        is MusicFolder.Removal.NeedsConsent -> consent.launch(IntentSenderRequest.Builder(removal.intentSender).build())
                        MusicFolder.Removal.Failed -> deleting = null
                    }
                }
            },
            onDeleteCancel = { deleting = null },
        ),
        thumbnail = thumbnail,
        sorryMascot = sorryMascot,
        currentId = state.songId.takeIf { queued },
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
        onSwipeUp = { listOpen = true },
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

/** Covers are drawn at 330 dp and 48 dp; these are plenty on a 3x screen. */
private const val COVER_PX = 990
private const val THUMB_PX = 144
