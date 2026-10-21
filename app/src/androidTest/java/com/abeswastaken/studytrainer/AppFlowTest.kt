package com.abeswastaken.studytrainer

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abeswastaken.studytrainer.data.AppDatabase
import com.abeswastaken.studytrainer.data.Prefs
import com.abeswastaken.studytrainer.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Walks the real app: flashcards, a quiz, then closes and reopens it to check nothing was lost. */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val dao get() = AppDatabase.get(ctx).dao()

    @Before fun fresh() {
        runBlocking { AppDatabase.get(ctx).clearAllTables() }
        Prefs(ctx).clear()
        java.io.File(ctx.filesDir, "courses").deleteRecursively()
    }

    // Taps through semantics so a button below the fold still works.
    private fun SemanticsNodeInteraction.tap() = performSemanticsAction(SemanticsActions.OnClick)

    // Unmerged tree: the card's text sits inside its clickable, which merges child tags away.
    private fun waitForTag(tag: String) = compose.waitUntil(15_000) {
        runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
    }

    // Like waitUntil, but a timeout says what was on screen.
    private fun waitOrDump(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(15_000, condition)
        } catch (e: Throwable) {
            val texts = runCatching {
                compose.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
                    .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") }
            }.getOrElse { listOf("(no tree: ${it.message})") }
            throw AssertionError("Timed out waiting for $what. Screen: $texts", e)
        }
    }

    @Test fun studyQuizAndResumeAfterClose() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForTag("fc-counter")
            compose.onNodeWithTag("fc-counter").assertTextEquals("1 / 461")
            compose.onNodeWithTag("fc-def", useUnmergedTree = true).assertExists()

            compose.onNodeWithTag("card").tap()
            waitForTag("fc-term")

            compose.onNodeWithText("✓ Got it").tap()
            compose.onNodeWithTag("fc-counter").assertTextEquals("2 / 461")
            compose.onNodeWithTag("fc-known").assertTextContains("1 known", substring = true)
            compose.onNodeWithText("✗ Still learning").tap()
            compose.onNodeWithTag("fc-counter").assertTextEquals("3 / 461")

            compose.onNodeWithTag("tab-QUIZ").tap()
            compose.onNodeWithText("Start quiz").tap()
            compose.onNodeWithTag("q-counter").assertTextEquals("Question 1 / 20")
            compose.onNodeWithTag("opt0").tap()
            waitForTag("feedback")
            compose.onNodeWithTag("q-next").tap()
            compose.onNodeWithTag("q-counter").assertTextEquals("Question 2 / 20")

            compose.onNodeWithTag("tab-STATS").tap()
            waitForTag("st-known")
        }

        // Wait for the saves to land, then reopen like after an app kill.
        compose.waitUntil(10_000) { runBlocking { dao.statuses("y3-c1-steam-systems").size == 2 && dao.activeQuiz("y3-c1-steam-systems") != null } }
        assertEquals(2, runBlocking { dao.position("y3-c1-steam-systems")!!.idx })

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForTag("st-known")
            compose.onNodeWithTag("st-known").assertTextEquals("1")
            compose.onNodeWithTag("tab-QUIZ").tap()
            waitForTag("q-counter")
            compose.onNodeWithTag("q-counter").assertTextEquals("Question 2 / 20")
            compose.onNodeWithTag("tab-FLASH").tap()
            waitForTag("fc-counter")
            compose.onNodeWithTag("fc-counter").assertTextEquals("3 / 461")
        }
    }

    @Test fun openingACourseFileAddsIt() {
        val file = java.io.File(ctx.cacheDir, "new-course.json")
        file.writeText(
            """{"course":{"id":"test-course","title":"A Test Course","version":1},
               "flashcards":[{"chapter":"Chapter 1: Test","term":"widget","definition":"A test card.","ref":"p. 7"}],
               "quizzes":[]}"""
        )
        val intent = android.content.Intent(ctx, MainActivity::class.java)
            .setAction(android.content.Intent.ACTION_VIEW)
            .setDataAndType(android.net.Uri.fromFile(file), "application/json")
        ActivityScenario.launch<MainActivity>(intent).use {
            // The "Added …" notice shows before the course switches, so wait for the new deck itself.
            waitOrDump("the imported deck") {
                runCatching { compose.onNodeWithTag("fc-counter").assertTextEquals("1 / 1") }.isSuccess
            }
            compose.onNodeWithTag("card").tap()
            waitForTag("fc-ref")
        }
        assertEquals(true, java.io.File(ctx.filesDir, "courses/test-course.json").exists())
    }

    @Test fun chapterRangeNarrowsTheDeck() {
        val course = ctx.assets.open("courses/y3-c1-steam-systems.json").bufferedReader().use {
            com.abeswastaken.studytrainer.core.Course.parse(it.readText())
        }
        val ch = course.chapters
        val expected = course.flashcards.count { it.chapter in ch.subList(0, 4) }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForTag("fc-counter")
            compose.onNodeWithText("All chapters").tap()
            compose.onNodeWithText("Chapter range…").tap()
            compose.onNodeWithText(com.abeswastaken.studytrainer.core.shortChapter(ch.last())).tap()
            compose.onNodeWithText(com.abeswastaken.studytrainer.core.shortChapter(ch[3])).tap()
            compose.onNodeWithTag("range-ok").tap()
            waitOrDump("the Ch. 1–4 deck") {
                runCatching { compose.onNodeWithTag("fc-counter").assertTextEquals("1 / $expected") }.isSuccess
            }
            compose.onNodeWithText("Ch. 1–4").assertExists()
        }
    }

    @Test fun mixedRunHasCardsBetweenQuestions() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForTag("fc-counter")
            compose.onNodeWithTag("tab-QUIZ").tap()
            compose.onNodeWithTag("q-mixed").tap()
            compose.onNodeWithText("Start quiz").tap()
            compose.onNodeWithTag("q-counter").assertTextEquals("Item 1 / 20")
            compose.onNodeWithTag("opt0").tap()
            waitForTag("feedback")
            compose.onNodeWithTag("q-next").tap()
            compose.onNodeWithTag("q-counter").assertTextEquals("Item 2 / 20")
            compose.onNodeWithTag("qc-show").tap()
            waitForTag("qc-term")
            compose.onNodeWithTag("qc-known").tap()
            compose.onNodeWithTag("q-counter").assertTextEquals("Item 3 / 20")
        }
        // The card marked in the quiz counts as known in the Flashcards tab too.
        compose.waitUntil(10_000) { runBlocking { dao.statuses("y3-c1-steam-systems").size == 1 } }
    }

    @Test fun finishingAQuizLogsItInHistory() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForTag("fc-counter")
            compose.onNodeWithTag("tab-QUIZ").tap()
            compose.onNodeWithText("Start quiz").tap()
            repeat(20) {
                waitForTag("opt1")
                compose.onNodeWithTag("opt1").tap()
                compose.onNodeWithTag("q-next").tap()
            }
            waitForTag("q-final")
            compose.onNodeWithTag("tab-HISTORY").tap()
            waitForTag("run")
            compose.onNodeWithTag("run").tap()
            compose.onNodeWithText("Retake these", substring = true).assertExists()
        }
    }
}
