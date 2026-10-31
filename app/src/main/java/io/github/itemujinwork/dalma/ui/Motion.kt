package io.github.itemujinwork.dalma.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.compositionLocalOf

/**
 * True when animations are switched off in the phone's settings: Dalma then shows
 * still images and the sky stands still, as the spec asks.
 */
val LocalReducedMotion = compositionLocalOf { false }

fun Context.animationsDisabled(): Boolean {
    val scale = try {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (_: Exception) {
        1f
    }
    return scale == 0f
}
