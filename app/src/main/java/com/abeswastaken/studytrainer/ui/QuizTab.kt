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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
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
import com.abeswastaken.studytrainer.core.Drills
import com.abeswastaken.studytrainer.core.Explain
import com.abeswastaken.studytrainer.core.Flashcard
import com.abeswastaken.studytrainer.core.QuizText
import com.abeswastaken.studytrainer.core.shortChapter
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun QuizTab(vm: TrainerViewModel, course: Course) {
    val quiz = vm.quiz
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        when {
            quiz == null -> QuizSetup(vm, course)
            quiz.finished -> QuizDone(vm, course, quiz)
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
                if (vm.quizMixed && !vm.quizExam) "Items" else "Questions", vm.quizLength,
                ActiveQuiz.LENGTHS.map { it to if (it == 0) "All" else it.toString() },
                vm::chooseQuizLength, Modifier.fillMaxWidth(),
            )
            val p = LocalPalette.current
            Toggle(vm.quizExam, vm::chooseQuizExam, "q-exam", "Exam mode",
                "Timed (1 minute per question), no answers shown until the end.")
            if (!vm.quizExam) {
                Toggle(vm.quizMixed, vm::chooseQuizMixed, "q-mixed", "Mix in flashcards",
                    "Half the items are flashcards, between the questions.")
            }
            Spacer(Modifier.height(10.dp))
            Btn(if (vm.quizExam) "Start exam" else "Start quiz", vm::startQuiz, Modifier.fillMaxWidth().testTag("q-start"), BtnKind.PRIMARY)
        }
    }
}

@Composable
private fun Toggle(on: Boolean, set: (Boolean) -> Unit, tag: String, title: String, hint: String) {
    val p = LocalPalette.current
    Row(
        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { set(!on) }.testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            on, { set(it) },
            colors = CheckboxDefaults.colors(checkedColor = p.accent, checkmarkColor = p.accentInk, uncheckedColor = p.muted),
        )
        Column(Modifier.padding(end = 6.dp)) {
            Text(title, color = p.text, fontSize = 13.sp)
            Text(hint, color = p.muted, fontSize = 11.sp)
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
    // The exam clock: ticks each second and ends the exam at 0, also after reopening the app.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (quiz.exam) {
        LaunchedEffect(quiz.startedAt) {
            while (true) {
                now = System.currentTimeMillis()
                if (quiz.secondsLeft(now) == 0L) { vm.examTimeUp(); break }
                delay(1000)
            }
        }
    }

    Panel(pad = 12) {
        Column {
            ProgressBar(quiz.idx.toFloat() / quiz.pool.size)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text((if (quiz.mixed) "Item" else "Question") + " ${quiz.idx + 1} / ${quiz.pool.size}", color = p.muted, fontSize = 12.sp,
                    modifier = Modifier.weight(1f).testTag("q-counter"))
                val left = quiz.secondsLeft(now)
                if (left != null) {
                    Text("⏱ ${clock(left)} left", color = if (left < 60) p.bad else p.muted, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("q-timer"))
                } else {
                    Text("Score: ${quiz.score}", color = p.muted, fontSize = 12.sp)
                }
            }
        }
    }
    Spacer(Modifier.height(SectionGap))
    if (quiz.currentIsCard) {
        val card = course.card(ActiveQuiz.cardKey(quiz.pool[quiz.idx])) ?: return
        QuizCard(vm, quiz, card, onQuit = { confirmQuit = true })
        return
    }
    val itemKey = quiz.pool[quiz.idx]
    val q = quiz.question(course) ?: return
    Panel(pad = 18) {
        Column {
            SelectionContainer {
                Column {
                    Text(q.chapter.uppercase(), color = p.muted, fontSize = 11.sp, letterSpacing = 1.sp)
                    q.section?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = p.muted, fontSize = 12.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 2.dp))
                    }
                    Text(q.question, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 14.dp).testTag("q-text"))
                }
            }
            q.image?.let { Figure(it, Modifier.padding(bottom = 14.dp)) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                q.options.forEachIndexed { i, text ->
                    val state = when {
                        picked == null -> 0
                        quiz.exam -> if (i == picked) 3 else 0 // exam: just show the pick
                        i == q.correct -> 1
                        i == picked -> 2
                        else -> 0
                    }
                    OptionRow(i, text, state, enabled = picked == null || quiz.exam) { vm.pick(i) }
                }
            }
            if (picked != null && !quiz.exam) Feedback(course, q, picked == q.correct)
        }
    }
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Gap8) {
        Btn("Quit", { confirmQuit = true }, Modifier.weight(1f), BtnKind.GHOST)
        Btn(if (quiz.isLast) "Finish" else "Next →", vm::nextQuestion, Modifier.weight(1f).testTag("q-next"), BtnKind.PRIMARY, enabled = picked != null)
    }
    if (picked != null && !quiz.exam && !Drills.isDrill(itemKey)) {
        key(itemKey) { NoteAndCopy(vm, itemKey, questionCopy(q), Modifier.padding(top = 8.dp)) }
    }
}

private fun questionCopy(q: com.abeswastaken.studytrainer.core.QuizQuestion): String =
    q.question + "\n" + q.options.mapIndexed { i, o -> "${QuizText.letter(i)}. $o" }.joinToString("\n") +
        "\nAnswer: ${QuizText.letter(q.correct)}. ${q.correctText}" + (q.ref?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")

/** state: 0 plain, 1 correct (green), 2 wrong pick (red), 3 picked in an exam (accent, no verdict). */
@Composable
private fun OptionRow(i: Int, text: String, state: Int, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    val color = when (state) { 1 -> p.good; 2 -> p.bad; 3 -> p.accent; else -> null }
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
    SelectionContainer {
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
            (if (q.chapter == Drills.CHAPTER) null else Explain.find(course, q))?.let { card ->
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
}

@Composable
private fun QuizDone(vm: TrainerViewModel, course: Course, quiz: ActiveQuiz) {
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
            } else if (quiz.drill) {
                Text("Practice drills: not logged in History.", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Gap8) {
                if (quiz.wrongKeys().isNotEmpty()) Btn("Review wrong", vm::reviewWrong, Modifier.weight(1f), BtnKind.GHOST)
                Btn("New quiz", vm::endQuiz, Modifier.weight(1f).testTag("q-new"), BtnKind.PRIMARY)
            }
        }
    }
    if (quiz.exam) ExamReview(course, quiz)
}

/** After an exam: how each chapter went, then every miss with the right answer and page. */
@Composable
private fun ExamReview(course: Course, quiz: ActiveQuiz) {
    val p = LocalPalette.current
    val run = remember(quiz) { quiz.toRun(course, 0) }
    Spacer(Modifier.height(SectionGap))
    Panel {
        Column {
            Text("By chapter", color = p.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            run.byChapter.forEach { (ch, sc) ->
                val pct = if (sc.total == 0) 0 else (sc.right * 100.0 / sc.total).roundToInt()
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(shortChapter(ch), color = p.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("${sc.right}/${sc.total} · $pct%", color = if (pct >= 70) p.good else p.bad, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    if (run.wrong.isNotEmpty()) {
        Spacer(Modifier.height(SectionGap))
        Panel {
            SelectionContainer {
                Column {
                    Text("Missed (${run.wrong.size})", color = p.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                    run.wrong.forEach { w ->
                        Column(Modifier.padding(bottom = 12.dp).testTag("exam-miss")) {
                            Text(w.question, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("You: ${w.pickedText.ifBlank { "—" }}", color = p.bad, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                            Text("Correct: ${w.correctText}" + if (w.ref.isNotBlank()) "  (${w.ref})" else "", color = p.good, fontSize = 13.sp)
                        }
                    }
                }
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
            SelectionContainer {
                Text(card.definition, color = p.text, fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp).testTag("qc-def"))
            }
            card.image?.let { Figure(it, Modifier.padding(top = 10.dp)) }
            if (shown || answered) {
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = p.border)
                Text("TERM", color = p.muted, fontSize = 11.sp, letterSpacing = 1.sp)
                SelectionContainer {
                    Text(card.term, color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp).testTag("qc-term"))
                }
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
    if (shown || answered) key(card.key) { NoteAndCopy(vm, card.key, copyText(card), Modifier.padding(top = 8.dp)) }
}
