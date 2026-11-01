package io.github.itemujinwork.dalma.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.itemujinwork.dalma.data.MusicFolder
import io.github.itemujinwork.dalma.data.Song
import io.github.itemujinwork.dalma.play.PlayerRoute
import io.github.itemujinwork.dalma.ui.theme.DalmaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What opens: the empty sky with Dalma when the folder is empty, the player when it is not.
 * The folder is read on start and on every return, since files come and go behind Dalma's back.
 */
@Composable
fun DalmaRoot() {
    val context = LocalContext.current
    val folder = remember { MusicFolder(context) }
    val scope = rememberCoroutineScope()
    var songs by remember { mutableStateOf<List<Song>?>(null) }
    val reread: () -> Unit = { scope.launch { songs = withContext(Dispatchers.IO) { folder.read() } } }
    LifecycleResumeEffect(Unit) {
        val reading = scope.launch { songs = withContext(Dispatchers.IO) { folder.read() } }
        onPauseOrDispose { reading.cancel() }
    }
    when (val list = songs) {
        null -> Box(Modifier.fillMaxSize().background(DalmaColors.Night))
        else -> if (list.isEmpty()) HomeScreen() else PlayerRoute(list, onFolderChanged = reread)
    }
}
