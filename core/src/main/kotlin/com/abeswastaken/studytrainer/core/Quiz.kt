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

/** Right answers out of questions asked, for one chapter in one run. */
@Serializable
data class ChapterScore(val right: Int, val total: Int)

/** A finished quiz run in the history log. `chapter` is a [ChapterScope] (or [Weak]/drill scope). */
@Serializable
data class QuizRun(
    val date: Long,
    val chapter: String,
    val score: Int,
    val total: Int,
    val wrong: List<Miss> = emptyList(),
    val retake: Boolean = false,
    val exam: Boolean = false,
    /** Per-chapter results, for Stats' weakest chapter. Empty for runs logged before this existed. */
    val byChapter: Map<String, ChapterScore> = emptyMap(),
) {
    val percent: Int get() = if (total == 0) 0 else Math.round(score * 100.0 / total).toInt()
    val label: String get() =
        (if (exam) "Exam · " else "") + (if (retake) "Retake · " else "") + ChapterScope.label(chapter)
}

@Serializable
data class Answer(val key: String, val picked: Int, val correct: Boolean)

/**
 * A quiz in progress. Saved after every tap so closing the app between calls doesn't lose it.
 * Quit (after the confirm) discards it.
 *
 * Pool items are question keys, card items ("card:<chapter|term>", self-graded: picked 1 = got it,
 * 0 = still learning, never scored) or drill items ([Drills], generated from the key).
 * An exam shows no feedback until the end and stops when the time runs out.
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
    val exam: Boolean = false,
    /** Exam start (epoch ms) and time limit in seconds; 0 = no limit. */
    val startedAt: Long = 0,
    val limitSec: Int = 0,
) {
    /** Correct answers to questions (cards aren't scored). */
    val score: Int get() = answers.count { it.correct && !isCard(it.key) }
    val questionCount: Int get() = pool.count { !isCard(it) }
    val cardCount: Int get() = pool.size - questionCount
    val cardsKnown: Int get() = answers.count { isCard(it.key) && it.correct }
    val mixed: Boolean get() = cardCount > 0
    /** Drills are practice and aren't logged. */
    val drill: Boolean get() = chapter == Drills.SCOPE
    val logged: Boolean get() = !reviewing && !drill && questionCount > 0

    /** The option picked for the current item, or null if not answered yet. */
    val pickedNow: Int? get() = answers.getOrNull(idx)?.picked
    val isLast: Boolean get() = idx == pool.size - 1
    val currentIsCard: Boolean get() = pool.getOrNull(idx)?.let(::isCard) == true

    /** Seconds left in a timed exam (never below 0), or null if untimed. */
    fun secondsLeft(now: Long): Long? =
        if (!exam || limitSec <= 0) null else maxOf(0L, limitSec - (now - startedAt) / 1000)

    fun question(course: Course, key: String = pool[idx]): QuizQuestion? =
        if (Drills.isDrill(key)) Drills.make(key) else course.question(key)

    /** Picks an option. In an exam you can change your pick until you move on (no feedback is shown). */
    fun pick(course: Course, option: Int): ActiveQuiz {
        if (finished || currentIsCard) return this
        if (pickedNow != null && !exam) return this
        val q = question(course) ?: return this
        val a = Answer(pool[idx], option, option == q.correct)
        return copy(answers = if (pickedNow != null) answers.take(idx) + a else answers + a)
    }

    /** Got it / Still learning on a card item. */
    fun markCard(known: Boolean): ActiveQuiz {
        if (pickedNow != null || finished || !currentIsCard) return this
        return copy(answers = answers + Answer(pool[idx], if (known) 1 else 0, known))
    }

    fun next(): ActiveQuiz = if (isLast) copy(finished = true) else copy(idx = idx + 1)

    /** Time's up: the exam ends; unanswered questions count as wrong. */
    fun timeUp(): ActiveQuiz = copy(finished = true)

    /** Questions answered wrong, plus (in an exam that ran out of time) the ones never answered. */
    fun wrongKeys(): List<String> {
        val answered = answers.associateBy { it.key }
        return pool.filter { !isCard(it) && answered[it]?.correct != true }
            .filter { answered[it] != null || finished }
    }

    /** Every item still exists in this course (an updated course file can drop some). */
    fun fits(course: Course): Boolean = pool.all {
        when {
            isCard(it) -> course.card(cardKey(it)) != null
            else -> question(course, it) != null
        }
    }

    fun toRun(course: Course, now: Long): QuizRun {
        val answered = answers.associateBy { it.key }
        val byChapter = LinkedHashMap<String, ChapterScore>()
        for (key in pool) {
            if (isCard(key)) continue
            val q = question(course, key) ?: continue
            val ok = answered[key]?.correct == true
            val c = byChapter[q.chapter] ?: ChapterScore(0, 0)
            byChapter[q.chapter] = ChapterScore(c.right + if (ok) 1 else 0, c.total + 1)
        }
        return QuizRun(
            date = now,
            chapter = chapter,
            score = score,
            total = questionCount,
            retake = retake,
            exam = exam,
            byChapter = byChapter,
            wrong = wrongKeys().mapNotNull { key ->
                val q = question(course, key) ?: return@mapNotNull null
                val picked = answered[key]?.picked
                Miss(q.question, q.correctText, picked?.let { q.options.getOrElse(it) { "" } } ?: "(no answer)", q.ref ?: "", q.chapter)
            },
        )
    }

    companion object {
        val LENGTHS = listOf(10, 20, 30, 50, 0) // 0 = all
        /** Exam time per question. */
        const val EXAM_SEC_PER_QUESTION = 60

        const val CARD = "card:"
        fun isCard(key: String) = key.startsWith(CARD)
        fun cardKey(key: String) = key.removePrefix(CARD)

        /**
         * A new run over [chapter] (a [ChapterScope]). With [mixed], about half the items are flashcards,
         * alternating with the questions; [length] counts both. An [exam] is questions only and timed.
         */
        fun start(
            course: Course,
            chapter: String,
            length: Int,
            random: kotlin.random.Random = kotlin.random.Random.Default,
            mixed: Boolean = false,
            exam: Boolean = false,
            now: Long = 0,
        ): ActiveQuiz? {
            val chapters = ChapterScope.chapters(course, chapter).toSet()
            val qs = course.quizzes.filter { it.chapter in chapters }.shuffled(random).map { it.key }
            if (exam || !mixed) {
                val pool = if (length == 0) qs else qs.take(length)
                if (pool.isEmpty()) return null
                return if (exam) ActiveQuiz(chapter, pool, exam = true, startedAt = now, limitSec = pool.size * EXAM_SEC_PER_QUESTION)
                else ActiveQuiz(chapter, pool)
            }
            val cs = course.flashcards.filter { it.chapter in chapters }.shuffled(random).map { CARD + it.key }
            val (q, c) = if (length == 0) qs to cs else {
                // Half each; if one side runs short, the other fills in.
                val wantQ = minOf(qs.size, maxOf((length + 1) / 2, length - cs.size))
                qs.take(wantQ) to cs.take(minOf(cs.size, length - wantQ))
            }
            return interleave(chapter, q, c)
        }

        /** Weak spots: the most-missed questions alternating with the Still learning cards. */
        fun weak(course: Course, runs: List<QuizRun>, statuses: Map<String, CardStatus>): ActiveQuiz? =
            interleave(Weak.SCOPE, Weak.questions(course, runs), Weak.cards(course, statuses).map { CARD + it })

        fun drills(n: Int = Drills.LENGTH, random: kotlin.random.Random = kotlin.random.Random.Default): ActiveQuiz =
            ActiveQuiz(Drills.SCOPE, Drills.keys(n, random))

        private fun interleave(chapter: String, q: List<String>, c: List<String>): ActiveQuiz? {
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
