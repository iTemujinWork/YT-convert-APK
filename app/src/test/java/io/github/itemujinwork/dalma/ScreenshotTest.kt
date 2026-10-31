package io.github.itemujinwork.dalma

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.itemujinwork.dalma.home.HomeScreen
import io.github.itemujinwork.dalma.share.ShareSheetContent
import io.github.itemujinwork.dalma.ui.LocalReducedMotion
import io.github.itemujinwork.dalma.ui.SkyState
import io.github.itemujinwork.dalma.ui.mascot.Expression
import io.github.itemujinwork.dalma.ui.mascot.MascotAssets
import io.github.itemujinwork.dalma.ui.mascot.MascotState
import io.github.itemujinwork.dalma.ui.theme.DalmaColors
import io.github.itemujinwork.dalma.ui.theme.DalmaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pictures of every screen state, taken on a virtual Pixel 7. CI uploads them, so the
 * look can be reviewed without a phone. Animations are off here on purpose.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            DalmaTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) { content() }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun mascot(block: suspend MascotState.() -> Unit = {}): MascotState {
        val state = MascotState(reducedMotion = true)
        runBlocking {
            state.closeness.snapTo(1f)
            state.block()
        }
        return state
    }

    private fun sheet(name: String, state: MascotState, glow: Float = 0f, sentence: String? = null, percent: Int? = null) = shot(name) {
        Box(Modifier.fillMaxSize().background(DalmaColors.Night)) {
            ShareSheetContent(mascot = state, sky = SkyState(), glow = glow, sentence = sentence, progressPercent = percent, modifier = Modifier.fillMaxSize())
        }
    }

    @Test
    fun home_greeting() = shot("home_greeting") { HomeScreen(sleepy = false, checkForUpdates = false) }

    @Test
    fun home_sleepy() = shot("home_sleepy") { HomeScreen(sleepy = true, checkForUpdates = false) }

    @Test
    fun sheet_arriving() = sheet("sheet_arriving", mascot())

    @Test
    fun sheet_turned_clip_filling() = sheet("sheet_turned_40", mascot { turn.snapTo(1f); clipFill = 0.4f }, glow = 0.4f, percent = 40)

    @Test
    fun sheet_turned_clip_full() = sheet("sheet_turned_100", mascot { turn.snapTo(1f); clipFill = 1f }, glow = 1f, percent = 100)

    @Test
    fun sheet_saved() = sheet("sheet_saved", mascot { setExpression(Expression.HAPPY); sparkles.snapTo(1f) }, glow = 1f, sentence = "Elmentettem neked.")

    @Test
    fun sheet_already_have() = sheet("sheet_already_have", mascot { setExpression(Expression.HAPPY); sparkles.snapTo(1f) }, sentence = "Ez már megvan.")

    @Test
    fun sheet_no_internet() = sheet("sheet_no_internet", mascot { setExpression(Expression.SORRY); drop.snapTo(1f); tilt.snapTo(5f) }, sentence = "Nincs net. Amint lesz, elmentem.")

    @Test
    fun sheet_private() = sheet("sheet_private", mascot { setExpression(Expression.SORRY); drop.snapTo(1f); tilt.snapTo(5f) }, sentence = "Ezt nem érem el, mert privát.")

    @Test
    fun mascot_wink() = sheet("mascot_wink", mascot { nod.snapTo(7f); greetStill() })

    @Test
    fun mascot_blink() = sheet("mascot_blink", mascot { blinkStill(2) })

    @Test
    fun mascot_half_turn() = sheet("mascot_half_turn", mascot { turn.snapTo(0.5f); clipFill = 0.7f })

    @Test
    fun mascot_sleepy() = sheet("mascot_sleepy", mascot { setExpression(Expression.SLEEPY); zz.snapTo(1f) })

    @Test
    fun mascot_look_left() = sheet("mascot_look_left", mascot { gazeX.snapTo(-1f) })

    // --- The picture body, with small fixtures made by tools/dalma-assets.py (src/test/assets/dalma-test) ---

    private fun pictures(block: suspend MascotState.() -> Unit = {}): MascotState {
        val images = MascotAssets.load(ApplicationProvider.getApplicationContext(), "dalma-test")
        assertNotNull("the test fixtures load", images)
        val state = MascotState(reducedMotion = true, images = images)
        runBlocking {
            state.closeness.snapTo(1f)
            state.block()
        }
        return state
    }

    @Test
    fun pictures_idle() = sheet("pictures_idle", pictures())

    @Test
    fun pictures_turned_clip_filling() = sheet("pictures_turned_40", pictures { turnAway(); clipFill = 0.4f }, glow = 0.4f, percent = 40)

    @Test
    fun pictures_happy() = sheet("pictures_happy", pictures { setExpression(Expression.HAPPY) }, glow = 1f, sentence = "Elmentettem neked.")

    @Test
    fun pictures_sorry() = sheet("pictures_sorry", pictures { setExpression(Expression.SORRY) }, sentence = "Ezt nem érem el, mert privát.")

    @Test
    fun pictures_sleepy() = sheet("pictures_sleepy", pictures { setExpression(Expression.SLEEPY) })
}
