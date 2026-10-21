package com.abeswastaken.studytrainer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abeswastaken.studytrainer.core.ALL_CHAPTERS
import com.abeswastaken.studytrainer.core.CardFilter
import com.abeswastaken.studytrainer.core.CardStatus
import com.abeswastaken.studytrainer.core.ChapterScope
import com.abeswastaken.studytrainer.core.Course
import com.abeswastaken.studytrainer.core.Deck
import com.abeswastaken.studytrainer.core.Flashcard
import com.abeswastaken.studytrainer.core.shortChapter
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun FlashTab(vm: TrainerViewModel, course: Course) {
    val p = LocalPalette.current
    val deck = vm.deck
    val idx = vm.idx
    val card = deck.getOrNull(idx)
    val pos = vm.position
    val filter = CardFilter.of(pos.filter)
    var confirmReset by remember { mutableStateOf(false) }
    var flipped by remember(card?.key, idx) { mutableStateOf(false) }

    if (confirmReset) {
        val scope = when {
            pos.chapter == ALL_CHAPTERS || !ChapterScope.isValid(course, pos.chapter) -> "ALL chapters"
            ChapterScope.ends(pos.chapter) != null -> ChapterScope.label(pos.chapter)
            else -> pos.chapter
        }
        Confirm("Reset known/learning for $scope?", "Reset", onYes = { vm.resetDeck() }, onDismiss = { confirmReset = false })
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        Panel {
            Column {
                Row(horizontalArrangement = Gap8) {
                    ChapterPicker(course, pos.chapter, vm::setChapter, Modifier.weight(2f))
                    Select("Show", filter, CardFilter.entries.map { it to it.label }, vm::setFilter, Modifier.weight(1.2f))
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Gap8, verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.setRandom(!pos.random) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            pos.random, { vm.setRandom(it) },
                            colors = CheckboxDefaults.colors(checkedColor = p.accent, checkmarkColor = p.accentInk, uncheckedColor = p.muted),
                        )
                        Text("Random", color = p.text, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
                    }
                    Btn("🔀 Reshuffle", vm::reshuffle, Modifier.weight(1f), BtnKind.GHOST)
                    Btn("↺ Reset", { confirmReset = true }, Modifier.weight(1f), BtnKind.GHOST)
                }
                Spacer(Modifier.height(10.dp))
                ProgressBar(if (deck.isEmpty()) 0f else (idx + 1f) / deck.size)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(if (deck.isEmpty()) "0 / 0" else "${idx + 1} / ${deck.size}", color = p.muted, fontSize = 12.sp,
                        modifier = Modifier.weight(1f).testTag("fc-counter"))
                    if (deck.isNotEmpty()) {
                        val known = deck.count { vm.statuses[it.key] == CardStatus.KNOWN }
                        val learning = deck.count { vm.statuses[it.key] == CardStatus.LEARNING }
                        Text(
                            "$known known · $learning learning (${(100.0 * known / deck.size).roundToInt()}%)",
                            color = p.muted, fontSize = 12.sp, modifier = Modifier.testTag("fc-known"),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(SectionGap))

        val density = LocalDensity.current
        val swipe = with(density) { 50.dp.toPx() }
        Box(
            Modifier.pointerInput(card?.key) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { if (abs(dx) >= swipe) { if (dx < 0) vm.next() else vm.prev() } },
                ) { _, d -> dx += d }
            }
        ) {
            key(card?.key, idx) {
                FlipCard(card, vm.statuses[card?.key ?: ""], flipped, Deck.emptyMessage(filter)) { flipped = !flipped }
            }
        }
        card?.image?.let { Figure(it, Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(SectionGap))

        Row(horizontalArrangement = Gap8) {
            Btn("← Prev", vm::prev, Modifier.weight(1f))
            Btn("Flip", { flipped = !flipped }, Modifier.weight(1f), BtnKind.PRIMARY)
            Btn("Next →", vm::next, Modifier.weight(1f))
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Gap8) {
            Btn("✗ Still learning", { vm.mark(CardStatus.LEARNING) }, Modifier.weight(1f), BtnKind.BAD)
            Btn("✓ Got it", { vm.mark(CardStatus.KNOWN) }, Modifier.weight(1f), BtnKind.GOOD)
        }
        if (card != null) {
            key(card.key) { NoteAndCopy(vm, card.key, copyText(card), Modifier.padding(top = 8.dp)) }
        }
        Footer(course.meta.source.ifBlank { course.meta.title })
    }
}

/** Definition on the front, term on the back (settled: recognize the term from its meaning). */
@Composable
private fun FlipCard(card: Flashcard?, status: CardStatus?, flipped: Boolean, emptyText: String, onTap: () -> Unit) {
    val p = LocalPalette.current
    val rot by animateFloatAsState(if (flipped) 180f else 0f, tween(500), label = "flip")
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape(16.dp)
    val back = rot > 90f
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp)
            .graphicsLayer { rotationY = rot; cameraDistance = 12f * density }
            .clip(shape)
            .background(if (back) p.surface2 else p.surface)
            .border(1.dp, p.border, shape)
            .clickable(enabled = card != null, onClick = onTap)
            .testTag("card"),
    ) {
        // Counter-rotate the back so its text reads the right way round.
        Box(Modifier.fillMaxWidth().heightIn(min = 280.dp).graphicsLayer { if (back) rotationY = 180f }) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 280.dp).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                if (card == null) {
                    Text(emptyText, color = p.text, fontSize = 16.sp, textAlign = TextAlign.Center)
                    return@Column
                }
                Text(if (back) "TERM" else "DEFINITION", color = p.muted, fontSize = 11.sp, letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 10.dp, top = 22.dp))
                if (back) {
                    Text(card.term, color = p.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("fc-term"))
                    card.ref?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp).testTag("fc-ref"))
                    }
                } else {
                    Text(card.definition, color = p.text, fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("fc-def"))
                }
                Text(if (back) "Tap card to flip back" else "Tap card to reveal term", color = p.muted, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp))
            }
            if (card != null) {
                Pill(shortChapter(card.chapter), p.surface2, p.muted,
                    Modifier.align(Alignment.TopStart).padding(10.dp).widthIn(max = 200.dp), border = p.border)
                if (!back) when (status) {
                    CardStatus.KNOWN -> Pill("✓ known", p.good, Color.White, Modifier.align(Alignment.TopEnd).padding(10.dp))
                    CardStatus.LEARNING -> Pill("⟳ learning", p.warn, Color.White, Modifier.align(Alignment.TopEnd).padding(10.dp))
                    null -> {}
                }
            }
        }
    }
}

/** What ⧉ Copy puts on the clipboard for a card. */
fun copyText(card: Flashcard): String =
    "${card.term}: ${card.definition}" + (card.ref?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
