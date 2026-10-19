package com.abeswastaken.studytrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abeswastaken.studytrainer.core.Course
import com.abeswastaken.studytrainer.core.shortChapter

@Composable
fun StatsTab(vm: TrainerViewModel, course: Course) {
    val p = LocalPalette.current
    val s = vm.stats ?: return
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        Panel(pad = 18) {
            Row(Modifier.fillMaxWidth()) {
                Big("${s.known}", "Known", Modifier.weight(1f), "st-known")
                Big("${s.learning}", "Learning", Modifier.weight(1f))
                Big(s.quizAvg?.let { "$it%" } ?: "—", "Quiz avg", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(SectionGap))
        Panel {
            Column {
                Text("Progress by chapter", color = p.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
                s.chapters.forEach { c ->
                    Panel(Modifier.padding(bottom = 6.dp), radius = 12, pad = 12) {
                        Column {
                            Text(shortChapter(c.chapter), color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 6.dp))
                            Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)).background(p.surface2)) {
                                if (c.known > 0) Box(Modifier.weight(c.known.toFloat()).fillMaxHeight().background(p.good))
                                if (c.learning > 0) Box(Modifier.weight(c.learning.toFloat()).fillMaxHeight().background(p.warn))
                                val rest = c.total - c.known - c.learning
                                if (rest > 0) Box(Modifier.weight(rest.toFloat()).fillMaxHeight())
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                Text("${c.known} known · ${c.learning} learning", color = p.muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("${c.total} cards", color = p.muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        Footer(course.meta.source.ifBlank { course.meta.title })
    }
}

@Composable
private fun Big(num: String, label: String, modifier: Modifier, tag: String = "") {
    val p = LocalPalette.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(num, color = p.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.testTag(tag))
        Text(label.uppercase(), color = p.muted, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
