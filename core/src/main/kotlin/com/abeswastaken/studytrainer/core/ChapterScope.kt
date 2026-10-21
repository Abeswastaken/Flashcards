package com.abeswastaken.studytrainer.core

/**
 * Which chapters a deck or quiz covers, stored as one string so it fits the existing saved fields:
 * ALL_CHAPTERS, a single chapter name, or a range "__range__|<from>|<to>" (inclusive, in course order).
 */
object ChapterScope {
    private const val RANGE = "__range__"

    fun range(from: String, to: String): String = if (from == to) from else "$RANGE|$from|$to"

    /** The (from, to) chapter names of a range, or null if [scope] isn't one. */
    fun ends(scope: String): Pair<String, String>? {
        if (!scope.startsWith("$RANGE|")) return null
        val parts = scope.removePrefix("$RANGE|").split("|")
        return if (parts.size == 2) parts[0] to parts[1] else null
    }

    /** The chapters covered, in course order. A scope this course doesn't have covers everything. */
    fun chapters(course: Course, scope: String): List<String> {
        if (scope == ALL_CHAPTERS) return course.chapters
        if (scope in course.chapters) return listOf(scope)
        val (from, to) = ends(scope) ?: return course.chapters
        val a = course.chapters.indexOf(from)
        val b = course.chapters.indexOf(to)
        if (a < 0 || b < 0) return course.chapters
        return course.chapters.subList(minOf(a, b), maxOf(a, b) + 1)
    }

    /** Whether [scope] still means something in this course (else callers fall back to all chapters). */
    fun isValid(course: Course, scope: String): Boolean =
        scope == ALL_CHAPTERS || scope in course.chapters ||
            ends(scope)?.let { (f, t) -> f in course.chapters && t in course.chapters } == true

    /** "All chapters", "Ch. 3: Low-Pressure Boilers", or "Ch. 1–4". */
    fun label(scope: String): String {
        if (scope == Weak.SCOPE) return "Weak spots"
        if (scope == Drills.SCOPE) return "Math drills"
        val (from, to) = ends(scope) ?: return shortChapter(scope)
        val n1 = number(from)
        val n2 = number(to)
        return if (n1 != null && n2 != null) "Ch. $n1–$n2" else "${shortChapter(from)} – ${shortChapter(to)}"
    }

    private fun number(chapter: String): String? = Regex("^Chapter (\\w+)").find(chapter)?.groupValues?.get(1)
}
