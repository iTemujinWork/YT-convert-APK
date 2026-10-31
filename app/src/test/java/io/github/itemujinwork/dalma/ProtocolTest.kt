package io.github.itemujinwork.dalma

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.itemujinwork.dalma.core.FailureKind
import io.github.itemujinwork.dalma.core.Outcome
import io.github.itemujinwork.dalma.core.SavedSong
import io.github.itemujinwork.dalma.core.Target
import io.github.itemujinwork.dalma.work.Protocol
import io.github.itemujinwork.dalma.work.SavePhase
import io.github.itemujinwork.dalma.work.SaveProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ProtocolTest {
    @Test
    fun targetsRoundTrip() {
        assertEquals(Target.Video("dQw4w9WgXcQ"), Protocol.target(Protocol.targetData(Target.Video("dQw4w9WgXcQ"))))
        assertEquals(Target.Playlist("PLabc"), Protocol.target(Protocol.targetData(Target.Playlist("PLabc"))))
        assertNull(Protocol.target(androidx.work.Data.EMPTY))
    }

    @Test
    fun outcomesRoundTrip() {
        val song = SavedSong(videoId = "dQw4w9WgXcQ", uri = "content://x", fileName = "a.opus", title = "T", artist = "A", savedAtMillis = 5)
        val saved = Protocol.outcome(Protocol.outcomeData(Outcome.Saved(song))) as Outcome.Saved
        assertEquals("T", saved.song.title)
        assertEquals("A", saved.song.artist)
        assertEquals(Outcome.SavedMany(3, 2, 1, true), Protocol.outcome(Protocol.outcomeData(Outcome.SavedMany(3, 2, 1, true))))
        assertEquals(Outcome.AlreadyHave, Protocol.outcome(Protocol.outcomeData(Outcome.AlreadyHave)))
        assertEquals(Outcome.Failed(FailureKind.PRIVATE, "p"), Protocol.outcome(Protocol.outcomeData(Outcome.Failed(FailureKind.PRIVATE, "p"))))
        assertNull(Protocol.outcome(androidx.work.Data.EMPTY))
    }

    @Test
    fun progressRoundTrip() {
        val p = SaveProgress.from(SaveProgress(SavePhase.DOWNLOADING, 0.42f, 2, 7).toData())!!
        assertEquals(SavePhase.DOWNLOADING, p.phase)
        assertEquals(0.42f, p.fraction!!, 0.0001f)
        assertEquals(2, p.index)
        assertEquals(7, p.count)
        assertNull(SaveProgress.from(SaveProgress(SavePhase.PREPARING, null, 0, 0).toData())!!.fraction)
        assertNull(SaveProgress.from(androidx.work.Data.EMPTY))
    }
}
