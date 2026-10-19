package com.abeswastaken.studytrainer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import java.time.LocalDate

@Composable
fun TrainerApp(vm: TrainerViewModel) {
    val p = LocalPalette.current
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.notice) {
        vm.notice?.let { snack.showSnackbar(it); vm.notice = null }
    }
    Scaffold(
        containerColor = p.bg,
        topBar = { Header(vm) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val course = vm.course
            when {
                vm.loading -> {}
                course == null -> Text(
                    "No course found. Use ⋮ > Import course to add one.",
                    color = p.muted, modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> when (vm.tab) {
                    Tab.FLASH -> FlashTab(vm, course)
                    Tab.QUIZ -> QuizTab(vm, course)
                    Tab.HISTORY -> HistoryTab(vm, course)
                    Tab.STATS -> StatsTab(vm, course)
                }
            }
        }
    }
}

@Composable
private fun Header(vm: TrainerViewModel) {
    val p = LocalPalette.current
    var menu by remember { mutableStateOf(false) }
    var coursesOpen by remember { mutableStateOf(false) }
    var pendingProgress by remember { mutableStateOf<Uri?>(null) }

    val importCourse = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importCourse(it) }
    }
    val exportProgress = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportProgress(it) }
    }
    val importProgress = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingProgress = uri
    }
    pendingProgress?.let { uri ->
        Confirm(
            "Replace the progress on this phone with the progress in that file? Courses in the file are overwritten.",
            "Replace", onYes = { vm.importProgress(uri) }, onDismiss = { pendingProgress = null },
        )
    }

    Column(Modifier.fillMaxWidth().background(p.bg).statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val course = vm.course
                val many = vm.courses.size > 1
                Box {
                    Text(
                        (course?.meta?.title ?: "Study Trainer") + if (many) "  ▾" else "",
                        color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = if (many) Modifier.clickable { coursesOpen = true } else Modifier,
                    )
                    DropdownMenu(expanded = coursesOpen, onDismissRequest = { coursesOpen = false }) {
                        vm.courses.forEach { c ->
                            DropdownMenuItem(
                                text = { Text((if (c.id == course?.id) "✓ " else "") + c.meta.title) },
                                onClick = { coursesOpen = false; vm.selectCourse(c) },
                            )
                        }
                    }
                }
                if (course != null) {
                    Text(
                        listOf(course.meta.program, "${course.flashcards.size} cards", "${course.quizzes.size} Qs")
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconBtn(if (vm.theme == "auto") "◐" else if (vm.theme == "light") "☀" else "☾", "Theme: ${vm.theme}") { vm.cycleTheme() }
            Box {
                IconBtn("⋮", "Menu") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Import course…") }, onClick = {
                        menu = false; importCourse.launch(arrayOf("*/*"))
                    })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Export progress…") }, onClick = {
                        menu = false; exportProgress.launch("study-trainer-progress-${LocalDate.now()}.json")
                    })
                    DropdownMenuItem(text = { Text("Import progress…") }, onClick = {
                        menu = false; importProgress.launch(arrayOf("*/*"))
                    })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tab.entries.forEach { t ->
                val active = vm.tab == t
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) p.accent else p.surface2)
                        .clickable { vm.selectTab(t) }
                        .testTag("tab-${t.name}")
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(t.label, color = if (active) p.accentInk else p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun IconBtn(symbol: String, desc: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).testTag(desc),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, color = p.text, fontSize = 20.sp) }
}

/** Footer line with the course source. */
@Composable
fun Footer(text: String) {
    val p = LocalPalette.current
    Text(
        text, color = p.muted, fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 40.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

val SectionGap = 14.dp
val Spacer10 = Modifier.height(10.dp)
