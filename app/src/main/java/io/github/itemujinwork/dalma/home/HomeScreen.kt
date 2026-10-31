package io.github.itemujinwork.dalma.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.itemujinwork.dalma.R
import io.github.itemujinwork.dalma.ui.Sky
import io.github.itemujinwork.dalma.ui.mascot.Mascot
import io.github.itemujinwork.dalma.ui.mascot.rememberMascotState
import io.github.itemujinwork.dalma.ui.rememberSkyState
import io.github.itemujinwork.dalma.ui.theme.DalmaColors
import io.github.itemujinwork.dalma.update.AppUpdateCheck
import kotlinx.coroutines.delay
import java.time.LocalTime

/** Late in the evening Dalma is sleepy; this follows the clock, not dark mode. */
fun isLateEvening(now: LocalTime = LocalTime.now()): Boolean = now.hour >= 22 || now.hour < 6

@Composable
fun HomeScreen(sleepy: Boolean = remember { isLateEvening() }, checkForUpdates: Boolean = true) {
    val context = LocalContext.current
    val mascot = rememberMascotState()
    val sky = rememberSkyState()
    val haptic = LocalHapticFeedback.current
    var showAbout by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<AppUpdateCheck.Available?>(null) }

    LaunchedEffect(checkForUpdates) { if (checkForUpdates) update = AppUpdateCheck.check(context) }
    LaunchedEffect(sleepy) {
        mascot.arrive()
        if (sleepy) {
            mascot.sleepy()
        } else {
            delay(120)
            mascot.greet()
        }
        mascot.live(sleepy)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DalmaColors.Night)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAbout = true
                })
            },
    ) {
        Sky(Modifier.fillMaxSize(), state = sky, ambientShootingStars = true)
        update?.let { available ->
            Text(
                text = stringResource(R.string.about_update),
                style = MaterialTheme.typography.bodyMedium,
                color = DalmaColors.Mint,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 20.dp)
                    .clickable { openUrl(context, available.url) }
                    .padding(8.dp),
            )
        }
        Mascot(
            state = mascot,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.86f)
                .aspectRatio(1f),
            contentDescription = stringResource(R.string.cd_dalma),
        )
    }

    if (showAbout) AboutCard(update = update, onDismiss = { showAbout = false })
}

internal fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
