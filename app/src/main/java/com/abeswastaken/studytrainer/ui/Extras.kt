package com.abeswastaken.studytrainer.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Decodes a course figure: a data URI ("data:image/png;base64,...") or bare base64. Null if it can't be read. */
fun decodeFigure(src: String?): ImageBitmap? {
    if (src.isNullOrBlank()) return null
    return runCatching {
        val b64 = if (src.startsWith("data:")) src.substringAfter(",") else src
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/** A figure from the book under a card or question. Tap to open it full screen; pinch to zoom there. */
@Composable
fun Figure(src: String?, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val bmp = remember(src) { decodeFigure(src) } ?: return
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.fillMaxWidth()) {
        Image(
            bmp, "Figure",
            Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(shape).background(Color.White).border(1.dp, p.border, shape)
                .clickable { open = true }.testTag("figure"),
            contentScale = ContentScale.Fit,
        )
        Text("Tap the figure to enlarge", color = p.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
    if (open) {
        Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            Box(
                Modifier.fillMaxSize().background(Color.Black)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .testTag("figure-zoom"),
            ) {
                Image(
                    bmp, "Figure, enlarged",
                    Modifier.fillMaxSize().background(Color.White).graphicsLayer {
                        scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                    },
                    contentScale = ContentScale.Fit,
                )
                TextButton(onClick = { open = false }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Text("✕ Close", color = Color.White, modifier = Modifier.background(Color(0x99000000)).padding(6.dp))
                }
            }
        }
    }
}

/**
 * Your own note on a card or question (the course text itself never changes), plus a Copy button
 * so the text can be pasted into a search or another app.
 */
@Composable
fun NoteAndCopy(vm: TrainerViewModel, key: String, copyText: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val clip = LocalClipboardManager.current
    val note = vm.notes[key]
    var editing by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        if (note != null) {
            val shape = RoundedCornerShape(10.dp)
            Text(
                "✎ $note", color = p.text, fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().clip(shape).background(p.surface2).border(1.dp, p.border, shape)
                    .clickable { editing = true }.padding(10.dp).testTag("note-text"),
            )
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Gap8) {
            Btn(if (note == null) "✎ Add note" else "✎ Edit note", { editing = true }, Modifier.weight(1f).testTag("note-edit"), BtnKind.GHOST)
            Btn("⧉ Copy text", {
                clip.setText(AnnotatedString(copyText))
                vm.notice = "Copied."
            }, Modifier.weight(1f).testTag("copy-text"), BtnKind.GHOST)
        }
    }
    if (editing) {
        var text by remember { mutableStateOf(note ?: "") }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Your note") },
            text = {
                OutlinedTextField(
                    text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("note-field"),
                    placeholder = { Text("What to remember, where the book says it, what tripped you up…") },
                )
            },
            confirmButton = { TextButton(onClick = { vm.setNote(key, text); editing = false }, Modifier.testTag("note-save")) { Text("Save") } },
            dismissButton = {
                Row {
                    if (note != null) TextButton(onClick = { vm.setNote(key, ""); editing = false }) { Text("Delete") }
                    TextButton(onClick = { editing = false }) { Text("Cancel") }
                }
            },
        )
    }
}

/** "9:05" from seconds. */
fun clock(sec: Long): String = "%d:%02d".format(sec / 60, sec % 60)
