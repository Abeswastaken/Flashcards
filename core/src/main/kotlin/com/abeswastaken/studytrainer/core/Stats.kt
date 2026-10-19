package com.abeswastaken.studytrainer.core

data class ChapterStat(val chapter: String, val known: Int, val learning: Int, val total: Int)

data class CourseStats(val known: Int, val learning: Int, val quizAvg: Int?, val chapters: List<ChapterStat>)

object Stats {
    fun of(course: Course, statuses: Map<String, CardStatus>, runs: List<QuizRun>): CourseStats {
        val chapterStats = course.chapters.mapNotNull { chap ->
            val cards = course.flashcards.filter { it.chapter == chap }
            if (cards.isEmpty()) return@mapNotNull null
            ChapterStat(
                chap,
                cards.count { statuses[it.key] == CardStatus.KNOWN },
                cards.count { statuses[it.key] == CardStatus.LEARNING },
                cards.size,
            )
        }
        val avg = if (runs.isEmpty()) null else Math.round(runs.map { it.score * 100.0 / it.total.coerceAtLeast(1) }.average()).toInt()
        // Count only cards that exist in this course, so stale keys from an older course version don't inflate totals.
        return CourseStats(chapterStats.sumOf { it.known }, chapterStats.sumOf { it.learning }, avg, chapterStats)
    }
}
