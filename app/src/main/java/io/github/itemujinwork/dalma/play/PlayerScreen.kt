package io.github.itemujinwork.dalma.play

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.itemujinwork.dalma.R
import io.github.itemujinwork.dalma.core.SongPalette
import io.github.itemujinwork.dalma.core.TimeText
import io.github.itemujinwork.dalma.data.Song
import io.github.itemujinwork.dalma.ui.LocalReducedMotion
import io.github.itemujinwork.dalma.ui.Sky
import io.github.itemujinwork.dalma.ui.SkyState
import io.github.itemujinwork.dalma.ui.mascot.Mascot
import io.github.itemujinwork.dalma.ui.mascot.MascotState
import io.github.itemujinwork.dalma.ui.theme.DalmaColors
import io.github.itemujinwork.dalma.ui.theme.Fraunces
import io.github.itemujinwork.dalma.ui.theme.Nunito

/**
 * "Most szól" and its list mode, one screen in two layouts, laid out in the dp of the 390 × 844
 * sheet. The cover is one element that shrinks into the top-left corner when the list opens;
 * the big title fades, the small one appears, the line slides down, Dalma fades, and the chips
 * and rows come up a beat later. The song's colours tint the sky and the walked part of the line
 * and cross-fade on a song change; Dalma's mint never follows them.
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
    list: ListUi,
    actions: ListActions,
    thumbnail: @Composable (Song) -> ImageBitmap?,
    sorryMascot: MascotState,
    onTap: () -> Unit,
    onSwipe: (direction: Int) -> Unit,
    onSwipeUp: () -> Unit,
    onLongPress: () -> Unit,
    onSeek: (positionMillis: Long) -> Unit,
    onMascotLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    /** The song in the queue, whose row is mint; `null` before anything plays. */
    currentId: Long? = null,
    /** 20:00–06:00: less of the song's colour in the sky. */
    evening: Boolean = false,
) {
    val reduced = LocalReducedMotion.current
    val transition = updateTransition(list.open, label = "list")
    fun <T> move(delay: Int = 0, duration: Int = 400): FiniteAnimationSpec<T> = if (reduced) snap() else tween(duration, delay, DalmaEase)
    val coverX by transition.animateDp({ move() }, "coverX") { if (it) 24.dp else 30.dp }
    val coverY by transition.animateDp({ move() }, "coverY") { if (it) 62.dp else 160.dp }
    val coverSize by transition.animateDp({ move() }, "coverSize") { if (it) 56.dp else 330.dp }
    val coverCorner by transition.animateDp({ move() }, "coverCorner") { if (it) 10.dp else 18.dp }
    val coverShadow by transition.animateDp({ move() }, "coverShadow") { if (it) 0.dp else 24.dp }
    val bigAlpha by transition.animateFloat({ move() }, "big") { if (it) 0f else 1f }
    val lineY by transition.animateDp({ move() }, "line") { if (it) LIST_LINE_TOP.dp else LINE_TOP.dp }
    val rowsAlpha by transition.animateFloat({ if (targetState) move(300, 300) else move(0, 300) }, "rows") { if (it) 1f else 0f }
    val rowsRise by transition.animateDp({ if (targetState) move(300, 300) else move(0, 300) }, "rowsRise") { if (it) 0.dp else 18.dp }
    val fadeStop by transition.animateFloat({ move() }, "fade") { if (it) 0.40f else 0.62f }
    val listOrMoving = list.open || transition.isRunning || rowsAlpha > 0f

    // The search pulls everything in the list down by 96 dp; while the finger pulls, it follows with resistance.
    val searchShift by animateDpAsState(if (list.open && list.searchOpen) PULL_MARK else 0.dp, if (reduced) snap() else tween(300, easing = DalmaEase), label = "search")
    val pull = remember { Animatable(0f) }
    val pullToSearch = rememberPullToSearch(enabled = list.open && !list.searchOpen, pull = pull, onOpen = actions.onSearchOpen)
    val shift = searchShift + with(LocalDensity.current) { pull.value.toDp() }

    val top by animateColorAsState(Color(palette.tint(if (list.open) 0.10f else if (evening) 0.22f else 0.32f)), tween(COLOR_FADE_MILLIS), label = "top")
    val walked by animateColorAsState(Color(palette.walkedLine()), tween(COLOR_FADE_MILLIS), label = "walked")
    val coverLight by animateColorAsState(Color(palette.coverLight()), tween(COLOR_FADE_MILLIS), label = "coverLight")
    val coverMid by animateColorAsState(Color(palette.dominant), tween(COLOR_FADE_MILLIS), label = "coverMid")
    val coverDark by animateColorAsState(Color(palette.coverDark()), tween(COLOR_FADE_MILLIS), label = "coverDark")

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to top, fadeStop to DalmaColors.Night, 1f to DalmaColors.Night)),
    ) {
        // Underneath everything: the line, Dalma and the list catch their own touches, the rest falls through here.
        Box(Modifier.fillMaxSize().songGestures(onTap, onSwipe, onSwipeUp, onLongPress))
        Sky(Modifier.fillMaxSize(), state = sky, ambientShootingStars = true)

        if (listOrMoving) {
            // Animated positions go through the lambda offset: layout moves, nothing recomposes.
            Chips(Modifier.offset { IntOffset(0, (CHIPS_TOP.dp + shift + rowsRise).roundToPx()) }.padding(start = 24.dp).alpha(rowsAlpha))
            SongRows(
                songs = list.songs,
                currentId = currentId,
                deleting = list.deleting,
                thumbnail = thumbnail,
                onPick = actions.onPick,
                onDeleteRequest = actions.onDeleteRequest,
                pullToSearch = pullToSearch,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = ROWS_TOP.dp + shift + rowsRise, bottom = (SHEET_HEIGHT - LIST_LINE_TOP + 16).dp)
                    .padding(horizontal = 24.dp)
                    .alpha(rowsAlpha),
            )
            Text(
                text = song.title,
                style = SmallTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset { IntOffset(94.dp.roundToPx(), (68.dp + shift).roundToPx()) }.padding(end = 24.dp).alpha(1f - bigAlpha),
            )
            Text(
                text = song.artist ?: stringResource(R.string.unknown_artist),
                style = SmallArtist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset { IntOffset(94.dp.roundToPx(), (94.dp + shift).roundToPx()) }.padding(end = 24.dp).alpha(1f - bigAlpha),
            )
            if (list.searchOpen || searchShift > 0.dp) {
                SearchRow(
                    query = list.query,
                    onQueryChange = actions.onQueryChange,
                    open = list.searchOpen,
                    modifier = Modifier.offset(y = 62.dp).fillMaxWidth().padding(horizontal = 24.dp).alpha(searchShift / PULL_MARK),
                )
            }
        }

        Cover(
            cover = cover,
            light = coverLight,
            mid = coverMid,
            dark = coverDark,
            corner = coverCorner,
            shadow = coverShadow,
            modifier = Modifier
                .offset { IntOffset(coverX.roundToPx(), (coverY + if (list.open) shift else 0.dp).roundToPx()) }
                .size(coverSize)
                .then(if (list.open) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = actions.onClose) else Modifier),
        )

        if (bigAlpha > 0f) {
            Text(
                text = song.title,
                style = Title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(y = 522.dp).fillMaxWidth().padding(horizontal = 24.dp).alpha(bigAlpha),
            )
            Text(
                text = song.artist ?: stringResource(R.string.unknown_artist),
                style = Artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(y = 556.dp).fillMaxWidth().padding(horizontal = 24.dp).alpha(bigAlpha),
            )
        }

        SongLine(
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            walked = walked,
            onSeek = onSeek,
            modifier = Modifier.offset { IntOffset(0, (lineY - (TOUCH_BAND / 2).dp).roundToPx()) }.fillMaxWidth().padding(horizontal = 24.dp),
        )

        if (bigAlpha > 0f) {
            Mascot(
                state = mascot,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-6).dp)
                    .size(190.dp)
                    .alpha(bigAlpha)
                    .then(if (list.open) Modifier else Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onMascotLongPress() }) }),
                contentDescription = stringResource(R.string.cd_dalma),
            )
        }

        DeleteSheet(song = list.deleting, mascot = sorryMascot, onConfirm = actions.onDeleteConfirm, onCancel = actions.onDeleteCancel)
    }
}

/** The cover as a rounded square with its shadow; without one, the song's colours (see [coverBrush]). */
@Composable
private fun Cover(cover: ImageBitmap?, light: Color, mid: Color, dark: Color, corner: Dp, shadow: Dp, modifier: Modifier) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .shadow(elevation = shadow, shape = shape, ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.45f))
            .clip(shape)
            .drawBehind { drawRect(coverBrush(size, light, mid, dark)) },
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

private const val SHEET_HEIGHT = 844
private const val LINE_TOP = 610
private const val LIST_LINE_TOP = 796
private const val CHIPS_TOP = 134
private const val ROWS_TOP = 182
private const val TOUCH_BAND = 44
private const val COLOR_FADE_MILLIS = 500

private val Title = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp, color = DalmaColors.Milk)
private val Artist = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, color = DalmaColors.Pale)
private val Time = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, color = DalmaColors.Dim)
