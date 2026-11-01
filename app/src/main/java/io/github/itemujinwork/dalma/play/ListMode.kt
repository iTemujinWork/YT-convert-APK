package io.github.itemujinwork.dalma.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.itemujinwork.dalma.R
import io.github.itemujinwork.dalma.core.SongPalette
import io.github.itemujinwork.dalma.data.Song
import io.github.itemujinwork.dalma.ui.mascot.Mascot
import io.github.itemujinwork.dalma.ui.mascot.MascotState
import io.github.itemujinwork.dalma.ui.theme.DalmaColors
import io.github.itemujinwork.dalma.ui.theme.Fraunces
import io.github.itemujinwork.dalma.ui.theme.Nunito
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The sheet's easing for every move: quick to leave, slow to land. */
val DalmaEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** What the list shows: [songs] are the rows, already narrowed by [query]; [deleting] is the row slid aside. */
data class ListUi(
    val open: Boolean = false,
    val songs: List<Song> = emptyList(),
    val searchOpen: Boolean = false,
    val query: String = "",
    val deleting: Song? = null,
)

/** What the list can do. */
class ListActions(
    val onClose: () -> Unit,
    val onPick: (Song) -> Unit,
    val onSearchOpen: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onDeleteRequest: (Song) -> Unit,
    val onDeleteConfirm: (Song) -> Unit,
    val onDeleteCancel: () -> Unit,
)

/** The chips: "Mind" is on, "+" will make a list (step 7). */
@Composable
internal fun Chips(modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(stringResource(R.string.list_all), active = true)
        Chip("+", active = false)
    }
}

@Composable
private fun Chip(text: String, active: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        text = text,
        style = ChipText,
        color = if (active) DalmaColors.Night else DalmaColors.Pale,
        modifier = Modifier
            .then(if (active) Modifier.background(DalmaColors.Mint, shape) else Modifier.border(1.dp, DalmaColors.DeepTeal, shape))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** The rows, 64 dp each; a swipe to the left slides one aside for deletion. */
@Composable
internal fun SongRows(
    songs: List<Song>,
    currentId: Long?,
    deleting: Song?,
    thumbnail: @Composable (Song) -> ImageBitmap?,
    onPick: (Song) -> Unit,
    onDeleteRequest: (Song) -> Unit,
    pullToSearch: NestedScrollConnection,
    modifier: Modifier,
) {
    LazyColumn(modifier.nestedScroll(pullToSearch)) {
        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                thumb = thumbnail(song),
                current = song.id == currentId,
                aside = deleting?.id == song.id,
                onPick = { onPick(song) },
                onDeleteRequest = { onDeleteRequest(song) },
            )
        }
    }
}

@Composable
private fun SongRow(song: Song, thumb: ImageBitmap?, current: Boolean, aside: Boolean, onPick: () -> Unit, onDeleteRequest: () -> Unit) {
    val asidePx = with(LocalDensity.current) { ASIDE.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(aside) { offset.animateTo(if (aside) -asidePx else 0f, tween(300, easing = DalmaEase)) }
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .offset { IntOffset(offset.value.roundToInt(), 0) }
            .pointerInput(song.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + dx).coerceIn(-asidePx, 0f)) }
                    },
                    onDragEnd = {
                        if (offset.value <= -asidePx / 2) onDeleteRequest() else scope.launch { offset.animateTo(0f, tween(300, easing = DalmaEase)) }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, tween(300, easing = DalmaEase)) } },
                )
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onPick),
    ) {
        Thumb(thumb, SongPalette.fromArtist(song.artist), Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(song.title, style = RowTitle, color = if (current) DalmaColors.Mint else DalmaColors.Milk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist ?: stringResource(R.string.unknown_artist), style = RowArtist, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (current) Box(Modifier.align(Alignment.CenterVertically).size(6.dp).background(DalmaColors.Mint, CircleShape))
    }
}

/** A small cover; without one, the song's colours, like the big square. */
@Composable
internal fun Thumb(cover: ImageBitmap?, palette: SongPalette, modifier: Modifier, corner: Dp = 10.dp) {
    val light = Color(palette.coverLight())
    val mid = Color(palette.dominant)
    val dark = Color(palette.coverDark())
    Box(modifier.clip(RoundedCornerShape(corner)).drawBehind { drawRect(coverBrush(size, light, mid, dark)) }) {
        if (cover != null) Image(bitmap = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** The missing-cover fill: light to dominant to dark along the sheet's 150° (0° points up, 90° right). */
internal fun coverBrush(size: Size, light: Color, mid: Color, dark: Color): Brush {
    val angle = Math.toRadians(150.0)
    val direction = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
    val length = size.width * abs(direction.x) + size.height * abs(direction.y)
    val centre = Offset(size.width / 2f, size.height / 2f)
    return Brush.linearGradient(0f to light, 0.55f to mid, 1f to dark, start = centre - direction * (length / 2f), end = centre + direction * (length / 2f))
}

/** A 44 dp row with a mint cursor and a hair line under it: no icon, no words. The keyboard comes with the focus. */
@Composable
internal fun SearchRow(query: String, onQueryChange: (String) -> Unit, open: Boolean, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(open) { if (open) runCatching { focus.requestFocus() } }
    Box(modifier.height(44.dp)) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = SearchText,
            cursorBrush = SolidColor(DalmaColors.Mint),
            modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth().focusRequester(focus),
        )
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(DalmaColors.DeepTeal))
    }
}

/**
 * A firm pull on the list from its top opens the search: the list follows the finger with
 * resistance, buzzes once past the 96 dp mark, and opens when let go past it. A plain scroll
 * never gets there.
 */
@Composable
internal fun rememberPullToSearch(enabled: Boolean, pull: Animatable<Float, AnimationVector1D>, onOpen: () -> Unit): NestedScrollConnection {
    val mark = with(LocalDensity.current) { PULL_MARK.toPx() }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var crossed by remember { mutableStateOf(false) }
    return remember(enabled, mark) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pull.value <= 0f || available.y >= 0f) return Offset.Zero
                // Pushing back up while stretched: the stretch gives first, the list scrolls after.
                val give = maxOf(available.y, -pull.value)
                scope.launch { pull.snapTo(pull.value + give) }
                return Offset(0f, give)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                val stretched = pull.value + available.y * 0.5f
                scope.launch { pull.snapTo(stretched) }
                if (!crossed && stretched >= mark) {
                    crossed = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val stretched = pull.value > 0f
                val open = crossed
                crossed = false
                if (open) onOpen()
                pull.animateTo(0f, tween(300, easing = DalmaEase))
                return if (stretched) available else Velocity.Zero
            }
        }
    }
}

/**
 * "Töröljem?" and one word to say yes, with Dalma sorry about it. Anything tapped outside the
 * sheet puts it away and nothing happens.
 */
@Composable
internal fun DeleteSheet(song: Song?, mascot: MascotState, onConfirm: (Song) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(song) }
    if (song != null) shown = song // kept while the sheet slides away
    Box(modifier.fillMaxSize()) {
        if (song != null) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onCancel() } })
        AnimatedVisibility(
            visible = song != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(300, easing = DalmaEase)) { it } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(300, easing = DalmaEase)) { it } + fadeOut(tween(300)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(SheetColor, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Box(Modifier.align(Alignment.TopCenter).offset(y = 8.dp).size(36.dp, 4.dp).background(DalmaColors.DeepTeal, RoundedCornerShape(2.dp)))
                Text(stringResource(R.string.delete_question), style = DeleteTitle, modifier = Modifier.offset(x = 150.dp, y = 58.dp))
                Text(
                    text = stringResource(R.string.delete_yes),
                    style = YesText,
                    modifier = Modifier
                        .offset(x = 150.dp, y = 112.dp)
                        .background(DalmaColors.Mint, RoundedCornerShape(20.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { shown?.let(onConfirm) }
                        .padding(horizontal = 26.dp, vertical = 10.dp),
                )
                Mascot(mascot, Modifier.align(Alignment.BottomStart).offset(x = (-20).dp).size(200.dp))
            }
        }
    }
}

internal val ASIDE = 96.dp
internal val PULL_MARK = 96.dp

/** The sheet's ground: the night with a little white in it. */
private val SheetColor = Color(SongPalette.mix(SongPalette.NIGHT, SongPalette.WHITE, 0.06f))

internal val SmallTitle = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 22.sp, color = DalmaColors.Milk)
internal val SmallArtist = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, color = DalmaColors.Pale)
private val ChipText = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp)
private val RowTitle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp)
private val RowArtist = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, color = DalmaColors.Dim)
private val SearchText = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp, color = DalmaColors.Milk)
private val DeleteTitle = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp, color = DalmaColors.Mint)
private val YesText = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp, color = DalmaColors.Night)
