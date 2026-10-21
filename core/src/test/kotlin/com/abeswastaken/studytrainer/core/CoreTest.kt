package com.abeswastaken.studytrainer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class CoreTest {
    private val course: Course by lazy {
        Course.parse(File(System.getProperty("courseDir"), "y3-c1-steam-systems.json").readText())
    }

    @Test fun parsesY3C1() {
        assertEquals("y3-c1-steam-systems", course.id)
        assertEquals(461, course.flashcards.size) // 462 in the file, one exact duplicate
        assertEquals(243, course.quizzes.size)
        assertEquals(12, course.chapters.size)
        assertEquals("Chapter 1: Introduction to Steam Systems", course.chapters.first())
    }

    @Test fun shuffleMatchesTheArtifact() {
        // Expected values come from running the artifact's JavaScript seededShuffle.
        assertEquals(listOf(9, 5, 4, 2, 1, 0, 3, 6, 7, 8), Deck.seededShuffle((0..9).toList(), 12345))
        assertEquals(listOf(9, 6, 3, 0, 2, 7, 5, 4, 1, 8), Deck.seededShuffle((0..9).toList(), 4294967295))
        val terms = Deck.seededShuffle(course.flashcards, 987654321).take(3).map { it.term }
        assertEquals(listOf("upfeed piping", "dynamic pressures", "stop valve"), terms)
    }

    @Test fun shuffleIsStableForASeed() {
        val a = Deck.build(course, ALL_CHAPTERS, CardFilter.ALL, emptyMap(), true, 42)
        val b = Deck.build(course, ALL_CHAPTERS, CardFilter.ALL, emptyMap(), true, 42)
        assertEquals(a, b)
    }

    @Test fun filtersByStatusAndChapter() {
        val ch1 = course.chapters[0]
        val cards = course.flashcards.filter { it.chapter == ch1 }
        val st = mapOf(cards[0].key to CardStatus.KNOWN, cards[1].key to CardStatus.LEARNING)
        assertEquals(cards.size, Deck.build(course, ch1, CardFilter.ALL, st, false, 1).size)
        assertEquals(listOf(cards[0]), Deck.build(course, ch1, CardFilter.KNOWN, st, false, 1))
        assertEquals(listOf(cards[1]), Deck.build(course, ch1, CardFilter.LEARNING, st, false, 1))
        assertEquals(cards.size - 2, Deck.build(course, ch1, CardFilter.UNSEEN, st, false, 1).size)
    }

    @Test fun markingAdvancesLikeTheArtifact() {
        assertEquals(3, Deck.indexAfterMark(2, 10, 10, filtered = false))
        assertEquals(0, Deck.indexAfterMark(9, 10, 10, filtered = false))
        assertEquals(2, Deck.indexAfterMark(2, 10, 9, filtered = true)) // next card slides into the same slot
        assertEquals(8, Deck.indexAfterMark(9, 10, 9, filtered = true))
        assertEquals(0, Deck.indexAfterMark(0, 1, 0, filtered = true))
    }

    @Test fun explanationsMatchTheArtifact() {
        val expected = javaClass.getResource("/explain-expected.txt")!!.readText().trimEnd('\n').split("\n")
        assertEquals(course.quizzes.size, expected.size)
        course.quizzes.forEachIndexed { i, q ->
            assertEquals("question $i", expected[i], Explain.find(course, q)?.key ?: "")
        }
    }

    @Test fun quizFlowLogsMisses() {
        var quiz = ActiveQuiz.start(course, course.chapters[0], 10, Random(1))!!
        assertEquals(10, quiz.pool.size)
        repeat(10) { i ->
            val q = course.question(quiz.pool[quiz.idx])!!
            val pick = if (i % 2 == 0) q.correct else (q.correct + 1) % q.options.size
            quiz = quiz.pick(course, pick)
            assertNotNull(quiz.pickedNow)
            quiz = quiz.pick(course, 0) // a second tap changes nothing
            quiz = quiz.next()
        }
        assertTrue(quiz.finished)
        assertEquals(5, quiz.score)
        val run = quiz.toRun(course, 1000)
        assertEquals(5, run.wrong.size)
        assertEquals(50, run.percent)
        val retake = ActiveQuiz.retake(course, run)!!
        assertEquals(5, retake.pool.size)
        assertTrue(retake.retake)
        assertEquals("Retake · Ch. 1: Introduction to Steam Systems", retake.toRun(course, 0).label)
    }

    @Test fun allMeansEveryQuestion() {
        assertEquals(243, ActiveQuiz.start(course, ALL_CHAPTERS, 0)!!.pool.size)
        assertEquals(20, ActiveQuiz.start(course, ALL_CHAPTERS, 20)!!.pool.size)
    }

    @Test fun statsCountPerChapter() {
        val c = course.flashcards.take(3)
        val s = Stats.of(course, mapOf(c[0].key to CardStatus.KNOWN, c[1].key to CardStatus.KNOWN, c[2].key to CardStatus.LEARNING),
            listOf(QuizRun(0, ALL_CHAPTERS, 8, 10), QuizRun(0, ALL_CHAPTERS, 5, 10)))
        assertEquals(2, s.known); assertEquals(1, s.learning); assertEquals(65, s.quizAvg)
        assertEquals(12, s.chapters.size)
        assertNull(Stats.of(course, emptyMap(), emptyList()).quizAvg)
    }

    @Test fun exportRoundTrips() {
        val p = ProgressExport(exportedAt = 5, courses = mapOf(course.id to CourseProgress(
            cards = mapOf("a|b" to "known"), position = Position(ALL_CHAPTERS, "learning", 4, true, 99),
            runs = listOf(QuizRun(1, ALL_CHAPTERS, 1, 2, listOf(Miss("q", "c", "p", "p. 1", "ch")))),
        )))
        assertEquals(p, ProgressExport.decode(p.encode()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aCourseFileIsNotAProgressBackup() {
        ProgressExport.decode(File(System.getProperty("courseDir"), "y3-c1-steam-systems.json").readText())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAFileThatIsNotACourse() { Course.parse("""{"course":{"id":"","title":"x"}}""") }

    @Test fun cardPageIsOptional() {
        val c = Course.parse("""{"course":{"id":"x","title":"X","version":2},
            "flashcards":[{"chapter":"Chapter 1: A","term":"t","definition":"d","ref":"p. 12"},
                          {"chapter":"Chapter 1: A","term":"u","definition":"e"}]}""")
        assertEquals("p. 12", c.flashcards[0].ref)
        assertNull(c.flashcards[1].ref)
        assertEquals(2, c.meta.version)
    }

    @Test fun chapterRangeCoversEveryChapterBetween() {
        val ch = course.chapters
        val r = ChapterScope.range(ch[0], ch[3])
        assertEquals(ch.subList(0, 4), ChapterScope.chapters(course, r))
        assertEquals(ch.subList(0, 4), ChapterScope.chapters(course, ChapterScope.range(ch[3], ch[0]))) // reversed ends
        assertEquals("Ch. 1–4", ChapterScope.label(r))
        assertEquals(ch[2], ChapterScope.range(ch[2], ch[2])) // a one-chapter range is just that chapter
        assertTrue(ChapterScope.isValid(course, r))
        assertTrue(!ChapterScope.isValid(course, ChapterScope.range(ch[0], "Chapter 99: Gone")))
        val expected = course.flashcards.count { it.chapter in ch.subList(0, 4) }
        assertEquals(expected, Deck.build(course, r, CardFilter.ALL, emptyMap(), false, 1).size)
        val quiz = ActiveQuiz.start(course, r, 0, Random(1))!!
        assertTrue(quiz.pool.all { course.question(it)!!.chapter in ch.subList(0, 4) })
        assertEquals(course.quizzes.count { it.chapter in ch.subList(0, 4) }, quiz.pool.size)
    }

    @Test fun mixedRunAlternatesCardsAndQuestions() {
        val q = ActiveQuiz.start(course, ALL_CHAPTERS, 20, Random(3), mixed = true)!!
        assertEquals(20, q.pool.size)
        assertEquals(10, q.cardCount)
        assertEquals(10, q.questionCount)
        assertTrue(!ActiveQuiz.isCard(q.pool[0]) && ActiveQuiz.isCard(q.pool[1]))
        assertTrue(q.fits(course))

        // Question right, card known, question wrong, card still learning...
        var run = q.pick(course, course.question(q.pool[0])!!.correct).next()
        assertEquals(run, run.pick(course, 0)) // can't answer a card item like a question
        run = run.markCard(true).next()
        run = run.pick(course, (course.question(run.pool[2])!!.correct + 1) % 4).next()
        run = run.markCard(false).next()
        assertEquals(1, run.score)
        assertEquals(1, run.cardsKnown)
        assertEquals(listOf(q.pool[2]), run.wrongKeys()) // only questions go to review
        val logged = run.toRun(course, 0)
        assertEquals(1, logged.score)
        assertEquals(10, logged.total) // history counts the questions
        assertEquals(1, logged.wrong.size)
    }

    @Test fun mixedRunFillsFromTheOtherSide() {
        val ch = course.chapters[0]
        val cards = course.flashcards.count { it.chapter == ch }
        val qs = course.quizzes.count { it.chapter == ch }
        val all = ActiveQuiz.start(course, ch, 0, Random(1), mixed = true)!!
        assertEquals(cards + qs, all.pool.size)
        val big = ActiveQuiz.start(course, ch, cards + qs + 50, Random(1), mixed = true)!!
        assertEquals(cards + qs, big.pool.size)
    }

    @Test fun shortChapterLabel() {
        assertEquals("Ch. 3: Low-Pressure Boilers", shortChapter("Chapter 3: Low-Pressure Boilers"))
        assertEquals("All chapters", shortChapter(ALL_CHAPTERS))
    }

    @Test fun spacedRepetitionGrowsTheGap() {
        var r = Srs.grade(null, true, 100)
        assertEquals(1, r.interval); assertEquals(101L, r.due)
        assertEquals(r, Srs.grade(r, true, 100)) // same day, not due: no stretch
        r = Srs.grade(r, true, 101)
        assertEquals(3, r.interval); assertEquals(104L, r.due)
        r = Srs.grade(r, true, 104)
        assertTrue(r.interval >= 7)
        val miss = Srs.grade(r, false, 110)
        assertEquals(0, miss.interval); assertEquals(110L, miss.due); assertEquals(0, miss.reps)
        assertTrue(miss.ease < r.ease)
        var floor = miss
        repeat(20) { floor = Srs.grade(floor, false, 110) }
        assertEquals(Srs.MIN_EASE, floor.ease, 1e-9)

        val c = course.flashcards
        val reviews = mapOf(c[0].key to Review(due = 50, reps = 1), c[1].key to Review(due = 51, reps = 1))
        assertEquals(setOf(c[0].key), Srs.due(course, reviews, 50))
        assertEquals(listOf(c[0]), Deck.build(course, ALL_CHAPTERS, CardFilter.DUE, emptyMap(), false, 1, Srs.due(course, reviews, 50)))
    }

    @Test fun weakSpotsPutTheMostMissedFirst() {
        val a = course.quizzes[0]; val b = course.quizzes[1]
        fun miss(q: QuizQuestion) = Miss(q.question, q.correctText, "x", chapter = q.chapter)
        val runs = listOf(
            QuizRun(1, ALL_CHAPTERS, 0, 2, listOf(miss(a), miss(b))),
            QuizRun(2, ALL_CHAPTERS, 0, 1, listOf(miss(b))),
        )
        assertEquals(listOf(b.key, a.key), Weak.questions(course, runs))
        val st = mapOf(course.flashcards[5].key to CardStatus.LEARNING, course.flashcards[6].key to CardStatus.KNOWN)
        val w = ActiveQuiz.weak(course, runs, st)!!
        assertEquals(listOf(b.key, ActiveQuiz.CARD + course.flashcards[5].key, a.key), w.pool)
        assertEquals("Weak spots", w.toRun(course, 0).label)
        assertNull(ActiveQuiz.weak(course, emptyList(), emptyMap()))
    }

    @Test fun drillAnswersAreRight() {
        val keys = Drills.keys(500, Random(9))
        for (k in keys) {
            val q = Drills.make(k)!!
            assertEquals(k, 4, q.options.size)
            assertEquals(k, 4, q.options.toSet().size)
            assertEquals(q, Drills.make(k)) // rebuilt the same from the key
            val nums = Regex("(\\w+) = (\\d+)").findAll(q.question).associate { it.groupValues[1] to it.groupValues[2].toDouble() }
            val ans = q.correctText.substringBefore(" ").toDouble()
            val expected = when (k.split(":")[1]) {
                "ohmE" -> nums["I"]!! * nums["R"]!!
                "ohmI" -> nums["E"]!! / nums["R"]!!
                "ohmR" -> nums["E"]!! / nums["I"]!!
                "powEI" -> nums["E"]!! * nums["I"]!!
                "powI" -> nums["P"]!! / nums["E"]!!
                "powIR" -> nums["I"]!! * nums["I"]!! * nums["R"]!!
                "powER" -> nums["E"]!! * nums["E"]!! / nums["R"]!!
                "series" -> nums.filterKeys { it.startsWith("R") }.values.sum()
                "parallel" -> 1.0 / nums.filterKeys { it.startsWith("R") }.values.sumOf { 1.0 / it }
                else -> error(k)
            }
            assertEquals(q.question, expected, ans, 0.01)
        }
        val d = ActiveQuiz.drills(10, Random(1))
        assertTrue(d.fits(course))
        assertTrue(d.drill && !d.logged)
    }

    @Test fun examCountsUnansweredAndScoresByChapter() {
        val ex = ActiveQuiz.start(course, ALL_CHAPTERS, 10, Random(4), mixed = true, exam = true, now = 1_000)!!
        assertEquals(0, ex.cardCount) // exams are questions only
        assertEquals(600, ex.limitSec)
        assertEquals(600L, ex.secondsLeft(1_000))
        assertEquals(0L, ex.secondsLeft(10_000_000))
        val q0 = ex.question(course)!!
        val changed = ex.pick(course, (q0.correct + 1) % q0.options.size).pick(course, q0.correct)
        assertEquals(1, changed.answers.size) // an exam pick can be changed before moving on
        val done = changed.next().timeUp()
        assertEquals(9, done.wrongKeys().size)
        val run = done.toRun(course, 5)
        assertTrue(run.exam)
        assertEquals(1, run.score); assertEquals(10, run.total)
        assertEquals(9, run.wrong.size)
        assertEquals("(no answer)", run.wrong.first().pickedText)
        assertEquals(10, run.byChapter.values.sumOf { it.total })
        assertEquals(1, run.byChapter[q0.chapter]!!.right)
        assertTrue(run.label.startsWith("Exam · "))

        val stats = Stats.of(course, emptyMap(), listOf(run))
        assertEquals(stats.accuracy.sumOf { it.total }, 10)
        val many = QuizRun(0, ALL_CHAPTERS, 1, 6, byChapter = mapOf(course.chapters[2] to ChapterScore(1, 6), course.chapters[3] to ChapterScore(5, 5)))
        assertEquals(course.chapters[2], Stats.of(course, emptyMap(), listOf(many)).weakest!!.chapter)
    }

    @Test fun oldBackupsAndRunsStillLoad() {
        val old = """{"app":"study-trainer","format":1,"courses":{"x":{"cards":{"a|b":"known"},"runs":[{"date":1,"chapter":"__all__","score":1,"total":2}]}}}"""
        val p = ProgressExport.decode(old)
        assertTrue(p.courses["x"]!!.reviews.isEmpty())
        assertTrue(!p.courses["x"]!!.runs[0].exam)
        val round = ProgressExport.decode(ProgressExport(courses = mapOf("x" to CourseProgress(reviews = mapOf("a|b" to Review(due = 3)), notes = mapOf("a|b" to "hi")))).encode())
        assertEquals("hi", round.courses["x"]!!.notes["a|b"])
        assertEquals(3L, round.courses["x"]!!.reviews["a|b"]!!.due)
    }
}
