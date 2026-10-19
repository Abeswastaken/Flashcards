package com.abeswastaken.studytrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abeswastaken.studytrainer.core.ALL_CHAPTERS
import com.abeswastaken.studytrainer.core.ActiveQuiz
import com.abeswastaken.studytrainer.core.Course
import com.abeswastaken.studytrainer.core.Explain
import com.abeswastaken.studytrainer.core.QuizText
import kotlin.math.roundToInt

@Composable
fun QuizTab(vm: TrainerViewModel, course: Course) {
    val quiz = vm.quiz
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        when {
            quiz == null -> QuizSetup(vm, course)
            quiz.finished -> QuizDone(vm, quiz)
            else -> QuizRunning(vm, course, quiz)
        }
        Footer(course.meta.source.ifBlank { course.meta.title })
    }
}

@Composable
private fun QuizSetup(vm: TrainerViewModel, course: Course) {
    Panel {
        Column {
            Select(
                "Chapter", vm.quizChapter.takeIf { it in course.chapters } ?: ALL_CHAPTERS,
                listOf(ALL_CHAPTERS to "All chapters") + course.chapters.map { it to it },
                vm::setQuizChapter, Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Select(
                "Questions", vm.quizLength,
                ActiveQuiz.LENGTHS.map { it to if (it == 0) "All" else it.toString() },
                vm::setQuizLength, Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Btn("Start quiz", vm::startQuiz, Modifier.fillMaxWidth(), BtnKind.PRIMARY)
        }
    }
}

@Composable
private fun QuizRunning(vm: TrainerViewModel, course: Course, quiz: ActiveQuiz) {
    val p = LocalPalette.current
    val q = course.question(quiz.pool[quiz.idx]) ?: return
    val picked = quiz.pickedNow
    var confirmQuit by remember { mutableStateOf(false) }
    if (confirmQuit) {
        Confirm("Quit this quiz? Your progress won't be saved.", "Quit", onYes = vm::endQuiz, onDismiss = { confirmQuit = false })
    }

    Panel(pad = 12) {
        Column {
            ProgressBar(quiz.idx.toFloat() / quiz.pool.size)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Question ${quiz.idx + 1} / ${quiz.pool.size}", color = p.muted, fontSize = 12.sp,
                    modifier = Modifier.weight(1f).testTag("q-counter"))
                Text("Score: ${quiz.score}", color = p.muted, fontSize = 12.sp)
            }
        }
    }
    Spacer(Modifier.height(SectionGap))
    Panel(pad = 18) {
        Column {
            Text(q.chapter.uppercase(), color = p.muted, fontSize = 11.sp, letterSpacing = 1.sp)
            q.section?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = p.muted, fontSize = 12.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 2.dp))
            }
            Text(q.question, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                q.options.forEachIndexed { i, text ->
                    val state = when {
                        picked == null -> 0
                        i == q.correct -> 1
                        i == picked -> 2
                        else -> 0
                    }
                    OptionRow(i, text, state, enabled = picked == null) { vm.pick(i) }
                }
            }
            if (picked != null) Feedback(course, q, picked == q.correct)
        }
    }
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Gap8) {
        Btn("Quit", { confirmQuit = true }, Modifier.weight(1f), BtnKind.GHOST)
        Btn(if (quiz.isLast) "Finish" else "Next →", vm::nextQuestion, Modifier.weight(1f).testTag("q-next"), BtnKind.PRIMARY, enabled = picked != null)
    }
}

/** state: 0 plain, 1 correct (green), 2 wrong pick (red). */
@Composable
private fun OptionRow(i: Int, text: String, state: Int, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    val color = when (state) { 1 -> p.good; 2 -> p.bad; else -> null }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(color?.let { p.tint(it) } ?: p.surface2)
            .border(1.dp, color ?: p.border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("opt$i")
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(color ?: p.surface).border(1.dp, color ?: p.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(QuizText.letter(i), color = if (color != null) Color.White else p.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(text, color = p.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
    }
}

/** On a wrong answer: the right answer in full, the textbook ref, and the matching flashcard. */
@Composable
private fun Feedback(course: Course, q: com.abeswastaken.studytrainer.core.QuizQuestion, correct: Boolean) {
    val p = LocalPalette.current
    val c = if (correct) p.good else p.bad
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(p.tint(c))
            .border(1.dp, c, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("feedback")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (correct) "✓ Correct" else "✗ Not quite", color = c, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            q.ref?.takeIf { it.isNotBlank() }?.let { Text(it, color = p.muted, fontSize = 12.sp) }
        }
        if (!correct) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(QuizText.letter(q.correct) + ": ") }
                    append(q.correctText)
                },
                color = p.text, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp),
            )
            Explain.find(course, q)?.let { card ->
                HorizontalDivider(Modifier.padding(top = 8.dp), color = p.border)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(card.term + ": ") }
                        append(card.definition)
                    },
                    color = p.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun QuizDone(vm: TrainerViewModel, quiz: ActiveQuiz) {
    val p = LocalPalette.current
    val pct = (quiz.score * 100.0 / quiz.pool.size).roundToInt()
    Panel(pad = 24) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${quiz.score} / ${quiz.pool.size}", color = p.text, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag("q-final"))
            Text("$pct%", color = p.muted, fontSize = 20.sp, modifier = Modifier.padding(top = 4.dp))
            Text(QuizText.resultMessage(pct), color = p.muted, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp))
            if (quiz.reviewing) {
                Text("Review round: not logged in History.", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Gap8) {
                if (quiz.wrongKeys().isNotEmpty()) Btn("Review wrong", vm::reviewWrong, Modifier.weight(1f), BtnKind.GHOST)
                Btn("New quiz", vm::endQuiz, Modifier.weight(1f), BtnKind.PRIMARY)
            }
        }
    }
}
