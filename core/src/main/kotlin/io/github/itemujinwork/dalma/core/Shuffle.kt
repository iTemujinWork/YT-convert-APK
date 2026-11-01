package io.github.itemujinwork.dalma.core

import kotlin.random.Random

/** A long press shuffles the list; the song that plays stays at the front. */
object Shuffle {
    fun order(ids: List<Long>, first: Long?, random: Random = Random.Default): List<Long> {
        val rest = ids.filterNot { it == first }.shuffled(random)
        return if (first != null && first in ids) listOf(first) + rest else rest
    }

    /** [order] applied to a list that may have gained or lost songs since: newcomers go to the end. */
    fun <T> apply(items: List<T>, order: List<Long>, id: (T) -> Long): List<T> {
        val byId = items.associateBy(id)
        val placed = order.mapNotNull { byId[it] }
        val known = order.toHashSet()
        return placed + items.filterNot { id(it) in known }
    }
}
