package io.github.itemujinwork.dalma.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.core.net.toUri
import io.github.itemujinwork.dalma.R
import io.github.itemujinwork.dalma.core.IdleMotion
import io.github.itemujinwork.dalma.core.StartTimeline
import io.github.itemujinwork.dalma.ui.Sky
import io.github.itemujinwork.dalma.ui.mascot.Mascot
import io.github.itemujinwork.dalma.ui.mascot.rememberMascotState
import io.github.itemujinwork.dalma.ui.rememberSkyState
import io.github.itemujinwork.dalma.ui.theme.DalmaColors
import io.github.itemujinwork.dalma.update.AppUpdateCheck
import kotlinx.coroutines.delay
import java.time.LocalTime

/** The brief's evening, 20:00–06:00: Dalma wakes from the wall instead of winking, and listens with closed eyes. It follows the clock, not dark mode. */
fun isLateEvening(now: LocalTime = LocalTime.now()): Boolean = now.hour >= 20 || now.hour < 6

@Composable
fun HomeScreen(sleepy: Boolean = remember { isLateEvening() }, checkForUpdates: Boolean = true) {
    val context = LocalContext.current
    val mascot = rememberMascotState(arriving = true)
    val sky = rememberSkyState()
    val haptic = LocalHapticFeedback.current
    var showAbout by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<AppUpdateCheck.Available?>(null) }

    LaunchedEffect(checkForUpdates) { if (checkForUpdates) update = AppUpdateCheck.check(context) }
    // The opening, to the sheet's clock: she appears (0–600), greets (800–2000) — by day with the
    // wink, at night by straightening up from the wall — and with no music that is all. Late at
    // night, with nothing to play, she leans back on the wall and sleeps.
    LaunchedEffect(sleepy) {
        if (sleepy) mascot.sleepAtWallStill() else mascot.setIdle(IdleMotion.BREATHE)
        mascot.arrive()
        if (!mascot.reducedMotion) {
            delay((StartTimeline.greet.startMillis - StartTimeline.appear.endMillis).toLong())
            if (sleepy) mascot.wakeUp() else mascot.greet()
        }
        if (sleepy) {
            if (!mascot.reducedMotion) delay(StartTimeline.air.durationMillis.toLong())
            mascot.sleepAtWall() // and from here only alszik-fal's slow sinking moves her
        } else {
            mascot.live()
        }
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
                .requiredSize(StartTimeline.BIG_DP.dp),
            contentDescription = stringResource(R.string.cd_dalma),
        )
    }

    if (showAbout) AboutCard(update = update, onDismiss = { showAbout = false })
}

internal fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
