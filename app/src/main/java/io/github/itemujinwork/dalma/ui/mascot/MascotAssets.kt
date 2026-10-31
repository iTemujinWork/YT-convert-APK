package io.github.itemujinwork.dalma.ui.mascot

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject

/** One of the AI-made clips, decoded lazily by Android. */
internal class ClipAsset(val drawable: AnimatedImageDrawable, val durationMillis: Long)

/**
 * The AI-made Dalma, when tools/dalma-assets.py has put her into assets/dalma/.
 * Stills by name (alap, fordul, orul, bocsanat, almos), clips by name (fordul,
 * vissza_orul, pislog, almos, koszon, korbenez), and the mask of the hair clip.
 */
internal class MascotImages(
    val stills: Map<String, ImageBitmap>,
    val clips: Map<String, ClipAsset>,
    val clipMask: ImageBitmap?,
    /** left, top, right, bottom of the clip inside a [size] x [size] picture. */
    val clipBox: IntArray?,
    val size: Int,
) {
    fun still(name: String): ImageBitmap? = stills[name] ?: stills["alap"]
    fun clip(name: String): ClipAsset? = clips[name]
}

internal object MascotAssets {
    private const val TAG = "DalmaAssets"
    private const val FOLDER = "dalma"

    @Volatile
    private var loaded = false
    private var images: MascotImages? = null

    /** Loads once (a few hundred milliseconds the first time), `null` when there are no pictures. */
    fun load(context: Context): MascotImages? {
        if (loaded) return images
        synchronized(this) {
            if (loaded) return images
            images = try {
                read(context.applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "mascot pictures unusable, using the vector Dalma", e)
                null
            }
            loaded = true
            return images
        }
    }

    /** Call early (e.g. from Application.onCreate on a worker thread) so the first sheet is instant. */
    fun warmUp(context: Context) {
        Thread({ load(context) }, "dalma-assets").start()
    }

    private fun read(context: Context): MascotImages? {
        val assets = context.assets
        val manifestText = try {
            assets.open("$FOLDER/manifest.json").bufferedReader().readText()
        } catch (_: Exception) {
            return null
        }
        val manifest = JSONObject(manifestText)
        val stills = HashMap<String, ImageBitmap>()
        val stillNames = manifest.optJSONObject("stills") ?: JSONObject()
        for (key in stillNames.keys()) {
            val file = stillNames.getString(key)
            val bitmap = assets.open("$FOLDER/$file").use { BitmapFactory.decodeStream(it) } ?: continue
            stills[key] = bitmap.asImageBitmap()
        }
        if (stills["alap"] == null) return null

        val clips = HashMap<String, ClipAsset>()
        val clipEntries = manifest.optJSONObject("clips") ?: JSONObject()
        for (key in clipEntries.keys()) {
            val entry = clipEntries.getJSONObject(key)
            try {
                val source = ImageDecoder.createSource(assets, "$FOLDER/${entry.getString("file")}")
                val drawable = ImageDecoder.decodeDrawable(source) as? AnimatedImageDrawable ?: continue
                val frames = entry.optInt("frames", 0)
                val fps = entry.optInt("fps", 24).coerceAtLeast(1)
                val duration = if (frames > 0) frames * 1000L / fps else 5_000L
                clips[key] = ClipAsset(drawable, duration)
            } catch (e: Exception) {
                Log.w(TAG, "clip $key unusable", e)
            }
        }

        var mask: ImageBitmap? = null
        var box: IntArray? = null
        var size = stills["alap"]!!.width
        manifest.optJSONObject("clip_mask")?.let { entry ->
            mask = runCatching { assets.open("$FOLDER/${entry.getString("file")}").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
            val array = entry.optJSONArray("box")
            if (array != null && array.length() == 4) box = IntArray(4) { array.getInt(it) }
            size = entry.optInt("size", size)
        }
        return MascotImages(stills, clips, mask, box, size)
    }
}
