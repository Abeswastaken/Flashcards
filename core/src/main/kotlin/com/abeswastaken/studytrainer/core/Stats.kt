package com.abeswastaken.studytrainer.core

data class ChapterStat(val chapter: String, val known: Int, val learning: Int, val total: Int)

/** Quiz accuracy for one chapter across History (runs logged with per-chapter results). */
data class ChapterAccuracy(val chapter: String, val right: Int, val total: Int) {
    val percent: Int get() = if (total == 0) 0 else Math.round(right * 100.0 / total).toInt()
}

data class CourseStats(
    val known: Int,
    val learning: Int,
    val quizAvg: Int?,
    val chapters: List<ChapterStat>,
    val accuracy: List<ChapterAccuracy> = emptyList(),
) {
    /** Lowest-scoring chapter with at least [Stats.MIN_ANSWERED] questions answered, or null. */
    val weakest: ChapterAccuracy? get() =
        accuracy.filter { it.total >= Stats.MIN_ANSWERED }.minWithOrNull(compareBy({ it.percent }, { -it.total }))
}

object Stats {
    const val MIN_ANSWERED = 5

    fun accuracy(course: Course, runs: List<QuizRun>): List<ChapterAccuracy> {
        val sum = HashMap<String, ChapterScore>()
        for (r in runs) for ((ch, sc) in r.byChapter) {
            val c = sum[ch] ?: ChapterScore(0, 0)
            sum[ch] = ChapterScore(c.right + sc.right, c.total + sc.total)
        }
        return course.chapters.mapNotNull { ch -> sum[ch]?.let { ChapterAccuracy(ch, it.right, it.total) } }
    }

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
        return CourseStats(chapterStats.sumOf { it.known }, chapterStats.sumOf { it.learning }, avg, chapterStats, accuracy(course, runs))
    }
}
