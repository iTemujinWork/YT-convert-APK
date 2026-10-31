package io.github.itemujinwork.dalma.share

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.itemujinwork.dalma.R
import io.github.itemujinwork.dalma.core.LinkCheck
import io.github.itemujinwork.dalma.core.Outcome
import io.github.itemujinwork.dalma.data.Prefs
import io.github.itemujinwork.dalma.ui.DragHandle
import io.github.itemujinwork.dalma.ui.LocalReducedMotion
import io.github.itemujinwork.dalma.ui.Sentence
import io.github.itemujinwork.dalma.ui.Sentences
import io.github.itemujinwork.dalma.ui.Sky
import io.github.itemujinwork.dalma.ui.SkyState
import io.github.itemujinwork.dalma.ui.mascot.Mascot
import io.github.itemujinwork.dalma.ui.mascot.MascotState
import io.github.itemujinwork.dalma.ui.mascot.rememberMascotState
import io.github.itemujinwork.dalma.ui.rememberSkyState
import io.github.itemujinwork.dalma.ui.theme.DalmaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** The move always plays out, about this long, so a fast download does not flash. */
private const val MIN_CHOREOGRAPHY_MILLIS = 2_000L
private const val DISMISS_AFTER_SUCCESS = 2_800L
private const val DISMISS_AFTER_FAILURE = 5_000L

/**
 * The small sheet over YouTube. No text, no button: Dalma comes closer, turns so her
 * hair clip shows, the clip fills with the download, she turns back and is happy, a quiet
 * vibration, one sentence, and the sheet slides away by itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(check: LinkCheck, onFinished: () -> Unit) {
    val context = LocalContext.current
    val reducedMotion = LocalReducedMotion.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val mascot = rememberMascotState()
    val sky = rememberSkyState()
    val haptic = LocalHapticFeedback.current
    var sentence by remember { mutableStateOf<String?>(null) }
    var glow by remember { mutableFloatStateOf(0f) }
    var progressDescription by remember { mutableStateOf<Int?>(null) }

    // Android 13+: the system asks once, with its own words, at the first share.
    var permissionSettled by remember { mutableStateOf(!shouldAskForNotifications(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionSettled = true }
    LaunchedEffect(Unit) {
        if (!permissionSettled) {
            Prefs(context).notificationAsked = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun dismissAfter(millis: Long) {
        scope.launch {
            delay(if (reducedMotion) millis.coerceAtMost(2_500L) else millis)
            runCatching { sheetState.hide() }
            onFinished()
        }
    }

    LaunchedEffect(permissionSettled) {
        if (!permissionSettled) return@LaunchedEffect
        mascot.arrive()
        val target = (check as? LinkCheck.Found)?.target
        if (target == null) {
            mascot.sorry()
            sentence = Sentences.forLinkCheck(context, check)
            dismissAfter(DISMISS_AFTER_FAILURE)
            return@LaunchedEffect
        }
        val controller = ShareController(context)
        if (controller.alreadyHave(target)) {
            delay(150)
            mascot.happy()
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            sentence = context.getString(R.string.already_have)
            dismissAfter(DISMISS_AFTER_SUCCESS)
            return@LaunchedEffect
        }

        val startedAt = SystemClock.elapsedRealtime()
        mascot.clipFill = 0f
        mascot.turnAway()
        val finalStatus = controller.start(target)
            .onEach { status ->
                if (status is ShareStatus.Working) {
                    status.fraction?.let { fraction ->
                        mascot.clipFill = fraction
                        glow = fraction
                        progressDescription = (fraction * 100).toInt()
                    }
                }
            }
            .first { it is ShareStatus.Finished || it is ShareStatus.WaitingForNetwork }

        val elapsed = SystemClock.elapsedRealtime() - startedAt
        if (!reducedMotion) delay((MIN_CHOREOGRAPHY_MILLIS - elapsed).coerceAtLeast(0L))
        progressDescription = null
        val outcome = (finalStatus as? ShareStatus.Finished)?.outcome
        if (outcome != null && outcome !is Outcome.Failed) {
            mascot.clipFill = 1f
            glow = 1f
            delay(if (reducedMotion) 0L else 260L)
            mascot.clipFill = null
            mascot.celebrate()
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            sky.shoot()
            sentence = Sentences.forOutcome(context, outcome)
            dismissAfter(DISMISS_AFTER_SUCCESS)
        } else {
            mascot.clipFill = null
            glow = 0f
            mascot.apologize()
            sentence = if (outcome != null) Sentences.forOutcome(context, outcome) else context.getString(R.string.no_internet)
            dismissAfter(DISMISS_AFTER_FAILURE)
        }
    }

    val sheetHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.62f).toDp() }
    ModalBottomSheet(
        onDismissRequest = onFinished,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = DalmaColors.Night,
        contentColor = DalmaColors.Milk,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = { DragHandle() },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        ShareSheetContent(
            mascot = mascot,
            sky = sky,
            glow = glow,
            sentence = sentence,
            progressPercent = progressDescription,
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight),
        )
    }
}

/** What is inside the sheet: sky, the sentence, Dalma. Also drawn on its own for screenshots. */
@Composable
fun ShareSheetContent(
    mascot: MascotState,
    sky: SkyState,
    glow: Float,
    sentence: String?,
    progressPercent: Int?,
    modifier: Modifier = Modifier,
) {
    val description = progressPercent?.let { stringResource(R.string.cd_dalma_working, it) } ?: stringResource(R.string.cd_dalma)
    Box(modifier) {
        Sky(Modifier.fillMaxSize(), state = sky, glow = glow)
        Sentence(sentence, Modifier.align(Alignment.TopCenter).padding(top = 52.dp))
        Mascot(
            state = mascot,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.8f)
                .aspectRatio(1f),
            contentDescription = description,
        )
    }
}

@SuppressLint("InlinedApi")
private fun shouldAskForNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
        !Prefs(context).notificationAsked
