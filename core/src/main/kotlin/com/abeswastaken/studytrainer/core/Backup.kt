package com.abeswastaken.studytrainer.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Progress for one course. Card statuses use the artifact's "<chapter>|<term>" keys. */
@Serializable
data class CourseProgress(
    val cards: Map<String, String> = emptyMap(),
    val position: Position? = null,
    val runs: List<QuizRun> = emptyList(),
    /** Spaced-repetition state per card key. */
    val reviews: Map<String, Review> = emptyMap(),
    /** Personal notes per card key or question key. */
    val notes: Map<String, String> = emptyMap(),
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

        /** Only a file that really says it's a Study Trainer backup ("app" key present) is accepted. */
        fun decode(text: String): ProgressExport {
            val raw = json.parseToJsonElement(text) as? JsonObject
            require((raw?.get("app") as? JsonPrimitive)?.content == "study-trainer") { "Not a Study Trainer progress file" }
            return json.decodeFromJsonElement(serializer(), raw!!)
        }
    }
}
