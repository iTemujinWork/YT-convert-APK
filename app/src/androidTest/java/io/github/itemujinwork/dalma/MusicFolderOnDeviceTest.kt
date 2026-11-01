package io.github.itemujinwork.dalma

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.itemujinwork.dalma.data.MusicFolder
import io.github.itemujinwork.dalma.data.MusicStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The real MediaStore: a file put into Music/Dalma is in the folder Dalma reads, and once it
 * is deleted elsewhere it is gone without a fuss. Runs on an emulator or a phone.
 */
@RunWith(AndroidJUnit4::class)
class MusicFolderOnDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun aFileInTheFolderAppearsAndALostOneDisappears() {
        val name = "Dalma teszt ${System.currentTimeMillis()}.opus"
        val source = File(context.cacheDir, "teszt.opus").apply { writeBytes(ByteArray(64)) }
        val uri = MusicStore(context).save(source, name)
        try {
            val song = MusicFolder(context).read().firstOrNull { it.uri == uri }
            assertTrue("the new file is in the folder", song != null)
            assertEquals(name, song!!.fileName)
        } finally {
            context.contentResolver.delete(uri, null, null)
            source.delete()
        }
        assertTrue("the deleted file is let go", MusicFolder(context).read().none { it.uri == uri })
    }
}
