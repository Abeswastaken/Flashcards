package com.abeswastaken.studytrainer.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Progress for one course. Card statuses use the artifact's "<chapter>|<term>" keys. */
@Serializable
data class CourseProgress(
    val cards: Map<String, String> = emptyMap(),
    val position: Position? = null,
    val runs: List<QuizRun> = emptyList(),
)

/** The export file: every course's progress. Theme is per device and is not included. */
@Serializable
data class ProgressExport(
    val app: String = "study-trainer",
    val format: Int = 1,
    val exportedAt: Long = 0,
    val courses: Map<String, CourseProgress> = emptyMap(),
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

        fun decode(text: String): ProgressExport {
            val p = json.decodeFromString(serializer(), text)
            require(p.app == "study-trainer") { "Not a Study Trainer progress file" }
            return p
        }
    }
}
