package io.github.itemujinwork.dalma

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import io.github.itemujinwork.dalma.core.YouTubeLinks
import io.github.itemujinwork.dalma.share.ShareSheet
import io.github.itemujinwork.dalma.ui.LocalReducedMotion
import io.github.itemujinwork.dalma.ui.animationsDisabled
import io.github.itemujinwork.dalma.ui.theme.DalmaTheme

/** "Dalma" in YouTube's Share menu. Everything happens in the sheet; the window stays see-through. */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        val shared = intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent?.getStringExtra(Intent.EXTRA_SUBJECT)
        val check = YouTubeLinks.check(shared)
        setContent {
            DalmaTheme {
                CompositionLocalProvider(LocalReducedMotion provides remember { animationsDisabled() }) {
                    ShareSheet(check = check, onFinished = { finish() })
                }
            }
        }
    }
}
