package com.abeswastaken.studytrainer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abeswastaken.studytrainer.data.AppDatabase
import com.abeswastaken.studytrainer.data.CardStatusEntity
import com.abeswastaken.studytrainer.data.PositionEntity
import com.abeswastaken.studytrainer.data.QuizRunEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: AppDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After fun close() = db.close()

    private fun run(courseId: String, date: Long) =
        QuizRunEntity(courseId = courseId, date = date, chapter = "__all__", score = 1, total = 2, retake = false, wrongJson = "[]")

    @Test fun historyKeepsTheNewest100PerCourse() = runBlocking {
        val dao = db.dao()
        repeat(105) { dao.addRun(run("a", it.toLong()), 100) }
        dao.addRun(run("b", 1), 100)
        val runs = dao.runs("a")
        assertEquals(100, runs.size)
        assertEquals(104L, runs.first().date)
        assertEquals(5L, runs.last().date)
        assertEquals(1, dao.runs("b").size)
    }

    @Test fun coursesDoNotShareProgress() = runBlocking {
        val dao = db.dao()
        dao.putStatus(CardStatusEntity("a", "ch|t", "known"))
        dao.putStatus(CardStatusEntity("b", "ch|t", "learning"))
        dao.putStatus(CardStatusEntity("a", "ch|t", "learning")) // upsert
        assertEquals(listOf("learning"), dao.statuses("a").map { it.status })
        dao.clearStatuses("b")
        assertEquals(1, dao.statuses("a").size)
    }

    @Test fun resetHandlesBigDecks() = runBlocking {
        val dao = db.dao()
        val keys = (0 until 1500).map { "ch|$it" }
        dao.putStatuses(keys.map { CardStatusEntity("a", it, "known") })
        dao.deleteStatuses("a", keys.take(1200))
        assertEquals(300, dao.statuses("a").size)
    }

    @Test fun importReplacesOneCourse() = runBlocking {
        val dao = db.dao()
        dao.putStatus(CardStatusEntity("a", "old", "known"))
        dao.addRun(run("a", 1), 100)
        dao.replaceProgress("a", listOf(CardStatusEntity("a", "new", "learning")), PositionEntity("a", "__all__", "all", 7, true, 9), emptyList())
        assertEquals(listOf("new"), dao.statuses("a").map { it.cardKey })
        assertEquals(7, dao.position("a")!!.idx)
        assertEquals(0, dao.runs("a").size)
        assertNull(dao.activeQuiz("a"))
    }
}
