package com.abeswastaken.studytrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abeswastaken.studytrainer.core.CardFilter
import com.abeswastaken.studytrainer.core.ChapterScope
import com.abeswastaken.studytrainer.core.Course
import com.abeswastaken.studytrainer.core.Weak
import com.abeswastaken.studytrainer.core.shortChapter
import kotlin.math.roundToInt

/** Where the app opens: pick up where you left off, or jump into any way of studying. */
@Composable
fun HomeTab(vm: TrainerViewModel, course: Course) {
    val p = LocalPalette.current
    val s = vm.stats
    val quiz = vm.quiz
    val active = quiz != null && !quiz.finished
    // Starting something new while a quiz is open replaces it, so ask first.
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    pending?.let { go ->
        Confirm("You have a quiz in progress. Start this instead? The open quiz won't be saved.", "Start",
            onYes = go, onDismiss = { pending = null })
    }
    fun guarded(go: () -> Unit) { if (active) pending = go else go() }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        // Continue
        if (active) {
            Tile(
                "▶ Resume " + (if (quiz!!.exam) "exam" else if (quiz.drill) "drills" else "quiz"),
                "${ChapterScope.label(quiz.chapter)} · ${quiz.idx + 1} of ${quiz.pool.size}",
                "home-continue", primary = true,
            ) { vm.selectTab(Tab.QUIZ) }
        } else {
            val pos = vm.position
            val where = if (ChapterScope.isValid(course, pos.chapter)) ChapterScope.label(pos.chapter) else "All chapters"
            val filter = CardFilter.of(pos.filter)
            Tile(
                "▶ Continue flashcards",
                "$where · " + (if (filter != CardFilter.ALL) filter.label + " · " else "") +
                    if (vm.deck.isEmpty()) "no cards" else "card ${vm.idx + 1} of ${vm.deck.size}",
                "home-continue", primary = true,
            ) { vm.selectTab(Tab.FLASH) }
        }
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Gap8) {
            val due = vm.dueKeys.size
            Tile(
                "⏰ Due today", if (due == 0) "Nothing due" else "$due card${if (due == 1) "" else "s"} to review",
                "home-due", Modifier.weight(1f), badge = due.takeIf { it > 0 },
            ) { vm.openDue() }
            val weakQs = remember(vm.runs, course) { Weak.questions(course, vm.runs.map { it.run }).size }
            val weakCards = s?.learning ?: 0
            Tile(
                "🎯 Weak spots",
                if (weakQs + weakCards == 0) "None yet" else "$weakQs missed Qs · ${minOf(weakCards, Weak.CARDS)} cards",
                "home-weak", Modifier.weight(1f),
            ) { guarded(vm::startWeak) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Gap8) {
            Tile("📝 Quiz", "Pick chapters and length", "home-quiz", Modifier.weight(1f)) {
                vm.chooseQuizExam(false); vm.selectTab(Tab.QUIZ)
            }
            Tile("⏱ Practice exam", "Timed, answers at the end", "home-exam", Modifier.weight(1f)) {
                guarded { vm.endQuiz(); vm.chooseQuizExam(true); vm.selectTab(Tab.QUIZ) }
            }
        }
        if (isElectrical(course)) {
            Spacer(Modifier.height(10.dp))
            Tile("🔢 Math drills", "Ohm's law, power, series and parallel, new numbers each time", "home-drills") {
                guarded(vm::startDrills)
            }
        }
        Spacer(Modifier.height(SectionGap))

        // Snapshot
        if (s != null) {
            Panel {
                Column {
                    Text("Snapshot", color = p.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                    val total = course.flashcards.size
                    val pct = if (total == 0) 0 else (s.known * 100.0 / total).roundToInt()
                    Line("Cards known", "${s.known} of $total ($pct%)")
                    Spacer(Modifier.height(4.dp))
                    ProgressBar(if (total == 0) 0f else s.known.toFloat() / total)
                    Spacer(Modifier.height(8.dp))
                    Line("Quiz average", s.quizAvg?.let { "$it% over ${vm.runs.size} run${if (vm.runs.size == 1) "" else "s"}" } ?: "No quizzes yet")
                    s.weakest?.let { Line("Weakest chapter", "${shortChapter(it.chapter)} · ${it.percent}%", p.bad) }
                    vm.runs.firstOrNull()?.run?.let { last -> Line("Last quiz", "${last.label} · ${last.percent}%") }
                }
            }
        }
        Footer(course.meta.source.ifBlank { course.meta.title })
    }
}

/** Drills are electrical math, so they only show for an electrical course. */
private fun isElectrical(course: Course): Boolean =
    listOf(course.meta.title, course.meta.id, course.meta.source).any { it.contains("electric", ignoreCase = true) }

@Composable
private fun Line(label: String, value: String, valueColor: Color? = null) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = p.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor ?: p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Tile(
    title: String,
    sub: String,
    tag: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    badge: Int? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(shape)
            .background(if (primary) p.accent else p.surface)
            .border(1.dp, if (primary) p.accent else p.border, shape)
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row {
            Text(title, color = if (primary) p.accentInk else p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            badge?.let { Pill("$it", p.warn, Color.White) }
        }
        Text(sub, color = if (primary) p.accentInk.copy(alpha = 0.8f) else p.muted, fontSize = 12.sp,
            modifier = Modifier.padding(top = 3.dp))
    }
}
