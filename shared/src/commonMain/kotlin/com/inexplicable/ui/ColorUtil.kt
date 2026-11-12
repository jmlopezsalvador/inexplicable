package com.inexplicable.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Paleta de la app: color principal (HUD, anillos), secundario (partículas, detalles) y de acento (resultado, destacados). */
data class Palette(val primary: Color, val secondary: Color, val accent: Color)

val LocalPalette = staticCompositionLocalOf { Palette(Color(DEFAULT_PRIMARY), Color(DEFAULT_SECONDARY), Color(DEFAULT_ACCENT)) }

@Composable
fun WithPalette(p: Palette, content: @Composable () -> Unit) = CompositionLocalProvider(LocalPalette provides p, content = content)

/** Color Compose desde un ARGB guardado como Long (siempre opaco). */
fun colorFrom(argb: Long): Color = Color((argb and 0xFFFFFF).toInt() or (0xFF shl 24))

/** `#RRGGBB` en mayúsculas. */
fun colorToHex(argb: Long): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

/** Acepta `#RRGGBB` o `RRGGBB`; devuelve ARGB opaco o null si no es válido. */
fun parseHex(s: String): Long? {
    val t = s.trim().removePrefix("#")
    if (t.length != 6 || !t.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return 0xFF000000L or t.toLong(16)
}

/** Matiz 0..360, saturación y valor 0..1. */
data class Hsv(val h: Float, val s: Float, val v: Float)

fun rgbToHsv(argb: Long): Hsv {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
    val h = when {
        d == 0f -> 0f
        mx == r -> 60f * (((g - b) / d) % 6f)
        mx == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    return Hsv(if (h < 0f) h + 360f else h, if (mx == 0f) 0f else d / mx, mx)
}

fun hsvToRgb(h: Float, s: Float, v: Float): Long {
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r, g, b) = when (floor(h / 60f).toInt().mod(6)) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun ch(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255).toLong()
    return 0xFF000000L or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}
