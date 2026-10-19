package com.abeswastaken.studytrainer.core

/** Finds the flashcard that explains a quiz answer. Same three steps as the artifact's findExplanation. */
object Explain {
    fun find(course: Course, q: QuizQuestion): Flashcard? {
        val correctText = q.options.getOrNull(q.correct) ?: return null
        if (correctText.isBlank()) return null
        // 1. Direct term lookup, parentheticals removed.
        val key = correctText.lowercase().trim().replace(Regex("\\s*\\([^)]*\\)\\s*"), "").trim()
        course.termIndex[key]?.let { return it }
        // 2. Longest leading run of words that is a term.
        val words = correctText.lowercase().split(Regex("[\\s,.()]+")).filter { it.isNotEmpty() }
        for (n in words.size downTo 1) {
            course.termIndex[words.take(n).joinToString(" ")]?.let { return it }
        }
        // 3. Any term from this chapter mentioned in the question or answer.
        val haystack = (q.question + " " + correctText).lowercase()
        return course.flashcards.firstOrNull { it.chapter == q.chapter && haystack.contains(it.term.lowercase()) }
    }
}
