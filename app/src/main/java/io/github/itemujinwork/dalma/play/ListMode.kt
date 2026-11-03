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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import io.github.itemujinwork.dalma.core.PullDown
import kotlinx.coroutines.CoroutineScope
import androidx.compose.animation.core.snap
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.pointer.PointerEventPass
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
import io.github.itemujinwork.dalma.ui.LocalReducedMotion
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
    /** The names of the .m3u lists in the folder, in the chips' order. */
    val lists: List<String> = emptyList(),
    /** The chosen list, or `null` for "Mind". */
    val selectedList: String? = null,
)

/** What the list can do. [onTouch] is any finger on the rows: a shuffled list stays up then. */
class ListActions(
    val onClose: () -> Unit,
    val onPick: (Song) -> Unit,
    val onSearchOpen: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onDeleteRequest: (Song) -> Unit,
    val onDeleteConfirm: (Song) -> Unit,
    val onDeleteCancel: () -> Unit,
    val onTouch: () -> Unit = {},
    val onSelectList: (String?) -> Unit = {},
    /** A pull past the mark on "Most szól": the list and the search together. */
    val onOpenWithSearch: () -> Unit = {},
    /** Scrolling up with an empty search: it closes. */
    val onCloseSearch: () -> Unit = {},
)

/** The chips: "Mind", then every list of the folder, then "+". The chosen one is mint. */
@Composable
internal fun Chips(lists: List<String>, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(stringResource(R.string.list_all), active = selected == null) { onSelect(null) }
        for (name in lists) Chip(name, active = selected == name) { onSelect(name) }
        Chip("+", active = false) { /* a new list: how it is named and filled is not in the brief yet */ }
    }
}

@Composable
private fun Chip(text: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        text = text,
        style = ChipText,
        color = if (active) DalmaColors.Night else DalmaColors.Pale,
        maxLines = 1,
        modifier = Modifier
            .then(if (active) Modifier.background(DalmaColors.Mint, shape) else Modifier.border(1.dp, DalmaColors.DeepTeal, shape))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * The rows, 64 dp each; a swipe to the left slides one aside for deletion, a long press shuffles.
 * When the order changes, every row slides to its new place on its own path (400 ms).
 */
@Composable
internal fun SongRows(
    songs: List<Song>,
    currentId: Long?,
    deleting: Song?,
    thumbnail: @Composable (Song) -> ImageBitmap?,
    onPick: (Song) -> Unit,
    onLongPress: () -> Unit,
    onTouch: () -> Unit,
    onDeleteRequest: (Song) -> Unit,
    pullToSearch: NestedScrollConnection,
    modifier: Modifier,
) {
    val reduced = LocalReducedMotion.current
    LazyColumn(
        modifier
            .nestedScroll(pullToSearch)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    onTouch()
                }
            },
    ) {
        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                thumb = thumbnail(song),
                current = song.id == currentId,
                aside = deleting?.id == song.id,
                onPick = { onPick(song) },
                onLongPress = onLongPress,
                onDeleteRequest = { onDeleteRequest(song) },
                modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = if (reduced) null else tween(400, easing = DalmaEase)),
            )
        }
    }
}

@Composable
private fun SongRow(song: Song, thumb: ImageBitmap?, current: Boolean, aside: Boolean, onPick: () -> Unit, onLongPress: () -> Unit, onDeleteRequest: () -> Unit, modifier: Modifier = Modifier) {
    // The pointer handlers live as long as the row; the handlers change with the screen's state.
    val pick by rememberUpdatedState(onPick)
    val longPress by rememberUpdatedState(onLongPress)
    val deleteRequest by rememberUpdatedState(onDeleteRequest)
    val asidePx = with(LocalDensity.current) { ASIDE.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(aside) { offset.animateTo(if (aside) -asidePx else 0f, tween(300, easing = DalmaEase)) }
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
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
                        if (offset.value <= -asidePx / 2) deleteRequest() else scope.launch { offset.animateTo(0f, tween(300, easing = DalmaEase)) }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, tween(300, easing = DalmaEase)) } },
                )
            }
            .pointerInput(song.id) { detectTapGestures(onTap = { pick() }, onLongPress = { longPress() }) },
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

/**
 * The search: a 44 dp row with a mint cursor and a hair line under it, no icon, no words. It
 * unfolds from 0 at the pull's mark (its line flashing mint), stays while the search is open, and
 * fades as the list goes back up. The keyboard comes with the focus.
 */
@Composable
internal fun SearchRow(query: String, onQueryChange: (String) -> Unit, open: Boolean, height: Dp, alpha: Float, flash: Float, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(open) { if (open) runCatching { focus.requestFocus() } }
    Box(modifier.height(height).alpha(alpha).clipToBounds()) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = SearchText,
            cursorBrush = SolidColor(DalmaColors.Mint),
            modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth().focusRequester(focus),
        )
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(lerp(DalmaColors.DeepTeal, DalmaColors.Mint, flash)))
    }
}

/** Which content the finger is pulling. */
internal enum class Pulling { None, NowPlaying, List }

/**
 * One gesture, two zones, one threshold: the content follows the finger one to one to the 96 dp
 * mark and at 0.4 past it; at the mark a buzz, and the search row unfolds. Letting go does
 * nothing, closes the list, or opens the search, by how far the finger went (PullDown in core).
 */
internal class PullController(
    private val markPx: Float,
    private val deadZonePx: Float,
    private val scope: CoroutineScope,
    private val haptic: HapticFeedback,
    private val reduced: Boolean,
) {
    /** The content's offset under the finger, px; after a release it animates home. */
    val offset = Animatable(0f)

    /** The list's standing shift while the search is open: 0, or the mark. */
    val base = Animatable(0f)

    /** The search line's one-shot flash at the mark. */
    val flash = Animatable(0f)

    var pulling by mutableStateOf(Pulling.None)
        private set

    /** The finger is past the mark: the search zone. */
    var crossed by mutableStateOf(false)
        private set

    private var finger = 0f

    private fun spec(millis: Int) = if (reduced) snap() else tween<Float>(millis, easing = DalmaEase)

    fun begin(what: Pulling) {
        pulling = what
        finger = 0f
        crossed = false
    }

    /** The finger moved [dy] px; returns how much the content moved. */
    fun drag(dy: Float): Float {
        finger = (finger + dy).coerceAtLeast(0f)
        val before = offset.value
        val target = PullDown.content(finger, markPx)
        scope.launch { offset.snapTo(target) }
        val now = finger >= markPx
        if (now && !crossed) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                flash.snapTo(1f)
                flash.animateTo(0f, spec(300))
            }
        }
        crossed = now
        return target - before
    }

    /** Lets go: the outcome, with the content already on its way; the caller opens or closes the screens. */
    fun release(): PullDown.Outcome {
        val outcome = PullDown.outcome(finger, markPx, deadZonePx, canClose = pulling == Pulling.List)
        val from = pulling
        finger = 0f
        crossed = false
        when (outcome) {
            PullDown.Outcome.Search -> {
                // The list settles the mark lower; the excess over it goes home (250 ms in the list, 400 from "Most szól").
                pulling = Pulling.List
                scope.launch {
                    base.snapTo(markPx)
                    offset.snapTo(offset.value - markPx)
                    offset.animateTo(0f, spec(if (from == Pulling.List) 250 else 400))
                    pulling = Pulling.None
                }
            }
            PullDown.Outcome.Nothing -> scope.launch {
                offset.animateTo(0f, spec(300))
                pulling = Pulling.None
            }
            PullDown.Outcome.Close -> Unit // the rows go on down from here while they fade; settle() ends it
        }
        return outcome
    }

    /** After a close by pull, once the list has gone: the pull is over. */
    fun settle() {
        scope.launch { offset.snapTo(0f) }
        pulling = Pulling.None
    }

    /** The search closes: the list goes up to its place (the row fades with it). */
    suspend fun closeSearch() = base.animateTo(0f, spec(300))

    suspend fun openSearch() = base.snapTo(markPx)

    /**
     * The rows' side of it. A pull begins when the list is at its top and the finger drags, never
     * from a scroll's momentum; scrolling up with the search open and empty closes the search.
     */
    fun connection(enabled: () -> Boolean, searchOpen: () -> Boolean, searchEmpty: () -> Boolean, onRelease: (PullDown.Outcome) -> Unit, onCloseSearch: () -> Unit): NestedScrollConnection =
        object : NestedScrollConnection {
            private var closingSearch = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (pulling == Pulling.List && available.y < 0f && finger > 0f) {
                    // Pushing back up while pulled: the pull gives first, the rows scroll after.
                    val moved = drag(available.y)
                    return Offset(0f, moved)
                }
                if (searchOpen() && available.y < 0f && searchEmpty() && base.value > 0f) {
                    if (!closingSearch) {
                        closingSearch = true
                        onCloseSearch()
                    }
                    return available
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!enabled() || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                if (pulling == Pulling.None) begin(Pulling.List)
                drag(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                closingSearch = false
                if (pulling != Pulling.List || (finger <= 0f && offset.value <= 0f)) return Velocity.Zero
                onRelease(release())
                return available
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
