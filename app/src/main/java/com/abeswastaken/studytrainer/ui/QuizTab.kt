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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import com.abeswastaken.studytrainer.core.ActiveQuiz
import com.abeswastaken.studytrainer.core.Course
import com.abeswastaken.studytrainer.core.Explain
import com.abeswastaken.studytrainer.core.Flashcard
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
            ChapterPicker(course, vm.quizChapter, vm::chooseQuizChapter, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Select(
                if (vm.quizMixed) "Items" else "Questions", vm.quizLength,
                ActiveQuiz.LENGTHS.map { it to if (it == 0) "All" else it.toString() },
                vm::chooseQuizLength, Modifier.fillMaxWidth(),
            )
            val p = LocalPalette.current
            Row(
                Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { vm.chooseQuizMixed(!vm.quizMixed) }
                    .testTag("q-mixed"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    vm.quizMixed, { vm.chooseQuizMixed(it) },
                    colors = CheckboxDefaults.colors(checkedColor = p.accent, checkmarkColor = p.accentInk, uncheckedColor = p.muted),
                )
                Column(Modifier.padding(end = 6.dp)) {
                    Text("Mix in flashcards", color = p.text, fontSize = 13.sp)
                    Text("Half the items are flashcards, between the questions.", color = p.muted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Btn("Start quiz", vm::startQuiz, Modifier.fillMaxWidth(), BtnKind.PRIMARY)
        }
    }
}

@Composable
private fun QuizRunning(vm: TrainerViewModel, course: Course, quiz: ActiveQuiz) {
    val p = LocalPalette.current
    val picked = quiz.pickedNow
    var confirmQuit by remember { mutableStateOf(false) }
    if (confirmQuit) {
        Confirm("Quit this quiz? Your progress won't be saved.", "Quit", onYes = vm::endQuiz, onDismiss = { confirmQuit = false })
    }

    Panel(pad = 12) {
        Column {
            ProgressBar(quiz.idx.toFloat() / quiz.pool.size)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text((if (quiz.mixed) "Item" else "Question") + " ${quiz.idx + 1} / ${quiz.pool.size}", color = p.muted, fontSize = 12.sp,
                    modifier = Modifier.weight(1f).testTag("q-counter"))
                Text("Score: ${quiz.score}", color = p.muted, fontSize = 12.sp)
            }
        }
    }
    Spacer(Modifier.height(SectionGap))
    if (quiz.currentIsCard) {
        val card = course.card(ActiveQuiz.cardKey(quiz.pool[quiz.idx])) ?: return
        QuizCard(vm, quiz, card, onQuit = { confirmQuit = true })
        return
    }
    val q = course.question(quiz.pool[quiz.idx]) ?: return
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
                        card.ref?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
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
    val total = quiz.questionCount
    val pct = if (total == 0) 0 else (quiz.score * 100.0 / total).roundToInt()
    Panel(pad = 24) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (total > 0) {
                Text("${quiz.score} / $total", color = p.text, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.testTag("q-final"))
                Text("$pct%", color = p.muted, fontSize = 20.sp, modifier = Modifier.padding(top = 4.dp))
                Text(QuizText.resultMessage(pct), color = p.muted, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp))
            }
            if (quiz.mixed) {
                Text("Flashcards: ${quiz.cardsKnown} of ${quiz.cardCount} got it", color = p.text, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp).testTag("q-cards"))
            }
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

/** A flashcard inside a mixed run: definition first, reveal the term, then grade yourself. */
@Composable
private fun QuizCard(vm: TrainerViewModel, quiz: ActiveQuiz, card: Flashcard, onQuit: () -> Unit) {
    val p = LocalPalette.current
    var shown by remember(quiz.idx) { mutableStateOf(false) }
    val answered = quiz.pickedNow != null
    Panel(pad = 18) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("FLASHCARD · " + card.chapter.uppercase(), color = p.muted, fontSize = 11.sp, letterSpacing = 1.sp,
                textAlign = TextAlign.Center)
            Text("DEFINITION", color = p.muted, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 14.dp))
            Text(card.definition, color = p.text, fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp).testTag("qc-def"))
            if (shown || answered) {
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = p.border)
                Text("TERM", color = p.muted, fontSize = 11.sp, letterSpacing = 1.sp)
                Text(card.term, color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp).testTag("qc-term"))
                card.ref?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
    when {
        answered -> Row(Modifier.padding(top = 10.dp), horizontalArrangement = Gap8) {
            Btn("Quit", onQuit, Modifier.weight(1f), BtnKind.GHOST)
            Btn(if (quiz.isLast) "Finish" else "Next →", vm::nextQuestion, Modifier.weight(1f).testTag("q-next"), BtnKind.PRIMARY)
        }
        !shown -> Row(Modifier.padding(top = 10.dp), horizontalArrangement = Gap8) {
            Btn("Quit", onQuit, Modifier.weight(1f), BtnKind.GHOST)
            Btn("Show term", { shown = true }, Modifier.weight(1f).testTag("qc-show"), BtnKind.PRIMARY)
        }
        else -> Row(Modifier.padding(top = 10.dp), horizontalArrangement = Gap8) {
            Btn("✗ Still learning", { vm.markQuizCard(false) }, Modifier.weight(1f).testTag("qc-learning"), BtnKind.BAD)
            Btn("✓ Got it", { vm.markQuizCard(true) }, Modifier.weight(1f).testTag("qc-known"), BtnKind.GOOD)
        }
    }
}
