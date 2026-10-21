package com.abeswastaken.studytrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abeswastaken.studytrainer.core.Course
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryTab(vm: TrainerViewModel, course: Course) {
    val p = LocalPalette.current
    var open by rememberSaveable { mutableStateOf(-1L) }
    var pendingRetake by remember { mutableStateOf<RunRow?>(null) }
    pendingRetake?.let { row ->
        Confirm("Drop the quiz you're in the middle of and retake these?", "Retake",
            onYes = { vm.retake(row) }, onDismiss = { pendingRetake = null })
    }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    LazyColumn(Modifier.padding(horizontal = 14.dp)) {
        item {
            Panel {
                Column {
                    Text("Past quizzes", color = p.text, fontWeight = FontWeight.SemiBold)
                    Text("Tap any to see which questions you missed.", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(SectionGap))
        }
        if (vm.runs.isEmpty()) item {
            Text("No quizzes yet. Take one and it'll show up here.", color = p.muted, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp))
        }
        items(vm.runs, key = { it.id }) { row ->
            val r = row.run
            val shape = RoundedCornerShape(12.dp)
            val color = when { r.percent >= 85 -> p.good; r.percent >= 70 -> p.warn; else -> p.bad }
            Row(
                Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(p.surface)
                    .border(1.dp, p.border, shape)
                    .clickable { open = if (open == row.id) -1L else row.id }
                    .testTag("run")
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.label, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${fmt.format(Date(r.date))} · ${r.total} Qs", color = p.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 10.dp)) {
                    Text("${r.score}/${r.total}", color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("${r.percent}%", color = color, fontSize = 11.sp)
                }
            }
            if (open == row.id) {
                Column(Modifier.padding(bottom = 12.dp)) {
                    if (r.wrong.isEmpty()) {
                        Detail { Text("All correct — nothing to review.", color = p.text, fontSize = 13.sp) }
                    } else {
                        Btn("🔁 Retake these ${r.wrong.size} questions", {
                            val q = vm.quiz
                            if (q != null && !q.finished) pendingRetake = row else vm.retake(row)
                        }, Modifier.fillMaxWidth().padding(bottom = 2.dp), BtnKind.GHOST)
                        r.wrong.forEach { w ->
                            Detail {
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Column {
                                        Text(w.question, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("You: ${w.pickedText.ifBlank { "—" }}", color = p.bad, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                                        Text("Correct: ${w.correctText}" + if (w.ref.isNotBlank()) "  (${w.ref})" else "",
                                            color = p.good, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Footer(course.meta.source.ifBlank { course.meta.title }) }
    }
}

@Composable
private fun Detail(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(p.surface2)
            .border(1.dp, p.border, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) { content() }
}
