package io.github.itemujinwork.dalma.core

import java.text.Collator
import java.util.Locale

/** The lists on the surface: what a chip under a row does to a list, what a typed name becomes, and the chips' order. */
object Lists {
    /** [entry] leaves the list if it is in it (by file name), else it goes to the end: a list's order is the order of adding. */
    fun toggled(entries: List<M3u.Entry>, entry: M3u.Entry): List<M3u.Entry> =
        if (contains(entries, entry.fileName)) entries.filterNot { it.fileName == entry.fileName } else entries + entry

    fun contains(entries: List<M3u.Entry>, fileName: String): Boolean = entries.any { it.fileName == fileName }

    /** The file of a list named as typed, or `null` when nothing was typed: no list is born of nothing, and on a rename it means "delete". */
    fun fileNameFor(typed: String): String? = typed.trim().takeIf { it.isNotEmpty() }?.let { M3u.fileNameFor(it) }

    /** The chips' order: the alphabet by file name, the way it is read here (á with a, case aside). */
    fun <T> sorted(lists: List<T>, fileName: (T) -> String): List<T> = lists.sortedWith { a, b -> collator.compare(fileName(a), fileName(b)) }

    private val collator: Collator = Collator.getInstance(Locale("hu")).apply { strength = Collator.SECONDARY }
}
