package io.github.itemujinwork.dalma

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.itemujinwork.dalma.play.rememberForScreen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The player's controller must live as long as the screen. Reading it from the state inside
 * onDispose released it the moment it arrived, and then every tap was ignored: no sound.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ScreenLifetimeTest {
    @get:Rule
    val compose = createComposeRule()

    private class Controller {
        var released = false
    }

    @Test
    fun theControllerStaysWhileTheScreenIsUpAndGoesWithIt() {
        val controller = Controller()
        var onScreen by mutableStateOf(true)
        var seen: Controller? = null
        compose.setContent {
            if (onScreen) {
                seen = rememberForScreen(make = { controller }, release = { it.released = true })
            }
        }
        compose.waitForIdle()

        assertSame("the screen gets the controller", controller, seen)
        assertFalse("it is not released the moment it arrives", controller.released)

        onScreen = false
        compose.waitForIdle()
        assertTrue("it is released when the screen goes", controller.released)
    }
}
