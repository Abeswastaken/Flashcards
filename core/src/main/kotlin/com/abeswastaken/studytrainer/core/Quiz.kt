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
    val label: String get() = (if (retake) "Retake · " else "") + ChapterScope.label(chapter)
}

@Serializable
data class Answer(val key: String, val picked: Int, val correct: Boolean)

/**
 * A quiz in progress. Saved after every tap so closing the app between calls doesn't lose it.
 * Quit (after the confirm) discards it.
 *
 * A mixed run interleaves flashcards with the questions. Card items have keys starting with
 * [CARD]; they're self-graded (picked 1 = got it, 0 = still learning) and don't count toward the score.
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
    /** Correct answers to questions (cards aren't scored). */
    val score: Int get() = answers.count { it.correct && !isCard(it.key) }
    val questionCount: Int get() = pool.count { !isCard(it) }
    val cardCount: Int get() = pool.size - questionCount
    val cardsKnown: Int get() = answers.count { isCard(it.key) && it.correct }
    val mixed: Boolean get() = cardCount > 0

    /** The option picked for the current item, or null if not answered yet. */
    val pickedNow: Int? get() = answers.getOrNull(idx)?.picked
    val isLast: Boolean get() = idx == pool.size - 1
    val currentIsCard: Boolean get() = pool.getOrNull(idx)?.let(::isCard) == true

    fun pick(course: Course, option: Int): ActiveQuiz {
        if (pickedNow != null || finished || currentIsCard) return this
        val q = course.question(pool[idx]) ?: return this
        return copy(answers = answers + Answer(q.key, option, option == q.correct))
    }

    /** Got it / Still learning on a card item. */
    fun markCard(known: Boolean): ActiveQuiz {
        if (pickedNow != null || finished || !currentIsCard) return this
        return copy(answers = answers + Answer(pool[idx], if (known) 1 else 0, known))
    }

    fun next(): ActiveQuiz = if (isLast) copy(finished = true) else copy(idx = idx + 1)

    fun wrongKeys(): List<String> = answers.filter { !it.correct && !isCard(it.key) }.map { it.key }

    /** Every item still exists in this course (an updated course file can drop some). */
    fun fits(course: Course): Boolean =
        pool.all { if (isCard(it)) course.card(cardKey(it)) != null else course.question(it) != null }

    fun toRun(course: Course, now: Long): QuizRun = QuizRun(
        date = now,
        chapter = chapter,
        score = score,
        total = questionCount,
        retake = retake,
        wrong = answers.filter { !it.correct && !isCard(it.key) }.mapNotNull { a ->
            val q = course.question(a.key) ?: return@mapNotNull null
            Miss(q.question, q.correctText, q.options.getOrElse(a.picked) { "" }, q.ref ?: "", q.chapter)
        },
    )

    companion object {
        val LENGTHS = listOf(10, 20, 30, 50, 0) // 0 = all

        const val CARD = "card:"
        fun isCard(key: String) = key.startsWith(CARD)
        fun cardKey(key: String) = key.removePrefix(CARD)

        /**
         * A new run over [chapter] (a [ChapterScope]). With [mixed], about half the items are flashcards,
         * alternating with the questions; [length] counts both.
         */
        fun start(
            course: Course,
            chapter: String,
            length: Int,
            random: kotlin.random.Random = kotlin.random.Random.Default,
            mixed: Boolean = false,
        ): ActiveQuiz? {
            val chapters = ChapterScope.chapters(course, chapter).toSet()
            val qs = course.quizzes.filter { it.chapter in chapters }.shuffled(random).map { it.key }
            if (!mixed) {
                val pool = if (length == 0) qs else qs.take(length)
                return if (pool.isEmpty()) null else ActiveQuiz(chapter, pool)
            }
            val cs = course.flashcards.filter { it.chapter in chapters }.shuffled(random).map { CARD + it.key }
            val (q, c) = if (length == 0) qs to cs else {
                // Half each; if one side runs short, the other fills in.
                val wantQ = minOf(qs.size, maxOf((length + 1) / 2, length - cs.size))
                qs.take(wantQ) to cs.take(minOf(cs.size, length - wantQ))
            }
            val pool = buildList {
                for (i in 0 until maxOf(q.size, c.size)) {
                    q.getOrNull(i)?.let(::add)
                    c.getOrNull(i)?.let(::add)
                }
            }
            return if (pool.isEmpty()) null else ActiveQuiz(chapter, pool)
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
