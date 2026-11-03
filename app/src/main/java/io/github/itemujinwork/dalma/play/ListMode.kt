package io.github.itemujinwork.dalma.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import io.github.itemujinwork.dalma.R
import io.github.itemujinwork.dalma.core.Lists
import io.github.itemujinwork.dalma.core.PullDown
import io.github.itemujinwork.dalma.core.SongPalette
import io.github.itemujinwork.dalma.data.Song
import io.github.itemujinwork.dalma.ui.LocalReducedMotion
import io.github.itemujinwork.dalma.ui.mascot.Mascot
import io.github.itemujinwork.dalma.ui.mascot.MascotState
import io.github.itemujinwork.dalma.ui.theme.DalmaColors
import io.github.itemujinwork.dalma.ui.theme.Fraunces
import io.github.itemujinwork.dalma.ui.theme.Nunito
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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
    /** The names of the .m3u lists in the folder, in the chips' order (the alphabet). */
    val lists: List<String> = emptyList(),
    /** The chosen list, or `null` for "Mind". */
    val selectedList: String? = null,
    /** The lists each song is in, by song id: the chips under a row opened to the right. */
    val listsOf: Map<Long, Set<String>> = emptyMap(),
    /** The row opened to the right, by song id. */
    val openRowId: Long? = null,
    /** The chip being renamed in place. */
    val renaming: String? = null,
    /** The list the sheet asks "Töröljem?" about. */
    val deletingList: String? = null,
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
    /** A row swiped right opens (its song); a finger anywhere else or a swipe left closes it (`null`). */
    val onOpenRow: (Song?) -> Unit = {},
    /** A chip under the open row: the song joins the list or leaves it, and the .m3u is written at once. */
    val onToggleList: (Song, String) -> Unit = { _, _ -> },
    /** The "+" under the open row, with a name typed: a list is born with this one song in it. */
    val onCreateList: (Song, String) -> Unit = { _, _ -> },
    /** A long press on a chip starts renaming it in place; `null` lets it go, nothing happened. */
    val onRenameStart: (String?) -> Unit = {},
    /** Enter in the chip: the name typed, or blank, which asks "Töröljem?". */
    val onRenameCommit: (old: String, typed: String) -> Unit = { _, _ -> },
    val onDeleteListConfirm: (String) -> Unit = {},
    val onDeleteListCancel: () -> Unit = {},
)

/**
 * The chip row: "Mind", then the lists in the alphabet's order, scrolling sideways under the
 * margins. The mint pill slides from the chosen chip to the next (250 ms) and the words' colour
 * goes with it. A long press on a list's chip (never on "Mind") renames it in place; a chip whose
 * list is gone closes to nothing (250 ms) and the others slide into its place; a new one slides in
 * from 8 dp at the end, and the row scrolls there.
 */
@Composable
internal fun Chips(
    lists: List<String>,
    selected: String?,
    renaming: String?,
    onSelect: (String?) -> Unit,
    onRenameStart: (String?) -> Unit,
    onRenameCommit: (String, String) -> Unit,
    onEditorBounds: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scroll = rememberScrollState()

    // Chips whose list just went stay for their closing; chips born now slide in.
    var previous by remember { mutableStateOf(lists) }
    val leaving = remember { mutableStateListOf<String>() }
    var born by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(lists) {
        val before = previous
        previous = lists
        val gone = before.filterNot { it in lists }
        val fresh = lists.filterNot { it in before }
        born = fresh.toSet()
        leaving += gone
        if (fresh.isNotEmpty()) {
            delay(50)
            if (reduced) scroll.scrollTo(scroll.maxValue) else scroll.animateScrollTo(scroll.maxValue, tween(250, easing = DalmaEase))
        }
        if (gone.isNotEmpty()) {
            if (!reduced) delay(250)
            leaving.removeAll(gone.toSet())
        }
    }
    val shown = remember(lists, leaving.toList()) { Lists.sorted((lists + leaving).distinct()) { it } }

    // The pill: drawn behind the chips, on its way from the chip chosen before to the one chosen now.
    // Each chip's place is read in the row's own coordinates at drawing time: a chip sits inside its
    // coming-and-going wrapper, so its place in that wrapper would always be nought.
    val bounds = remember { mutableStateMapOf<String?, LayoutCoordinates>() }
    var rowCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pillFrom by remember { mutableStateOf(selected) }
    var pillTo by remember { mutableStateOf(selected) }
    val slide = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected == pillTo) return@LaunchedEffect
        pillFrom = pillTo
        pillTo = selected
        slide.snapTo(0f)
        if (reduced) slide.snapTo(1f) else slide.animateTo(1f, tween(250, easing = DalmaEase))
    }
    // A chip just renamed starts with the mint frame of the editor and lets it go in 200 ms; while the
    // chosen chip itself is being renamed, the pill gives way to that frame.
    var justRenamed by remember { mutableStateOf<String?>(null) }
    val pillAlpha by animateFloatAsState(if (renaming != null && renaming == pillTo) 0f else 1f, if (reduced) snap() else tween(150), label = "pill")

    Box(modifier.fillMaxWidth().horizontalScroll(scroll)) {
        Row(
            Modifier
                .padding(horizontal = 24.dp)
                .onGloballyPositioned { rowCoordinates = it }
                .drawBehind {
                    val row = rowCoordinates?.takeIf { it.isAttached } ?: return@drawBehind
                    fun placeOf(name: String?): Rect? = bounds[name]?.takeIf { it.isAttached }?.let { Rect(row.localPositionOf(it, Offset.Zero), it.size.toSize()) }
                    val to = placeOf(pillTo) ?: return@drawBehind
                    val from = placeOf(pillFrom) ?: to
                    val rect = androidx.compose.ui.geometry.lerp(from, to, slide.value)
                    if (pillAlpha > 0f) drawRoundRect(DalmaColors.Mint, rect.topLeft, rect.size, CornerRadius(14.dp.toPx()), alpha = pillAlpha)
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChipSlot(
                text = stringResource(R.string.list_all),
                active = pillTo == null,
                onTap = { onSelect(null) },
                onLongPress = null,
                modifier = Modifier.onGloballyPositioned { bounds[null] = it },
            )
            for (name in shown) {
                key(name) {
                    val state = remember { MutableTransitionState(name !in born) }
                    state.targetState = name !in leaving
                    AnimatedVisibility(
                        visibleState = state,
                        enter = fadeIn(tween(250)) + slideInHorizontally(tween(250, easing = DalmaEase)) { with(density) { 8.dp.roundToPx() } },
                        exit = shrinkHorizontally(tween(250, easing = DalmaEase)) + fadeOut(tween(250)),
                    ) {
                        val placed = Modifier.onGloballyPositioned { bounds[name] = it }
                        if (renaming == name) {
                            ChipEditor(
                                initial = name,
                                onDone = { typed ->
                                    justRenamed = name
                                    onRenameCommit(name, typed)
                                },
                                onBounds = onEditorBounds,
                                modifier = placed,
                            )
                        } else {
                            ChipSlot(
                                text = name,
                                active = pillTo == name,
                                onTap = { onSelect(name) },
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onRenameStart(name)
                                },
                                fromMint = justRenamed == name,
                                modifier = placed,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One chip of the row: the words in a hair line of deep teal, or on the pill in the night's dark. */
@Composable
private fun ChipSlot(text: String, active: Boolean, onTap: () -> Unit, onLongPress: (() -> Unit)?, modifier: Modifier = Modifier, fromMint: Boolean = false) {
    val reduced = LocalReducedMotion.current
    val colour by animateColorAsState(if (active) DalmaColors.Night else DalmaColors.Pale, if (reduced) snap() else tween(250), label = "chipWords")
    val line by animateFloatAsState(if (active) 0f else 1f, if (reduced) snap() else tween(250), label = "chipLine")
    // After a rename the editor's mint frame goes back to deep teal in 200 ms.
    val mint = remember { Animatable(if (fromMint && !reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (mint.value > 0f) mint.animateTo(0f, tween(200)) }
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLongPress)
    Text(
        text = text,
        style = ChipText,
        color = colour,
        maxLines = 1,
        modifier = modifier
            .border(1.dp, lerp(DalmaColors.DeepTeal, DalmaColors.Mint, mint.value).copy(alpha = line), ChipShape)
            .pointerInput(Unit) { detectTapGestures(onTap = { tap() }, onLongPress = { long?.invoke() }) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * A chip being typed into: the frame turns mint (150 ms), the cursor sits at the end, the keyboard
 * comes up. The frame jumps with every letter, like any field, while the chips behind it follow in
 * 200 ms; born from the "+", it widens from that chip in 250 ms. Enter gives the words typed; the
 * caller decides what they mean.
 */
@Composable
private fun ChipEditor(initial: String, onDone: (String) -> Unit, onBounds: (Rect?) -> Unit, modifier: Modifier = Modifier, fromWidth: Dp? = null) {
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val done by rememberUpdatedState(onDone)
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val words = with(density) { measurer.measure(value.text.ifEmpty { " " }, ChipText).size.width.toDp() } + 2.dp
    val frameWidth = (words + 24.dp).coerceAtLeast(32.dp)
    val slot = remember { Animatable(with(density) { (fromWidth ?: frameWidth).toPx() }) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(frameWidth) {
        val target = with(density) { frameWidth.toPx() }
        if (reduced) slot.snapTo(target) else slot.animateTo(target, tween(if (first) 250 else 200, easing = DalmaEase))
        first = false
    }
    val mint = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduced) mint.snapTo(1f) else mint.animateTo(1f, tween(150))
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    DisposableEffect(Unit) { onDispose { onBounds(null) } }
    Box(modifier.width(with(density) { slot.value.toDp() }).onGloballyPositioned { onBounds(it.boundsInRoot()) }) {
        Box(
            Modifier
                .requiredWidth(frameWidth)
                .border(1.dp, lerp(DalmaColors.DeepTeal, DalmaColors.Mint, mint.value), ChipShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = ChipText.copy(color = DalmaColors.Milk),
                cursorBrush = SolidColor(DalmaColors.Mint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    done(value.text)
                }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
}

/**
 * The chip switch, as the strip has it: the old rows leave top-down 30 ms apart (180 ms each:
 * fading, 6 dp down, blurring), and from 250 ms on the new ones come the same way (220 ms each).
 * Half a second and it is over, because it is pressed fifty times a day.
 */
internal class Cascade(private val reduced: Boolean, private val scope: CoroutineScope) {
    /** The rows of the list before, on their way out; `null` when none are. */
    var leaving by mutableStateOf<List<Song>?>(null)
        private set
    var leavingFirst = 0
        private set
    var leavingOffset = 0
        private set

    /** Milliseconds since the switch; far along when there was none. */
    val clock = Animatable(1e6f)

    fun switch(old: List<Song>, firstVisible: Int, firstOffset: Int, count: Int) {
        leaving = old
        leavingFirst = firstVisible
        leavingOffset = firstOffset
        val total = ENTER_START + ENTER_MILLIS + STEP_MILLIS * (count - 1).coerceIn(0, MAX_STEPS)
        scope.launch {
            clock.snapTo(0f)
            if (reduced) clock.snapTo(total.toFloat()) else clock.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
            leaving = null
        }
    }

    /** 0 → 1 as the row at [index] leaves. */
    fun exit(index: Int): Float = DalmaEase.transform(((clock.value - STEP_MILLIS * (index - leavingFirst).coerceIn(0, MAX_STEPS)) / EXIT_MILLIS).coerceIn(0f, 1f))

    /** 0 → 1 as the row at [index] comes. */
    fun enter(index: Int): Float = DalmaEase.transform(((clock.value - ENTER_START - STEP_MILLIS * index.coerceAtMost(MAX_STEPS)) / ENTER_MILLIS).coerceIn(0f, 1f))

    companion object {
        const val STEP_MILLIS = 30
        const val EXIT_MILLIS = 180
        const val ENTER_START = 250
        const val ENTER_MILLIS = 220

        /** Rows below the first screen come with the last delay. */
        const val MAX_STEPS = 14
    }
}

private fun Modifier.cascadeExit(cascade: Cascade, index: Int): Modifier =
    graphicsLayer {
        val p = cascade.exit(index)
        alpha = 1f - p
        translationY = 6.dp.toPx() * p
    }.blur(3.dp * cascade.exit(index))

private fun Modifier.cascadeEnter(cascade: Cascade, index: Int): Modifier =
    if (cascade.leaving == null) {
        this
    } else {
        graphicsLayer {
            val p = cascade.enter(index)
            alpha = p
            translationY = -6.dp.toPx() * (1f - p)
        }.blur(3.dp * (1f - cascade.enter(index)))
    }

/** The rows of the list before the switch, kept until the cascade has taken them. */
private class Shown(var key: String?, var songs: List<Song>)

/**
 * The rows, 64 dp apart; a swipe to the left slides one aside for deletion, a swipe to the right
 * opens it to show its lists, a long press shuffles. When the order changes, every row slides to its
 * new place on its own path (400 ms); when the chip changes, the rows cascade out and in.
 */
@Composable
internal fun SongRows(
    songs: List<Song>,
    currentId: Long?,
    deleting: Song?,
    listKey: String?,
    lists: List<String>,
    listsOf: Map<Long, Set<String>>,
    openRowId: Long?,
    thumbnail: @Composable (Song) -> ImageBitmap?,
    onPick: (Song) -> Unit,
    onLongPress: () -> Unit,
    onTouch: () -> Unit,
    onDeleteRequest: (Song) -> Unit,
    onOpenRow: (Song?) -> Unit,
    onToggleList: (Song, String) -> Unit,
    onCreateList: (Song, String) -> Unit,
    onOpenBounds: (Rect?) -> Unit,
    pullToSearch: NestedScrollConnection,
    modifier: Modifier,
) {
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val cascade = remember { Cascade(reduced, scope) }
    val state = rememberLazyListState()
    val shown = remember { Shown(listKey, songs) }
    if (shown.key == listKey) shown.songs = songs
    LaunchedEffect(listKey) {
        if (shown.key == listKey) return@LaunchedEffect
        cascade.switch(shown.songs, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, songs.size)
        shown.key = listKey
        shown.songs = songs
        state.scrollToItem(0)
    }
    Box(modifier) {
        cascade.leaving?.let { old ->
            key(old) {
                // The rows of the list before, underneath, on their way out: not for touching.
                LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState(cascade.leavingFirst, cascade.leavingOffset), userScrollEnabled = false) {
                    itemsIndexed(old, key = { _, song -> "gone-${song.id}" }) { index, song ->
                        SongRow(
                            song = song,
                            thumb = thumbnail(song),
                            current = song.id == currentId,
                            aside = false,
                            open = false,
                            lists = emptyList(),
                            inLists = emptySet(),
                            onPick = {},
                            onLongPress = {},
                            onDeleteRequest = {},
                            onOpen = {},
                            onToggleList = {},
                            onCreateList = {},
                            onOpenBounds = {},
                            interactive = false,
                            modifier = Modifier.cascadeExit(cascade, index),
                        )
                    }
                }
            }
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .nestedScroll(pullToSearch)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        onTouch()
                    }
                },
            state = state,
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongRow(
                    song = song,
                    thumb = thumbnail(song),
                    current = song.id == currentId,
                    aside = deleting?.id == song.id,
                    open = openRowId == song.id,
                    lists = lists,
                    inLists = listsOf[song.id].orEmpty(),
                    onPick = { onPick(song) },
                    onLongPress = onLongPress,
                    onDeleteRequest = { onDeleteRequest(song) },
                    onOpen = { open -> onOpenRow(if (open) song else null) },
                    onToggleList = { name -> onToggleList(song, name) },
                    onCreateList = { name -> onCreateList(song, name) },
                    onOpenBounds = onOpenBounds,
                    modifier = Modifier
                        .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = if (reduced) null else tween(400, easing = DalmaEase))
                        .cascadeEnter(cascade, index),
                )
            }
        }
    }
}

/**
 * One row: the small cover, the title (mint when it plays), the artist, the dot. Swiped right it
 * follows the finger to 72 dp, springs back (250 ms) and opens from 60 to 104 dp (300 ms) with its
 * lists underneath; swiped left while open it closes; swiped left otherwise it slides aside for
 * deletion.
 */
@Composable
private fun SongRow(
    song: Song,
    thumb: ImageBitmap?,
    current: Boolean,
    aside: Boolean,
    open: Boolean,
    lists: List<String>,
    inLists: Set<String>,
    onPick: () -> Unit,
    onLongPress: () -> Unit,
    onDeleteRequest: () -> Unit,
    onOpen: (Boolean) -> Unit,
    onToggleList: (String) -> Unit,
    onCreateList: (String) -> Unit,
    onOpenBounds: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val reduced = LocalReducedMotion.current
    // The pointer handlers live as long as the row; the handlers change with the screen's state.
    val pick by rememberUpdatedState(onPick)
    val longPress by rememberUpdatedState(onLongPress)
    val deleteRequest by rememberUpdatedState(onDeleteRequest)
    val openRow by rememberUpdatedState(onOpen)
    val isOpen by rememberUpdatedState(open)
    val density = LocalDensity.current
    val asidePx = with(density) { ASIDE.toPx() }
    val pullPx = with(density) { ROW_PULL.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    fun spec(millis: Int) = if (reduced) snap() else tween<Float>(millis, easing = DalmaEase)
    LaunchedEffect(aside) { offset.animateTo(if (aside) -asidePx else 0f, spec(300)) }
    val height by animateDpAsState(if (open) OPEN_ROW else ROW, if (reduced) snap() else tween(300, easing = DalmaEase), label = "row")
    Column(
        modifier
            .fillMaxWidth()
            .height(height + ROW_GAP)
            .offset { IntOffset(offset.value.roundToInt(), 0) }
            .then(if (open) Modifier.onGloballyPositioned { onOpenBounds(it.boundsInRoot()) } else Modifier)
            .then(
                if (!interactive) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(song.id) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dx ->
                                    change.consume()
                                    val next = (offset.value + dx).coerceIn(-asidePx, pullPx)
                                    scope.launch { offset.snapTo(if (isOpen && next < 0f) next.coerceAtLeast(-pullPx / 3) else next) }
                                },
                                onDragEnd = {
                                    val x = offset.value
                                    when {
                                        isOpen && x < 0f -> {
                                            openRow(false)
                                            scope.launch { offset.animateTo(0f, spec(250)) }
                                        }
                                        x >= pullPx / 2 -> {
                                            openRow(true)
                                            scope.launch { offset.animateTo(0f, spec(250)) }
                                        }
                                        x <= -asidePx / 2 -> deleteRequest()
                                        else -> scope.launch { offset.animateTo(0f, spec(if (x > 0f) 250 else 300)) }
                                    }
                                },
                                onDragCancel = { scope.launch { offset.animateTo(0f, spec(300)) } },
                            )
                        }
                        .pointerInput(song.id) { detectTapGestures(onTap = { pick() }, onLongPress = { longPress() }) }
                },
            ),
    ) {
        Row(Modifier.fillMaxWidth().height(ROW), verticalAlignment = Alignment.Top) {
            Thumb(thumb, SongPalette.fromArtist(song.artist), Modifier.size(48.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(song.title, style = RowTitle, color = if (current) DalmaColors.Mint else DalmaColors.Milk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist ?: stringResource(R.string.unknown_artist), style = RowArtist, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (current) Box(Modifier.align(Alignment.CenterVertically).size(6.dp).background(DalmaColors.Mint, CircleShape))
        }
        // The row's own bounds cover the chip typed into underneath, so taps on its other chips stay inside.
        if (height > ROW) RowLists(lists, inLists, open, onToggleList, onCreateList, {}, Modifier.padding(top = 4.dp))
    }
}

/**
 * Under a row opened to the right: the lists (without "Mind"), the ones the song is in filled
 * mint, the rest framed, and the "+" at the end, the only one in the app and the only place a
 * list is born. The chips come 150 ms after the opening, from 8 dp on the left, 40 ms apart, and
 * go in 150 ms. A tap fills a chip or empties it (200 ms); the "+" widens into a chip to type in.
 */
@Composable
private fun RowLists(lists: List<String>, inLists: Set<String>, open: Boolean, onToggle: (String) -> Unit, onCreate: (String) -> Unit, onEditorBounds: (Rect?) -> Unit, modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    var naming by remember { mutableStateOf(false) }
    LaunchedEffect(open) { if (!open) naming = false }
    @Composable
    fun coming(index: Int): Float {
        val come by animateFloatAsState(
            if (open) 1f else 0f,
            if (reduced) snap() else if (open) tween(200, delayMillis = 150 + 40 * index, easing = DalmaEase) else tween(150),
            label = "rowChip",
        )
        return come
    }
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        lists.forEachIndexed { index, name ->
            val come = coming(index)
            RowChip(name, filled = name in inLists, onTap = { onToggle(name) }, Modifier.graphicsLayer { alpha = come; translationX = -8.dp.toPx() * (1f - come) })
        }
        val come = coming(lists.size)
        val arriving = Modifier.graphicsLayer { alpha = come; translationX = -8.dp.toPx() * (1f - come) }
        if (naming) {
            ChipEditor(
                initial = "",
                fromWidth = PLUS_WIDTH,
                onDone = { typed ->
                    naming = false
                    if (typed.isNotBlank()) onCreate(typed)
                },
                onBounds = onEditorBounds,
                modifier = arriving,
            )
        } else {
            RowChip("+", filled = false, onTap = { naming = true }, arriving)
        }
    }
}

/** A chip under the row: filled mint with the night's dark words when the song is in the list, a hair line otherwise; it fills or empties in 200 ms. */
@Composable
private fun RowChip(text: String, filled: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val fill by animateFloatAsState(if (filled) 1f else 0f, if (reduced) snap() else tween(200, easing = DalmaEase), label = "fill")
    val tap by rememberUpdatedState(onTap)
    Text(
        text = text,
        style = ChipText,
        color = lerp(DalmaColors.Pale, DalmaColors.Night, fill),
        maxLines = 1,
        modifier = modifier
            .background(DalmaColors.Mint.copy(alpha = fill), ChipShape)
            .border(1.dp, DalmaColors.DeepTeal.copy(alpha = 1f - fill), ChipShape)
            .pointerInput(Unit) { detectTapGestures(onTap = { tap() }) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * While a row is open to the right or a chip is being typed into, a finger anywhere else closes
 * it, and that is all the finger does: the touch is taken before anything under it sees it.
 */
internal fun Modifier.closeOnTapOutside(active: Boolean, inside: () -> Rect?, onOutside: () -> Unit): Modifier {
    if (!active) return this
    var coordinates: LayoutCoordinates? = null
    return onGloballyPositioned { coordinates = it }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val at = coordinates?.localToRoot(down.position) ?: return@awaitEachGesture
            if (inside()?.contains(at) != true) {
                down.consume()
                onOutside()
            }
        }
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
 * "Töröljem?" and one word to say yes, with Dalma sorry about it: for a song slid aside and for a
 * list whose name was typed away. Anything tapped outside the sheet puts it away and nothing happens.
 */
@Composable
internal fun DeleteSheet(visible: Boolean, mascot: MascotState, onConfirm: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        if (visible) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onCancel() } })
        AnimatedVisibility(
            visible = visible,
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
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onConfirm)
                        .padding(horizontal = 26.dp, vertical = 10.dp),
                )
                Mascot(mascot, Modifier.align(Alignment.BottomStart).offset(x = (-20).dp).size(200.dp))
            }
        }
    }
}

internal val ASIDE = 96.dp
internal val PULL_MARK = 96.dp

/** A row swiped right follows the finger this far. */
private val ROW_PULL = 72.dp

/** A row is 60 dp with 4 dp under it (64 apart); opened, 104. */
private val ROW = 60.dp
private val OPEN_ROW = 104.dp
private val ROW_GAP = 4.dp

/** About the width of the "+" chip: the chip to type into widens from here. */
private val PLUS_WIDTH = 34.dp

private val ChipShape = RoundedCornerShape(14.dp)

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
