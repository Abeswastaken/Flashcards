package com.abeswastaken.studytrainer.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One course file (see DECISION-MAP.md §4). Content is data: never edited by the app. */
@Serializable
data class CourseFile(
    val course: CourseMeta,
    val flashcards: List<Flashcard> = emptyList(),
    val quizzes: List<QuizQuestion> = emptyList(),
)

@Serializable
data class CourseMeta(
    val id: String,
    val title: String,
    val program: String = "",
    val source: String = "",
    val version: Int = 1,
)

@Serializable
data class Flashcard(
    val chapter: String,
    val term: String,
    val definition: String,
    /** Optional textbook page, e.g. "p. 12". Added from the chapter text; the card's words never change. */
    val ref: String? = null,
    /** Optional figure from the book, as a data URI ("data:image/png;base64,...") or bare base64. */
    val image: String? = null,
) {
    /** Same key the artifact uses: "<chapter>|<term>". */
    val key: String get() = "$chapter|$term"
}

@Serializable
data class QuizQuestion(
    val chapter: String,
    val section: String? = null,
    val question: String,
    val options: List<String>,
    val correct: Int,
    val ref: String? = null,
    /** Optional figure from the book the question refers to (data URI or bare base64). */
    val image: String? = null,
) {
    val key: String get() = "$chapter|$question"
    val correctText: String get() = options.getOrElse(correct) { "" }
}

/** A loaded course: cards deduped by (chapter, term), questions by (chapter, question). */
class Course(val meta: CourseMeta, flashcards: List<Flashcard>, quizzes: List<QuizQuestion>) {
    val flashcards: List<Flashcard> = flashcards.distinctBy { it.key }
    val quizzes: List<QuizQuestion> = quizzes.distinctBy { it.key }
    val id: String get() = meta.id

    /** Chapters in first-seen order: flashcards first, then any chapter that only has questions. */
    val chapters: List<String> =
        (this.flashcards.map { it.chapter } + this.quizzes.map { it.chapter }).distinct()

    private val quizByKey = this.quizzes.associateBy { it.key }
    fun question(key: String): QuizQuestion? = quizByKey[key]

    private val cardByKey = this.flashcards.associateBy { it.key }
    fun card(key: String): Flashcard? = cardByKey[key]

    /** Lowercased term -> first card with that term (the artifact's termIndex). */
    val termIndex: Map<String, Flashcard> = buildMap {
        for (c in this@Course.flashcards) putIfAbsent(c.term.lowercase().trim(), c)
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        fun parse(text: String): Course {
            val f = json.decodeFromString(CourseFile.serializer(), text)
            require(f.course.id.isNotBlank()) { "Course file has no course.id" }
            require(f.flashcards.isNotEmpty() || f.quizzes.isNotEmpty()) { "Course file has no cards or questions" }
            f.quizzes.forEach { q ->
                require(q.correct in q.options.indices) { "Question has no correct option: ${q.question.take(60)}" }
            }
            return Course(f.course, f.flashcards, f.quizzes)
        }
    }
}

/** "Chapter 3: Low-Pressure Boilers" -> "Ch. 3: Low-Pressure Boilers" (the artifact's short label). */
fun shortChapter(chapter: String): String =
    if (chapter == ALL_CHAPTERS) "All chapters" else chapter.replace(Regex("^Chapter "), "Ch. ")

const val ALL_CHAPTERS = "__all__"
