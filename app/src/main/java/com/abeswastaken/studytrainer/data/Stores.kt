package com.abeswastaken.studytrainer.data

import android.content.Context
import com.abeswastaken.studytrainer.core.ALL_CHAPTERS
import com.abeswastaken.studytrainer.core.Course
import java.io.File

/** Per-device settings. Not part of the progress export. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var theme: String
        get() = sp.getString("theme", "auto") ?: "auto"
        set(v) = sp.edit().putString("theme", v).apply()
    var courseId: String?
        get() = sp.getString("course", null)
        set(v) = sp.edit().putString("course", v).apply()
    var tab: Int
        get() = sp.getInt("tab", 0)
        set(v) = sp.edit().putInt("tab", v).apply()
    var quizChapter: String
        get() = sp.getString("quiz_chapter", ALL_CHAPTERS) ?: ALL_CHAPTERS
        set(v) = sp.edit().putString("quiz_chapter", v).apply()
    var quizLength: Int
        get() = sp.getInt("quiz_length", 20)
        set(v) = sp.edit().putInt("quiz_length", v).apply()

    fun clear() = sp.edit().clear().commit()
}

/**
 * Courses are data: bundled ones ship in assets/courses, imported ones are copied to files/courses.
 * For the same course id the highest version wins; an imported file wins a tie.
 */
class CourseStore(private val context: Context) {
    private val dir = File(context.filesDir, "courses")

    fun loadAll(): List<Course> {
        val imported = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
            .mapNotNull { runCatching { Course.parse(it.readText()) }.getOrNull() }
        val bundled = context.assets.list("courses").orEmpty().filter { it.endsWith(".json") }.sorted()
            .mapNotNull { name ->
                runCatching { context.assets.open("courses/$name").bufferedReader().use { Course.parse(it.readText()) } }.getOrNull()
            }
        return (imported + bundled)
            .groupBy { it.id }
            .map { (_, list) -> list.maxWith(compareBy<Course> { it.meta.version }.thenByDescending { list.indexOf(it) }) }
            .sortedBy { it.meta.title }
    }

    /** Validates and saves an imported course file. Throws with a readable message if it can't be used. */
    fun import(text: String): Course {
        val course = Course.parse(text)
        val existing = loadAll().firstOrNull { it.id == course.id }
        require(existing == null || course.meta.version >= existing.meta.version) {
            "This phone already has a newer version of ${existing!!.meta.title} (v${existing.meta.version})."
        }
        dir.mkdirs()
        File(dir, course.id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json").writeText(text)
        return course
    }
}
