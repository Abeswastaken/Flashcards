package com.abeswastaken.studytrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class BtnKind { NORMAL, PRIMARY, GHOST, GOOD, BAD }

/** The artifact's button: rounded, bordered, bold, 44dp tall. */
@Composable
fun Btn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.NORMAL,
    enabled: Boolean = true,
) {
    val p = LocalPalette.current
    val (bg, fg, border) = when (kind) {
        BtnKind.NORMAL -> Triple(p.surface2, p.text, p.border)
        BtnKind.PRIMARY -> Triple(p.accent, p.accentInk, p.accent)
        BtnKind.GHOST -> Triple(Color.Transparent, p.text, p.border)
        BtnKind.GOOD -> Triple(p.good, Color.White, p.good)
        BtnKind.BAD -> Triple(p.bad, Color.White, p.bad)
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

/** A labelled select box, like the artifact's <select>. */
@Composable
fun <T> Select(
    label: String,
    value: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(bottom = 4.dp))
        Box {
            val shape = RoundedCornerShape(10.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .clip(shape)
                    .background(p.surface2)
                    .border(1.dp, p.border, shape)
                    .clickable { open = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    options.firstOrNull { it.first == value }?.second ?: "",
                    color = p.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("▾", color = p.muted, fontSize = 14.sp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (v, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(v) })
                }
            }
        }
    }
}

/** Surface card with the artifact's border and radius. */
@Composable
fun Panel(modifier: Modifier = Modifier, radius: Int = 16, pad: Int = 14, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(radius.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.border, shape)
            .padding(pad.dp)
    ) { content() }
}

/** Thin accent progress bar. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 6.dp, max = 6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(p.surface2)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .heightIn(min = 6.dp, max = 6.dp)
                .background(p.accent)
        )
    }
}

@Composable
fun Confirm(text: String, yes: String, onYes: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onYes() }) { Text(yes) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, border: Color? = null) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) { Text(text, color = fg, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

val Gap8 = Arrangement.spacedBy(8.dp)
