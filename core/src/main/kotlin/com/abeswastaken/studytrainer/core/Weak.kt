package com.abeswastaken.studytrainer.core

/** Weak spots: the questions missed most across History, plus cards marked Still learning. */
object Weak {
    const val SCOPE = "__weak__"
    const val QUESTIONS = 20
    const val CARDS = 20

    /** Question keys ordered by how often they were missed (most first), then by how recently. */
    fun questions(course: Course, runs: List<QuizRun>, limit: Int = QUESTIONS): List<String> {
        val count = LinkedHashMap<String, Int>()
        for (run in runs.sortedByDescending { it.date }) {
            for (m in run.wrong) {
                val q = course.quizzes.firstOrNull { it.question == m.question && it.correctText == m.correctText } ?: continue
                count[q.key] = (count[q.key] ?: 0) + 1
            }
        }
        return count.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    fun cards(course: Course, statuses: Map<String, CardStatus>, limit: Int = CARDS): List<String> =
        course.flashcards.filter { statuses[it.key] == CardStatus.LEARNING }.take(limit).map { it.key }
}
