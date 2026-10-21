package com.abeswastaken.studytrainer.core

import kotlin.random.Random

/**
 * Electrical math drills: Ohm's law, power, and series/parallel resistance with new numbers each time.
 * Each problem is rebuilt from its key ("drill:<kind>:<seed>"), so an unfinished drill resumes exactly.
 * Uses the book's letters: E volts, I amps, R ohms, P watts.
 */
object Drills {
    const val PREFIX = "drill:"
    const val CHAPTER = "Electrical math drills"
    const val SCOPE = "__drills__"
    const val LENGTH = 10

    val KINDS = listOf("ohmE", "ohmI", "ohmR", "powEI", "powI", "powIR", "powER", "series", "parallel")

    fun isDrill(key: String) = key.startsWith(PREFIX)

    fun keys(n: Int, random: Random = Random.Default): List<String> =
        List(n) { PREFIX + KINDS[random.nextInt(KINDS.size)] + ":" + random.nextInt(1, Int.MAX_VALUE) }

    /** The question for a drill key, or null if the key isn't a drill. */
    fun make(key: String): QuizQuestion? {
        if (!isDrill(key)) return null
        val parts = key.removePrefix(PREFIX).split(":")
        if (parts.size != 2) return null
        val r = Random(parts[1].toLongOrNull() ?: return null)
        return when (parts[0]) {
            "ohmE" -> { val i = pick(r, 1, 2, 3, 4, 5, 6, 8, 10); val res = pick(r, 2, 3, 4, 5, 6, 10, 12, 15, 20, 24)
                q(key, r, "Find E: I = $i A, R = $res Ω.", i * res.toDouble(), "V", "E = I × R",
                    listOf(res.toDouble() / i, i + res.toDouble(), i.toDouble() * i * res)) }
            "ohmI" -> { val i = pick(r, 1, 2, 3, 4, 5, 6, 8, 10); val res = pick(r, 2, 3, 4, 5, 6, 10, 12, 15, 20, 24); val e = i * res
                q(key, r, "Find I: E = $e V, R = $res Ω.", i.toDouble(), "A", "I = E ÷ R",
                    listOf(e.toDouble() * res, res.toDouble() / e, e.toDouble() - res)) }
            "ohmR" -> { val i = pick(r, 1, 2, 3, 4, 5, 6, 8, 10); val res = pick(r, 2, 3, 4, 5, 6, 10, 12, 15, 20, 24); val e = i * res
                q(key, r, "Find R: E = $e V, I = $i A.", res.toDouble(), "Ω", "R = E ÷ I",
                    listOf(e.toDouble() * i, i.toDouble() / e, e.toDouble() + i)) }
            "powEI" -> { val e = pick(r, 6, 12, 24, 48, 120, 240); val i = pick(r, 1, 2, 3, 4, 5, 10)
                q(key, r, "Find P: E = $e V, I = $i A.", e * i.toDouble(), "W", "P = E × I",
                    listOf(e.toDouble() / i, e + i.toDouble(), e.toDouble() * i * i)) }
            "powI" -> { val e = pick(r, 6, 12, 24, 48, 120, 240); val i = pick(r, 1, 2, 3, 4, 5, 10); val p = e * i
                q(key, r, "Find I: P = $p W, E = $e V.", i.toDouble(), "A", "I = P ÷ E",
                    listOf(p.toDouble() * e, e.toDouble() / p, p.toDouble() - e)) }
            "powIR" -> { val i = pick(r, 1, 2, 3, 4, 5, 6); val res = pick(r, 2, 3, 4, 5, 6, 10, 12, 20)
                q(key, r, "Find P: I = $i A, R = $res Ω.", i.toDouble() * i * res, "W", "P = I² × R",
                    listOf(i.toDouble() * res, i.toDouble() * res * res, i.toDouble() * i / res)) }
            "powER" -> { val res = pick(r, 2, 3, 4, 5, 6, 8, 10, 12); val i = pick(r, 1, 2, 3, 4, 5, 6); val e = i * res
                q(key, r, "Find P: E = $e V, R = $res Ω.", e.toDouble() * e / res, "W", "P = E² ÷ R",
                    listOf(e.toDouble() / res, e.toDouble() * res, e.toDouble() * e * res)) }
            "series" -> { val rs = List(r.nextInt(2, 5)) { pick(r, 2, 3, 4, 5, 6, 7, 8, 10, 12, 15, 20) }
                val total = rs.sum().toDouble()
                q(key, r, "Series circuit: " + rs.mapIndexed { n, v -> "R${n + 1} = $v Ω" }.joinToString(", ") + ". Find RT.",
                    total, "Ω", "Series: RT = R1 + R2 + …",
                    listOf(parallel(rs), total / rs.size, rs.max().toDouble())) }
            "parallel" -> { val rs = PARALLEL_SETS[r.nextInt(PARALLEL_SETS.size)]
                val total = parallel(rs)
                q(key, r, "Parallel circuit: " + rs.mapIndexed { n, v -> "R${n + 1} = $v Ω" }.joinToString(", ") + ". Find RT.",
                    total, "Ω", if (rs.size == 2) "Parallel: RT = (R1 × R2) ÷ (R1 + R2)" else "Parallel: 1/RT = 1/R1 + 1/R2 + …",
                    listOf(rs.sum().toDouble(), rs.sum().toDouble() / rs.size, rs.min().toDouble() * 2)) }
            else -> null
        }
    }

    /** Sets that give a whole-number total in parallel. */
    private val PARALLEL_SETS = listOf(
        listOf(6, 3), listOf(12, 6), listOf(20, 5), listOf(10, 10), listOf(30, 15), listOf(12, 4),
        listOf(60, 30, 20), listOf(12, 12, 6), listOf(6, 6, 6), listOf(20, 20, 10), listOf(30, 30, 15), listOf(4, 12),
    )

    private fun parallel(rs: List<Int>): Double = 1.0 / rs.sumOf { 1.0 / it }

    private fun pick(r: Random, vararg xs: Int) = xs[r.nextInt(xs.size)]

    fun fmt(v: Double): String {
        val rounded = Math.round(v * 100) / 100.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
    }

    /** Four options: the answer plus distinct wrong ones from common slips (multiplied instead of divided, etc.). */
    private fun q(key: String, r: Random, text: String, answer: Double, unit: String, formula: String, slips: List<Double>): QuizQuestion {
        val right = "${fmt(answer)} $unit"
        val wrong = LinkedHashSet<String>()
        for (s in slips) { val t = "${fmt(s)} $unit"; if (s > 0 && t != right) wrong += t }
        var k = 2
        while (wrong.size < 3) { val t = "${fmt(answer * k)} $unit"; if (t != right) wrong += t; k++ }
        val opts = (wrong.take(3) + right).shuffled(r)
        return QuizQuestion(CHAPTER, null, text, opts, opts.indexOf(right), formula)
    }
}
