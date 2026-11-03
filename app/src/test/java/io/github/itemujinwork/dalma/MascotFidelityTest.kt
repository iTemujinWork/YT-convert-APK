package io.github.itemujinwork.dalma

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.itemujinwork.dalma.ui.LocalReducedMotion
import io.github.itemujinwork.dalma.ui.mascot.Expression
import io.github.itemujinwork.dalma.ui.mascot.Mascot
import io.github.itemujinwork.dalma.ui.mascot.MascotState
import io.github.itemujinwork.dalma.ui.theme.DalmaColors
import io.github.itemujinwork.dalma.ui.theme.DalmaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dalma alone in a square, one picture per pose file, on the night: these are laid over the
 * designer's SVGs rendered at the same size and compared pixel by pixel (see docs/handover.md,
 * "fidelity"). The square is the 240 box, so the SVG's 200-point viewBox lands on it exactly.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h400dp-xhdpi")
class MascotFidelityTest {
    @get:Rule
    val compose = createComposeRule()

    private fun pose(name: String, block: suspend MascotState.() -> Unit) {
        val state = MascotState(reducedMotion = true)
        runBlocking {
            state.closeness.snapTo(1f)
            state.block()
        }
        compose.setContent {
            DalmaTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    Box(Modifier.size(400.dp).background(DalmaColors.Night)) {
                        Mascot(state, Modifier.size(400.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/fidelity/$name.png")
    }

    @Test fun alap() = pose("alap") {}
    @Test fun orul() = pose("orul") { setExpression(Expression.HAPPY); sparkles.snapTo(1f) }
    @Test fun bocsanat() = pose("bocsanat") { sorryStill() }
    @Test fun almos() = pose("almos") { setExpression(Expression.SLEEPY); zz.snapTo(1f) }
    @Test fun hallgatEste() = pose("hallgat-este") { setExpression(Expression.NIGHT) }
    @Test fun alszikFal() = pose("alszik-fal") { sleepAtWallStill() }
    // The file holds the head, the back hair and the fringe at 4° alike; the motion's 1.2× and 0.6× are the canvas's.
    @Test fun kacsint() = pose("kacsint") { greetTilt.snapTo(4f); greetTiltBack.snapTo(4f); greetTiltFront.snapTo(4f); winkSquash.snapTo(1f); browMorph.snapTo(1f) }
    @Test fun tolt() = pose("tolt") { clipFill = 0.6f }
}
