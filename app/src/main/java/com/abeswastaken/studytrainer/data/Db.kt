package com.abeswastaken.studytrainer.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert

// Every row is scoped by courseId so adding a course never touches another course's progress.

@Entity(tableName = "card_status", primaryKeys = ["courseId", "cardKey"])
data class CardStatusEntity(val courseId: String, val cardKey: String, val status: String)

@Entity(tableName = "position", primaryKeys = ["courseId"])
data class PositionEntity(
    val courseId: String,
    val chapter: String,
    val filter: String,
    val idx: Int,
    val random: Boolean,
    val seed: Long,
)

@Entity(tableName = "quiz_run")
data class QuizRunEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: String,
    val date: Long,
    val chapter: String,
    val score: Int,
    val total: Int,
    val retake: Boolean,
    /** The missed questions as JSON (a list of core Miss). */
    val wrongJson: String,
)

/** The quiz in progress, as JSON (core ActiveQuiz). At most one per course. */
@Entity(tableName = "active_quiz", primaryKeys = ["courseId"])
data class ActiveQuizEntity(val courseId: String, val json: String)

@Dao
abstract class TrainerDao {
    @Query("SELECT * FROM card_status WHERE courseId = :courseId")
    abstract suspend fun statuses(courseId: String): List<CardStatusEntity>

    @Upsert abstract suspend fun putStatus(e: CardStatusEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putStatuses(list: List<CardStatusEntity>)

    @Query("DELETE FROM card_status WHERE courseId = :courseId AND cardKey IN (:keys)")
    abstract suspend fun deleteStatusChunk(courseId: String, keys: List<String>)

    /** Chunked: older SQLite allows only 999 bound values per statement. */
    @Transaction
    open suspend fun deleteStatuses(courseId: String, keys: List<String>) {
        keys.chunked(500).forEach { deleteStatusChunk(courseId, it) }
    }

    @Query("SELECT * FROM position WHERE courseId = :courseId")
    abstract suspend fun position(courseId: String): PositionEntity?

    @Upsert abstract suspend fun putPosition(e: PositionEntity)

    @Query("SELECT * FROM quiz_run WHERE courseId = :courseId ORDER BY date DESC, id DESC")
    abstract suspend fun runs(courseId: String): List<QuizRunEntity>

    @Insert abstract suspend fun insertRun(e: QuizRunEntity): Long

    @Insert abstract suspend fun insertRuns(list: List<QuizRunEntity>)

    @Query(
        "DELETE FROM quiz_run WHERE courseId = :courseId AND id NOT IN " +
            "(SELECT id FROM quiz_run WHERE courseId = :courseId ORDER BY date DESC, id DESC LIMIT :keep)"
    )
    abstract suspend fun pruneRuns(courseId: String, keep: Int)

    @Transaction
    open suspend fun addRun(e: QuizRunEntity, keep: Int) {
        insertRun(e)
        pruneRuns(e.courseId, keep)
    }

    @Query("SELECT * FROM active_quiz WHERE courseId = :courseId")
    abstract suspend fun activeQuiz(courseId: String): ActiveQuizEntity?

    @Upsert abstract suspend fun putActiveQuiz(e: ActiveQuizEntity)

    @Query("DELETE FROM active_quiz WHERE courseId = :courseId")
    abstract suspend fun clearActiveQuiz(courseId: String)

    @Query(
        "SELECT courseId FROM card_status UNION SELECT courseId FROM position UNION SELECT courseId FROM quiz_run"
    )
    abstract suspend fun courseIdsWithProgress(): List<String>

    @Query("DELETE FROM card_status WHERE courseId = :courseId") abstract suspend fun clearStatuses(courseId: String)
    @Query("DELETE FROM position WHERE courseId = :courseId") abstract suspend fun clearPosition(courseId: String)
    @Query("DELETE FROM quiz_run WHERE courseId = :courseId") abstract suspend fun clearRuns(courseId: String)

    /** Import: replaces one course's progress in one step. */
    @Transaction
    open suspend fun replaceProgress(
        courseId: String,
        statuses: List<CardStatusEntity>,
        position: PositionEntity?,
        runs: List<QuizRunEntity>,
    ) {
        clearStatuses(courseId); clearPosition(courseId); clearRuns(courseId); clearActiveQuiz(courseId)
        putStatuses(statuses)
        position?.let { putPosition(it) }
        insertRuns(runs)
    }
}

@Database(
    entities = [CardStatusEntity::class, PositionEntity::class, QuizRunEntity::class, ActiveQuizEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TrainerDao

    companion object {
        const val NAME = "trainer.db"
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .build().also { instance = it }
        }
    }
}
