package com.abeswastaken.studytrainer.core

import kotlinx.serialization.Serializable

/** One missed question in a finished run (artifact's `wrong` entry). */
@Serializable
data class Miss(
    val question: String,
    val correctText: String,
    val pickedText: String,
    val ref: String = "",
    val chapter: String,
)

/** A finished quiz run in the history log. `chapter` is a chapter name or ALL_CHAPTERS. */
@Serializable
data class QuizRun(
    val date: Long,
    val chapter: String,
    val score: Int,
    val total: Int,
    val wrong: List<Miss> = emptyList(),
    val retake: Boolean = false,
) {
    val percent: Int get() = if (total == 0) 0 else Math.round(score * 100.0 / total).toInt()
    val label: String get() = (if (retake) "Retake · " else "") + shortChapter(chapter)
}

@Serializable
data class Answer(val key: String, val picked: Int, val correct: Boolean)

/**
 * A quiz in progress. Saved after every tap so closing the app between calls doesn't lose it.
 * Quit (after the confirm) discards it.
 */
@Serializable
data class ActiveQuiz(
    val chapter: String,
    val pool: List<String>,
    val idx: Int = 0,
    val answers: List<Answer> = emptyList(),
    /** "Review wrong" mini-quizzes are not logged, like the artifact. */
    val reviewing: Boolean = false,
    val retake: Boolean = false,
    val finished: Boolean = false,
) {
    val score: Int get() = answers.count { it.correct }
    /** The option picked for the current question, or null if not answered yet. */
    val pickedNow: Int? get() = answers.getOrNull(idx)?.picked
    val isLast: Boolean get() = idx == pool.size - 1

    fun pick(course: Course, option: Int): ActiveQuiz {
        if (pickedNow != null || finished) return this
        val q = course.question(pool[idx]) ?: return this
        return copy(answers = answers + Answer(q.key, option, option == q.correct))
    }

    fun next(): ActiveQuiz = if (isLast) copy(finished = true) else copy(idx = idx + 1)

    fun wrongKeys(): List<String> = answers.filter { !it.correct }.map { it.key }

    fun toRun(course: Course, now: Long): QuizRun = QuizRun(
        date = now,
        chapter = chapter,
        score = score,
        total = pool.size,
        retake = retake,
        wrong = answers.filter { !it.correct }.mapNotNull { a ->
            val q = course.question(a.key) ?: return@mapNotNull null
            Miss(q.question, q.correctText, q.options.getOrElse(a.picked) { "" }, q.ref ?: "", q.chapter)
        },
    )

    companion object {
        val LENGTHS = listOf(10, 20, 30, 50, 0) // 0 = all

        fun start(course: Course, chapter: String, length: Int, random: kotlin.random.Random = kotlin.random.Random.Default): ActiveQuiz? {
            val all = (if (chapter == ALL_CHAPTERS) course.quizzes else course.quizzes.filter { it.chapter == chapter })
                .shuffled(random)
            val pool = if (length == 0) all else all.take(length)
            if (pool.isEmpty()) return null
            return ActiveQuiz(chapter, pool.map { it.key })
        }

        /** Retake a past run's misses. Matches on question text and correct answer, like the artifact. */
        fun retake(course: Course, run: QuizRun): ActiveQuiz? {
            val pool = run.wrong.mapNotNull { w ->
                course.quizzes.firstOrNull { it.question == w.question && it.correctText == w.correctText }?.key
            }
            if (pool.isEmpty()) return null
            return ActiveQuiz(run.chapter, pool, retake = true)
        }
    }
}

object QuizText {
    fun resultMessage(pct: Int): String = when {
        pct == 100 -> "Perfect — journeyman material."
        pct >= 85 -> "Strong. Review the misses and move on."
        pct >= 70 -> "Solid. A pass by most standards."
        pct >= 50 -> "Keep grinding — hit the flashcards for the weak chapters."
        else -> "Back to the book. Flashcards first."
    }

    fun letter(i: Int): String = ('A' + i).toString()
}

/** History keeps the last 100 runs per course. */
const val HISTORY_CAP = 100
