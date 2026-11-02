package io.github.itemujinwork.dalma

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.itemujinwork.dalma.home.HomeScreen
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import io.github.itemujinwork.dalma.core.SongPalette
import io.github.itemujinwork.dalma.data.Song
import io.github.itemujinwork.dalma.play.ListActions
import io.github.itemujinwork.dalma.play.ListUi
import io.github.itemujinwork.dalma.play.PlayerScreen
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

    private fun text(id: Int): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

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

    /** The sheet is 62 % of the screen, at the bottom, like the real one over YouTube. */
    private fun sheet(name: String, state: MascotState, glow: Float = 0f, sentence: String? = null, percent: Int? = null) = shot(name) {
        Box(Modifier.fillMaxSize().background(Color(0xFF101010))) {
            ShareSheetContent(
                mascot = state,
                sky = SkyState(),
                glow = glow,
                sentence = sentence,
                progressPercent = percent,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.62f)
                    .background(DalmaColors.Night, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            )
        }
    }

    @Test
    fun home_greeting() = shot("home_greeting") { HomeScreen(sleepy = false, checkForUpdates = false) }

    @Test
    fun home_sleepy() = shot("home_sleepy") { HomeScreen(sleepy = true, checkForUpdates = false) }

    @Test
    fun mascot_asleep_at_wall() = sheet("mascot_asleep_at_wall", mascot { sleepAtWallStill() })

    @Test
    fun mascot_night() = sheet("mascot_night", mascot { setExpression(Expression.NIGHT) })

    // --- The player ("Most szól"), with the sheet's sample song ---

    /** A cover like the mock-up's: a sky, a sun, a dark ground. */
    private fun fakeCover(skyColor: Int = 0xFFC66E2C.toInt(), ground: Int = 0xFF5A2E12.toInt(), sun: Int = 0xFFFFD166.toInt()): ImageBitmap =
        Bitmap.createBitmap(660, 660, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawColor(skyColor)
            paint.color = ground
            canvas.drawRect(0f, 400f, 660f, 660f, paint)
            paint.color = sun
            canvas.drawCircle(330f, 330f, 120f, paint)
        }.asImageBitmap()

    /** The sheet's sample colours: the sunrise cover (nappal) and the violet one (este). */
    private val sunrise = SongPalette(dominant = 0xFFC66E2C.toInt(), vivid = 0xFFF7C15C.toInt())
    private val violet = SongPalette(dominant = 0xFF543476.toInt(), vivid = 0xFFD68BE8.toInt())

    private val noActions = ListActions(onClose = {}, onPick = {}, onSearchOpen = {}, onQueryChange = {}, onDeleteRequest = {}, onDeleteConfirm = {}, onDeleteCancel = {})

    /** The mock-up's eight songs; every third has no cover. */
    private val sampleSongs = listOf(
        "Hajnali vonat" to "Kóbor Lámpás", "Negyedik emelet" to "Ibolya", "Tengerszem" to "Fenyő & Szél", "Két szó" to "Kóbor Lámpás",
        "Csendes part" to "Fenyő & Szél", "Lámpaoltás" to "Ibolya", "Üres peron" to "Kóbor Lámpás", "Esti villamos" to null,
    ).mapIndexed { i, (title, artist) ->
        Song(id = (i + 1).toLong(), uri = "content://media/external_primary/audio/media/${i + 1}".toUri(), fileName = "$title.opus", title = title, artist = artist, durationMillis = 200_000L + i * 7_000, addedAtSeconds = 0)
    }

    private val sampleThumbs: Map<Long, ImageBitmap> by lazy {
        mapOf(
            1L to fakeCover(), 2L to fakeCover(0xFF543476.toInt(), 0xFF2E1D40.toInt(), 0xFFD9A3F0.toInt()),
            3L to fakeCover(0xFF225480.toInt(), 0xFF102A40.toInt(), 0xFF9AD3EE.toInt()), 4L to fakeCover(),
            5L to fakeCover(0xFF225480.toInt(), 0xFF102A40.toInt(), 0xFF9AD3EE.toInt()), 6L to fakeCover(0xFF543476.toInt(), 0xFF2E1D40.toInt(), 0xFFD9A3F0.toInt()),
            7L to fakeCover(),
        )
    }

    private fun player(
        name: String,
        cover: ImageBitmap?,
        artist: String? = "Kóbor Lámpás",
        playing: Boolean = true,
        palette: SongPalette = sunrise,
        evening: Boolean = false,
        title: String = "Hajnali vonat",
        list: ListUi = ListUi(),
        sorry: MascotState = mascot(),
    ) = shot(name) {
        PlayerScreen(
            song = Song(id = 1, uri = "content://media/external_primary/audio/media/1".toUri(), fileName = "$title.opus", title = title, artist = artist, durationMillis = 235_000, addedAtSeconds = 0),
            cover = cover,
            palette = palette,
            evening = evening,
            playing = playing,
            positionMillis = 108_000,
            durationMillis = 235_000,
            mascot = mascot(),
            sky = SkyState(),
            list = list,
            actions = noActions,
            thumbnail = { song -> sampleThumbs[song.id] },
            sorryMascot = sorry,
            currentId = if (playing) 1 else null,
            onTap = {},
            onSwipe = {},
            onSwipeUp = {},
            onLongPress = {},
            onSeek = {},
            onMascotLongPress = {},
        )
    }

    @Test
    fun list_rows() = player("list_rows", cover = fakeCover(), list = ListUi(open = true, songs = sampleSongs, lists = listOf("YouTube Mix", "Futás", "Este")))

    @Test
    fun list_search() = player("list_search", cover = fakeCover(), list = ListUi(open = true, songs = sampleSongs, searchOpen = true))

    @Test
    fun list_delete() = player(
        "list_delete",
        cover = fakeCover(),
        list = ListUi(open = true, songs = sampleSongs, deleting = sampleSongs[2]),
        sorry = mascot { setExpression(Expression.SORRY); drop.snapTo(1f) },
    )

    @Test
    fun player_playing() = player("player_playing", cover = fakeCover())

    @Test
    fun player_no_cover() = player("player_no_cover", cover = null, artist = null, playing = false, palette = SongPalette.fromArtist(null), title = "Esti villamos")

    @Test
    fun player_evening() = player("player_evening", cover = fakeCover(0xFF543476.toInt(), 0xFF2E1D40.toInt(), 0xFFD9A3F0.toInt()), artist = "Ibolya", palette = violet, evening = true, title = "Negyedik emelet")

    @Test
    fun sheet_arriving() = sheet("sheet_arriving", mascot())

    @Test
    fun sheet_turned_clip_filling() = sheet("sheet_turned_40", mascot { turn.snapTo(1f); clipFill = 0.4f }, glow = 0.4f, percent = 40)

    @Test
    fun sheet_turned_clip_full() = sheet("sheet_turned_100", mascot { turn.snapTo(1f); clipFill = 1f }, glow = 1f, percent = 100)

    @Test
    fun sheet_saved() = sheet("sheet_saved", mascot { setExpression(Expression.HAPPY); sparkles.snapTo(1f) }, glow = 1f, sentence = text(R.string.saved))

    @Test
    fun sheet_already_have() = sheet("sheet_already_have", mascot { setExpression(Expression.HAPPY); sparkles.snapTo(1f) }, sentence = text(R.string.already_have))

    @Test
    fun sheet_no_internet() = sheet("sheet_no_internet", mascot { setExpression(Expression.SORRY); drop.snapTo(1f); tilt.snapTo(5f) }, sentence = text(R.string.no_internet))

    @Test
    fun sheet_private() = sheet("sheet_private", mascot { setExpression(Expression.SORRY); drop.snapTo(1f); tilt.snapTo(5f) }, sentence = text(R.string.private_video))

    @Test
    fun mascot_wink() = sheet("mascot_wink", mascot { greetStill() })

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
    fun pictures_happy() = sheet("pictures_happy", pictures { setExpression(Expression.HAPPY) }, glow = 1f, sentence = text(R.string.saved))

    @Test
    fun pictures_sorry() = sheet("pictures_sorry", pictures { setExpression(Expression.SORRY) }, sentence = text(R.string.private_video))

    @Test
    fun pictures_sleepy() = sheet("pictures_sleepy", pictures { setExpression(Expression.SLEEPY) })
}
