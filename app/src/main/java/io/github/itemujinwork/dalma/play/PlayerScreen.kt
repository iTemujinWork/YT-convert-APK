package io.github.itemujinwork.dalma.play

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.itemujinwork.dalma.R
import io.github.itemujinwork.dalma.core.SongPalette
import io.github.itemujinwork.dalma.core.TimeText
import io.github.itemujinwork.dalma.data.Song
import io.github.itemujinwork.dalma.ui.Sky
import io.github.itemujinwork.dalma.ui.SkyState
import io.github.itemujinwork.dalma.ui.mascot.Mascot
import io.github.itemujinwork.dalma.ui.mascot.MascotState
import io.github.itemujinwork.dalma.ui.theme.DalmaColors
import io.github.itemujinwork.dalma.ui.theme.Fraunces
import io.github.itemujinwork.dalma.ui.theme.Nunito
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Most szól": the cover, the title, the line and Dalma in the corner, laid out in the dp of the
 * 390 × 844 sheet. No buttons: the moves are on the whole screen, except the line (seeking) and
 * Dalma. The song's colours tint the top of the sky and the walked part of the line, and they
 * cross-fade in half a second when the song changes; Dalma's mint never follows them.
 */
@Composable
fun PlayerScreen(
    song: Song,
    cover: ImageBitmap?,
    palette: SongPalette,
    playing: Boolean,
    positionMillis: Long,
    durationMillis: Long,
    mascot: MascotState,
    sky: SkyState,
    onTap: () -> Unit,
    onSwipe: (direction: Int) -> Unit,
    onSwipeUp: () -> Unit,
    onLongPress: () -> Unit,
    onSeek: (positionMillis: Long) -> Unit,
    onMascotLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    /** 20:00–06:00: less of the song's colour in the sky. */
    evening: Boolean = false,
) {
    val top by animateColorAsState(Color(palette.top(evening)), tween(COLOR_FADE_MILLIS), label = "top")
    val walked by animateColorAsState(Color(palette.walkedLine()), tween(COLOR_FADE_MILLIS), label = "walked")
    val coverLight by animateColorAsState(Color(palette.coverLight()), tween(COLOR_FADE_MILLIS), label = "coverLight")
    val coverMid by animateColorAsState(Color(palette.dominant), tween(COLOR_FADE_MILLIS), label = "coverMid")
    val coverDark by animateColorAsState(Color(palette.coverDark()), tween(COLOR_FADE_MILLIS), label = "coverDark")

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to top, 0.62f to DalmaColors.Night, 1f to DalmaColors.Night)),
    ) {
        // Underneath everything: the line and Dalma catch their own touches, the rest falls through here.
        Box(Modifier.fillMaxSize().songGestures(onTap, onSwipe, onSwipeUp, onLongPress))
        Sky(Modifier.fillMaxSize(), state = sky, ambientShootingStars = true)

        Cover(cover, coverLight, coverMid, coverDark, Modifier.offset(x = 30.dp, y = 160.dp).size(330.dp))

        Text(
            text = song.title,
            style = Title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(y = 522.dp).fillMaxWidth().padding(horizontal = 24.dp),
        )
        Text(
            text = song.artist ?: stringResource(R.string.unknown_artist),
            style = Artist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(y = 556.dp).fillMaxWidth().padding(horizontal = 24.dp),
        )

        SongLine(
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            walked = walked,
            onSeek = onSeek,
            modifier = Modifier.offset(y = (LINE_TOP - TOUCH_BAND / 2).dp).fillMaxWidth().padding(horizontal = 24.dp),
        )

        Mascot(
            state = mascot,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-6).dp)
                .size(190.dp)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onMascotLongPress() }) },
            contentDescription = stringResource(R.string.cd_dalma),
        )
    }
}

/**
 * The cover as a rounded square with its shadow. Without one, the same square filled with the
 * song's colours, light to dark from the top-left corner (150° in the sheet): no icon, no placeholder.
 */
@Composable
private fun Cover(cover: ImageBitmap?, light: Color, mid: Color, dark: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .shadow(elevation = 24.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.45f))
            .clip(shape)
            .drawBehind {
                // CSS angles: 0° points up, 90° right; the gradient line runs through the centre.
                val angle = Math.toRadians(150.0)
                val direction = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
                val length = size.width * kotlin.math.abs(direction.x) + size.height * kotlin.math.abs(direction.y)
                val centre = Offset(size.width / 2f, size.height / 2f)
                drawRect(
                    Brush.linearGradient(
                        0f to light,
                        0.55f to mid,
                        1f to dark,
                        start = centre - direction * (length / 2f),
                        end = centre + direction * (length / 2f),
                    ),
                )
            },
    ) {
        if (cover != null) Image(bitmap = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/**
 * Two pixels, no dot, no handle. A finger on the 44 dp band seeks and shows the time below;
 * the seek itself happens when the finger lifts.
 */
@Composable
private fun SongLine(positionMillis: Long, durationMillis: Long, walked: Color, onSeek: (Long) -> Unit, modifier: Modifier) {
    var touch by remember { mutableStateOf<Float?>(null) }
    val fraction = touch ?: if (durationMillis > 0) (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f) else 0f
    val shown = touch?.let { (it * durationMillis).toLong() } ?: positionMillis
    Box(
        modifier
            .height((TOUCH_BAND + 10 + 16).dp)
            .pointerInput(durationMillis) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    touch = (down.position.x / width).coerceIn(0f, 1f)
                    down.consume()
                    drag(down.id) { change ->
                        touch = (change.position.x / width).coerceIn(0f, 1f)
                        change.consume()
                    }
                    val landed = touch
                    touch = null
                    if (landed != null && durationMillis > 0) onSeek((landed * durationMillis).toLong())
                }
            },
    ) {
        Canvas(Modifier.align(Alignment.TopCenter).offset(y = (TOUCH_BAND / 2 - 1).dp).fillMaxWidth().height(2.dp)) {
            val corner = CornerRadius(1.dp.toPx())
            drawRoundRect(color = DalmaColors.DeepTeal, cornerRadius = corner)
            drawRoundRect(color = walked, size = Size(size.width * fraction, size.height), cornerRadius = corner)
        }
        if (touch != null) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (TOUCH_BAND / 2 + 1 + 10).dp).fillMaxWidth(),
            ) {
                Text(TimeText.clock(shown), style = Time)
                Text(TimeText.remaining(shown, durationMillis), style = Time)
            }
        }
    }
}

private const val LINE_TOP = 610
private const val TOUCH_BAND = 44
private const val COLOR_FADE_MILLIS = 500

private val Title = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp, color = DalmaColors.Milk)
private val Artist = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, color = DalmaColors.Pale)
private val Time = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, color = DalmaColors.Dim)
