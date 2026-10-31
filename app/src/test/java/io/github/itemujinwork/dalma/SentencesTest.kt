package io.github.itemujinwork.dalma

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.itemujinwork.dalma.core.FailureKind
import io.github.itemujinwork.dalma.core.LinkCheck
import io.github.itemujinwork.dalma.core.Outcome
import io.github.itemujinwork.dalma.core.SavedSong
import io.github.itemujinwork.dalma.ui.Sentences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SentencesTest {
    private val hu: Locale = Locale.forLanguageTag("hu-HU")
    private val en = Locale.ENGLISH

    private fun context(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    @Test
    fun hungarianNumbersGetTheRightSuffix() {
        val expected = mapOf(
            1 to "1-et", 2 to "2-t", 3 to "3-at", 4 to "4-et", 5 to "5-öt", 6 to "6-ot", 7 to "7-et", 8 to "8-at", 9 to "9-et",
            10 to "10-et", 11 to "11-et", 12 to "12-t", 13 to "13-at", 20 to "20-at", 25 to "25-öt", 30 to "30-at", 40 to "40-et",
            50 to "50-et", 60 to "60-at", 70 to "70-et", 80 to "80-at", 90 to "90-et", 100 to "100-at", 300 to "300-at", 1000 to "1000-et",
        )
        for ((n, text) in expected) assertEquals(text, Sentences.accusative(n, "hu"))
        assertEquals("12", Sentences.accusative(12, "en"))
    }

    @Test
    fun sentencesInHungarian() {
        val c = context(hu)
        val song = SavedSong(videoId = "dQw4w9WgXcQ", uri = "content://x", fileName = "a.opus", savedAtMillis = 0)
        assertEquals("Elmentettem neked.", Sentences.forOutcome(c, Outcome.Saved(song)))
        assertEquals("Ez már megvan.", Sentences.forOutcome(c, Outcome.AlreadyHave))
        assertEquals("Elmentettem mind a 12-t.", Sentences.forOutcome(c, Outcome.SavedMany(12, 0, 0, false)))
        assertEquals("Elmentettem 10-et, 2-t nem értem\u00A0el.", Sentences.forOutcome(c, Outcome.SavedMany(10, 0, 2, false)))
        assertEquals("Elmentettem 3-at, 9 már\u00A0megvolt.", Sentences.forOutcome(c, Outcome.SavedMany(3, 9, 0, false)))
        assertEquals("Elmentettem az első 300-at.", Sentences.forOutcome(c, Outcome.SavedMany(300, 0, 0, true)))
        assertEquals("Ezek mind megvannak.", Sentences.forOutcome(c, Outcome.SavedMany(0, 5, 0, false)))
        assertEquals("Nincs net. Amint lesz,\u00A0elmentem.", Sentences.forOutcome(c, Outcome.Failed(FailureKind.NO_INTERNET, null)))
        assertEquals("Ezt nem érem el, mert\u00A0privát.", Sentences.forOutcome(c, Outcome.Failed(FailureKind.PRIVATE, null)))
        assertEquals("Ezt most nem érem el, próbáld\u00A0később.", Sentences.forOutcome(c, Outcome.Failed(FailureKind.ENGINE_OUTDATED, null)))
        assertEquals("Ezt nem ismerem, csak YouTube-ot\u00A0tudok.", Sentences.forLinkCheck(c, LinkCheck.NotYouTube))
        assertNull(Sentences.forLinkCheck(c, LinkCheck.Found(io.github.itemujinwork.dalma.core.Target.Video("dQw4w9WgXcQ"))))
    }

    @Test
    fun sentencesInEnglish() {
        val c = context(en)
        assertEquals("Saved all 12.", Sentences.forOutcome(c, Outcome.SavedMany(12, 0, 0, false)))
        assertEquals("No internet. I'll save it as soon as it's back.", Sentences.forOutcome(c, Outcome.Failed(FailureKind.NO_INTERNET, null)))
    }
}
