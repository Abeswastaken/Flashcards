package com.abeswastaken.studytrainer

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abeswastaken.studytrainer.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Updating the app from v1 of the database keeps every card mark, place and quiz run. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun v1ProgressSurvivesV2() {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO card_status (courseId, cardKey, status) VALUES ('c', 'Ch 1|ohm', 'known')")
            execSQL("INSERT INTO position (courseId, chapter, filter, idx, random, seed) VALUES ('c', '__all__', 'all', 7, 1, 99)")
            execSQL(
                "INSERT INTO quiz_run (courseId, date, chapter, score, total, retake, wrongJson) " +
                    "VALUES ('c', 5, '__all__', 8, 10, 0, '[]')"
            )
            execSQL("INSERT INTO active_quiz (courseId, json) VALUES ('c', '{}')")
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true, AppDatabase.MIGRATION_1_2).close()

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_1_2).build()
        try {
            runBlocking {
                val dao = db.dao()
                assertEquals("known", dao.statuses("c").single().status)
                assertEquals(7, dao.position("c")!!.idx)
                val run = dao.runs("c").single()
                assertEquals(8, run.score)
                assertEquals(false, run.exam)
                assertNull(run.chaptersJson)
                assertEquals("{}", dao.activeQuiz("c")!!.json)
                assertEquals(0, dao.reviews("c").size)
                assertEquals(0, dao.notes("c").size)
            }
        } finally {
            db.close()
        }
    }
}
