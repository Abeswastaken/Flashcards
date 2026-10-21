package com.abeswastaken.studytrainer.core

import kotlinx.serialization.Serializable

enum class CardStatus(val stored: String) {
    KNOWN("known"), LEARNING("learning");

    companion object {
        fun of(s: String?): CardStatus? = entries.firstOrNull { it.stored == s }
    }
}

enum class CardFilter(val stored: String, val label: String) {
    ALL("all", "All cards"),
    LEARNING("learning", "⟳ Still learning"),
    UNSEEN("unseen", "Not reviewed"),
    KNOWN("known", "✓ Known");

    companion object {
        fun of(s: String?): CardFilter = entries.firstOrNull { it.stored == s } ?: ALL
    }
}

/** Where the flashcard tab was left. Saved on every change so the app reopens on the same card. */
@Serializable
data class Position(
    val chapter: String = ALL_CHAPTERS,
    val filter: String = "all",
    val idx: Int = 0,
    val random: Boolean = false,
    val seed: Long = 1,
)

object Deck {
    /**
     * The artifact's seeded shuffle, bit for bit: a 32-bit LCG driving Fisher–Yates.
     * Same seed, same order, on every launch.
     */
    fun <T> seededShuffle(items: List<T>, seed: Long): List<T> {
        var s = seed and 0xFFFFFFFFL
        val out = items.toMutableList()
        for (i in out.size - 1 downTo 1) {
            s = (s * 1664525L + 1013904223L) and 0xFFFFFFFFL
            val j = (s % (i + 1)).toInt()
            val t = out[i]; out[i] = out[j]; out[j] = t
        }
        return out
    }

    fun build(
        course: Course,
        chapter: String,
        filter: CardFilter,
        statuses: Map<String, CardStatus>,
        random: Boolean,
        seed: Long,
    ): List<Flashcard> {
        val chapters = ChapterScope.chapters(course, chapter).toSet()
        var all = course.flashcards.filter { it.chapter in chapters }
        if (filter != CardFilter.ALL) {
            all = all.filter {
                val st = statuses[it.key]
                when (filter) {
                    CardFilter.LEARNING -> st == CardStatus.LEARNING
                    CardFilter.KNOWN -> st == CardStatus.KNOWN
                    CardFilter.UNSEEN -> st == null
                    CardFilter.ALL -> true
                }
            }
        }
        return if (random) seededShuffle(all, seed) else all
    }

    /** Index to show after marking a card. In a filtered view the card may drop out and the next one slides in. */
    fun indexAfterMark(oldIdx: Int, oldSize: Int, newSize: Int, filtered: Boolean): Int = when {
        newSize == 0 -> 0
        filtered -> oldIdx.coerceAtMost(newSize - 1)
        else -> (oldIdx + 1) % oldSize.coerceAtLeast(1)
    }

    fun newSeed(): Long = (System.currentTimeMillis() xor (Math.random() * 1e9).toLong()) and 0xFFFFFFFFL

    fun emptyMessage(filter: CardFilter): String = when (filter) {
        CardFilter.LEARNING -> "No cards in \"Still learning\" for this chapter."
        CardFilter.KNOWN -> "No \"Known\" cards here yet."
        CardFilter.UNSEEN -> "All cards in this chapter have been reviewed."
        CardFilter.ALL -> "No cards."
    }
}
