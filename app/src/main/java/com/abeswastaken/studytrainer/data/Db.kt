package com.abeswastaken.studytrainer.data

import android.content.Context
import androidx.room.ColumnInfo
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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    @ColumnInfo(defaultValue = "0") val exam: Boolean = false,
    /** Per-chapter results as JSON (core ChapterScore by chapter); null for runs from before v2. */
    val chaptersJson: String? = null,
)

/** Spaced-repetition state for one card (core Review). */
@Entity(tableName = "card_review", primaryKeys = ["courseId", "cardKey"])
data class CardReviewEntity(
    val courseId: String,
    val cardKey: String,
    val ease: Double,
    val interval: Int,
    val due: Long,
    val reps: Int,
)

/** A personal note on a card or question, keyed like the course ("<chapter>|<term or question>"). */
@Entity(tableName = "note", primaryKeys = ["courseId", "itemKey"])
data class NoteEntity(val courseId: String, val itemKey: String, val text: String)

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

    @Query("SELECT * FROM card_review WHERE courseId = :courseId")
    abstract suspend fun reviews(courseId: String): List<CardReviewEntity>

    @Upsert abstract suspend fun putReview(e: CardReviewEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putReviews(list: List<CardReviewEntity>)

    @Query("SELECT * FROM note WHERE courseId = :courseId")
    abstract suspend fun notes(courseId: String): List<NoteEntity>

    @Upsert abstract suspend fun putNote(e: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putNotes(list: List<NoteEntity>)

    @Query("DELETE FROM note WHERE courseId = :courseId AND itemKey = :itemKey")
    abstract suspend fun deleteNote(courseId: String, itemKey: String)

    @Query(
        "SELECT courseId FROM card_status UNION SELECT courseId FROM position UNION SELECT courseId FROM quiz_run " +
            "UNION SELECT courseId FROM card_review UNION SELECT courseId FROM note"
    )
    abstract suspend fun courseIdsWithProgress(): List<String>

    @Query("DELETE FROM card_status WHERE courseId = :courseId") abstract suspend fun clearStatuses(courseId: String)
    @Query("DELETE FROM position WHERE courseId = :courseId") abstract suspend fun clearPosition(courseId: String)
    @Query("DELETE FROM quiz_run WHERE courseId = :courseId") abstract suspend fun clearRuns(courseId: String)
    @Query("DELETE FROM card_review WHERE courseId = :courseId") abstract suspend fun clearReviews(courseId: String)
    @Query("DELETE FROM note WHERE courseId = :courseId") abstract suspend fun clearNotes(courseId: String)

    /** Import: replaces one course's progress in one step. */
    @Transaction
    open suspend fun replaceProgress(
        courseId: String,
        statuses: List<CardStatusEntity>,
        position: PositionEntity?,
        runs: List<QuizRunEntity>,
        reviews: List<CardReviewEntity> = emptyList(),
        notes: List<NoteEntity> = emptyList(),
    ) {
        clearStatuses(courseId); clearPosition(courseId); clearRuns(courseId); clearActiveQuiz(courseId)
        clearReviews(courseId); clearNotes(courseId)
        putStatuses(statuses)
        position?.let { putPosition(it) }
        insertRuns(runs)
        putReviews(reviews)
        putNotes(notes)
    }
}

@Database(
    entities = [
        CardStatusEntity::class, PositionEntity::class, QuizRunEntity::class, ActiveQuizEntity::class,
        CardReviewEntity::class, NoteEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TrainerDao

    companion object {
        const val NAME = "trainer.db"

        /** v2: spaced repetition, notes, and exam / per-chapter results on runs. Keeps all v1 progress. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quiz_run ADD COLUMN `exam` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE quiz_run ADD COLUMN `chaptersJson` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `card_review` (`courseId` TEXT NOT NULL, `cardKey` TEXT NOT NULL, " +
                        "`ease` REAL NOT NULL, `interval` INTEGER NOT NULL, `due` INTEGER NOT NULL, `reps` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`courseId`, `cardKey`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `note` (`courseId` TEXT NOT NULL, `itemKey` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                        "PRIMARY KEY(`courseId`, `itemKey`))"
                )
            }
        }
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
