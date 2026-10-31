package io.github.itemujinwork.dalma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.itemujinwork.dalma.ui.theme.DalmaColors

/** The small mint pill at the top of a sheet, as in the mock-ups. */
@Composable
fun DragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 12.dp, bottom = 4.dp)
            .size(width = 36.dp, height = 4.dp)
            .background(DalmaColors.Mint, CircleShape),
    )
}
