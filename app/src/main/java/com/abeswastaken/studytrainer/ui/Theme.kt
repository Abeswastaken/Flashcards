package com.abeswastaken.studytrainer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** The chat trainer's colors (its CSS variables), light and dark. */
data class Palette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val accent: Color,
    val accentInk: Color,
    val good: Color = Color(0xFF16A34A),
    val bad: Color = Color(0xFFDC2626),
    val warn: Color = Color(0xFFD97706),
    val dark: Boolean,
) {
    /** CSS color-mix(in srgb, c 15%, surface-2). */
    fun tint(c: Color, amount: Float = 0.15f) = lerp(surface2, c, amount)
}

val LightPalette = Palette(
    bg = Color(0xFFF6F7F9), surface = Color.White, surface2 = Color(0xFFF1F3F6), text = Color(0xFF0F172A),
    muted = Color(0xFF64748B), border = Color(0xFFE2E8F0), accent = Color(0xFF0EA5E9), accentInk = Color.White, dark = false,
)
val DarkPalette = Palette(
    bg = Color(0xFF0B1220), surface = Color(0xFF121A2B), surface2 = Color(0xFF0F1627), text = Color(0xFFE6EDF7),
    muted = Color(0xFF94A3B8), border = Color(0xFF1F2A44), accent = Color(0xFF38BDF8), accentInk = Color(0xFF06283D), dark = true,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun isDark(mode: String): Boolean = when (mode) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun TrainerTheme(mode: String, content: @Composable () -> Unit) {
    val p = if (isDark(mode)) DarkPalette else LightPalette
    val scheme = (if (p.dark) darkColorScheme() else lightColorScheme()).copy(
        primary = p.accent, onPrimary = p.accentInk, background = p.bg, onBackground = p.text,
        surface = p.surface, onSurface = p.text, surfaceVariant = p.surface2, onSurfaceVariant = p.muted,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surface, surfaceContainerHighest = p.surface2,
        outline = p.border, outlineVariant = p.border, error = p.bad,
        inverseSurface = p.text, inverseOnSurface = p.bg,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalPalette provides p, content = content)
    }
}
