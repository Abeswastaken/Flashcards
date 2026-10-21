package com.abeswastaken.studytrainer.core

import kotlinx.serialization.Serializable

/**
 * Spaced repetition for one card (a light SM-2). Days are epoch days.
 * Every Got it / Still learning anywhere in the app grades the card.
 */
@Serializable
data class Review(val ease: Double = 2.5, val interval: Int = 0, val due: Long = 0, val reps: Int = 0)

object Srs {
    const val MIN_EASE = 1.3
    const val MAX_EASE = 3.0

    /**
     * Got it: the gap grows (1 day, then 3, then gap × ease). Still learning: back today, and the card gets harder.
     * Grading again the same day a card isn't due doesn't stretch the gap further.
     */
    fun grade(old: Review?, known: Boolean, today: Long): Review {
        val r = old ?: Review()
        if (!known) return Review(ease = maxOf(MIN_EASE, r.ease - 0.2), interval = 0, due = today, reps = 0)
        if (old != null && old.reps > 0 && old.due > today) return old
        val reps = r.reps + 1
        val interval = when (reps) {
            1 -> 1
            2 -> 3
            else -> maxOf(r.interval + 1, Math.round(r.interval * r.ease).toInt())
        }
        return Review(ease = minOf(MAX_EASE, r.ease + 0.1), interval = interval, due = today + interval, reps = reps)
    }

    /** Keys of cards in this course that are due today or overdue. */
    fun due(course: Course, reviews: Map<String, Review>, today: Long): Set<String> =
        course.flashcards.filter { c -> reviews[c.key]?.let { it.due <= today } == true }.map { it.key }.toSet()
}
