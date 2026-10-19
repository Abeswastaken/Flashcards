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
    fun rejectsAFileThatIsNotACourse() { Course.parse("""{"course":{"id":"","title":"x"}}""") }

    @Test fun cardPageIsOptional() {
        val c = Course.parse("""{"course":{"id":"x","title":"X","version":2},
            "flashcards":[{"chapter":"Chapter 1: A","term":"t","definition":"d","ref":"p. 12"},
                          {"chapter":"Chapter 1: A","term":"u","definition":"e"}]}""")
        assertEquals("p. 12", c.flashcards[0].ref)
        assertNull(c.flashcards[1].ref)
        assertEquals(2, c.meta.version)
    }

    @Test fun shortChapterLabel() {
        assertEquals("Ch. 3: Low-Pressure Boilers", shortChapter("Chapter 3: Low-Pressure Boilers"))
        assertEquals("All chapters", shortChapter(ALL_CHAPTERS))
    }
}
