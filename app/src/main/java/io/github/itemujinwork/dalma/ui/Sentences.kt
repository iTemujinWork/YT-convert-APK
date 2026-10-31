package io.github.itemujinwork.dalma.ui

import android.content.Context
import io.github.itemujinwork.dalma.R
import io.github.itemujinwork.dalma.core.FailureKind
import io.github.itemujinwork.dalma.core.LinkCheck
import io.github.itemujinwork.dalma.core.Outcome
import kotlin.random.Random

/** Dalma's one sentence for every way a share can end. */
object Sentences {
    fun forFailure(kind: FailureKind): Int = when (kind) {
        FailureKind.NO_INTERNET -> R.string.no_internet
        FailureKind.PRIVATE -> R.string.private_video
        FailureKind.AGE_RESTRICTED -> R.string.age_restricted
        FailureKind.MEMBERS_ONLY -> R.string.members_only
        FailureKind.REGION_BLOCKED -> R.string.region_blocked
        FailureKind.UNAVAILABLE -> R.string.unavailable
        FailureKind.LIVE -> R.string.live
        FailureKind.NOT_YOUTUBE -> R.string.not_youtube
        FailureKind.DISK_FULL -> R.string.disk_full
        FailureKind.THROTTLED, FailureKind.BOT_CHECK, FailureKind.FORBIDDEN, FailureKind.FORMAT_UNAVAILABLE,
        FailureKind.ENGINE_OUTDATED, FailureKind.PROCESSING, FailureKind.ENGINE_BROKEN, FailureKind.UNKNOWN,
        -> R.string.unreachable
    }

    fun forOutcome(context: Context, outcome: Outcome, variety: Int = Random.nextInt(4)): String = when (outcome) {
        // Mostly "Elmentettem neked.", now and then "Kész is! Jó hallgatást."
        is Outcome.Saved -> context.getString(if (variety == 0) R.string.saved_alt else R.string.saved)
        Outcome.AlreadyHave -> context.getString(R.string.already_have)
        is Outcome.Failed -> context.getString(forFailure(outcome.kind))
        is Outcome.SavedMany -> {
            // The suffix follows the language the sentence is actually in (a German phone still
            // gets the Hungarian strings, so the phone's locale would be the wrong guide).
            val language = context.getString(R.string.sentence_language)
            when {
                outcome.saved == 0 && outcome.failed == 0 -> context.getString(R.string.already_have_all)
                outcome.capped -> context.getString(R.string.saved_many_capped, accusative(outcome.saved, language))
                outcome.failed > 0 -> context.getString(R.string.saved_many_partial, accusative(outcome.saved, language), accusative(outcome.failed, language))
                outcome.alreadyHad > 0 -> context.getString(R.string.saved_many_had, accusative(outcome.saved, language), outcome.alreadyHad)
                else -> context.getString(R.string.saved_many, accusative(outcome.saved, language))
            }
        }
    }

    /** For shares that never reach the engine. */
    fun forLinkCheck(context: Context, check: LinkCheck): String? = when (check) {
        is LinkCheck.Found -> null
        LinkCheck.NotAVideo -> context.getString(R.string.not_a_video)
        LinkCheck.NotYouTube -> context.getString(R.string.not_youtube)
        LinkCheck.Empty -> context.getString(R.string.empty_share)
    }

    /**
     * Hungarian numbers take an object suffix that depends on how they are said:
     * 12-t, 10-et, 3-at, 5-öt, 6-ot. Other languages get the plain number.
     */
    fun accusative(n: Int, language: String): String {
        if (language != "hu") return n.toString()
        val suffix = when {
            n % 10 != 0 -> when (n % 10) {
                1, 4, 7, 9 -> "et"
                2 -> "t"
                3, 8 -> "at"
                5 -> "öt"
                else -> "ot"
            }
            n % 100 != 0 -> when ((n / 10) % 10) {
                1, 4, 5, 7, 9 -> "et"
                else -> "at"
            }
            n % 1000 != 0 -> "at"
            else -> "et"
        }
        return "$n-$suffix"
    }
}
