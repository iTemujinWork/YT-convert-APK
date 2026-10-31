package io.github.itemujinwork.dalma.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The one sentence Dalma says at the end, in Fraunces, fading in from below. */
@Composable
fun Sentence(text: String?, modifier: Modifier = Modifier) {
    val reducedMotion = LocalReducedMotion.current
    AnimatedVisibility(
        visible = text != null,
        modifier = modifier,
        enter = if (reducedMotion) fadeIn(tween(0)) else fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 3 },
        exit = fadeOut(tween(if (reducedMotion) 0 else 200)),
    ) {
        Text(
            text = text.orEmpty(),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
        )
    }
}
