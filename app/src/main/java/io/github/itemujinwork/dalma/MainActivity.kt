package io.github.itemujinwork.dalma

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.itemujinwork.dalma.home.HomeScreen
import io.github.itemujinwork.dalma.ui.LocalReducedMotion
import io.github.itemujinwork.dalma.ui.animationsDisabled
import io.github.itemujinwork.dalma.ui.theme.DalmaTheme
import io.github.itemujinwork.dalma.work.EngineUpdateWorker

/** Opening the app: Dalma greets and is there, as if alive. Nothing to press. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        EngineUpdateWorker.enqueue(this)
        setContent {
            DalmaTheme {
                CompositionLocalProvider(LocalReducedMotion provides remember { animationsDisabled() }) {
                    HomeScreen()
                }
            }
        }
    }
}
