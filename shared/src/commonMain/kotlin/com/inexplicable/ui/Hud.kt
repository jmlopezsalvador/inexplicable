package com.inexplicable.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.inexplicable.effect.Stage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Segundos desde que se montó; se lee solo en la fase de dibujo para no recomponer cada fotograma. */
@Composable
fun rememberTime(): State<Float> {
    val t = remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t.value = (it - start) / 1_000_000_000f }
    }
    return t
}

/**
 * Reloj de movimiento sin saltos: `phase` acumula el tiempo multiplicado por una velocidad que se suaviza al cambiar de
 * ritmo (p. ej. al empezar o terminar de analizar). Multiplicar el tiempo total por otra velocidad haría saltar los ángulos.
 */
class MotionClock {
    private var last = 0f
    private var k = 1f
    var phase = 0f
        private set
    /** 0..1: sube suavemente mientras `appear` es true (para que los elementos entren sin «pop»). */
    var appearK = 0f
        private set

    fun step(t: Float, fast: Boolean, appear: Boolean = false, fastK: Float = 2.2f): Float {
        val dt = (t - last).coerceIn(0f, 0.1f)
        last = t
        k += ((if (fast) fastK else 1f) - k) * minOf(1f, dt * 3f)
        phase += dt * k
        appearK += ((if (appear) 1f else 0f) - appearK) * minOf(1f, dt * 5f)
        return phase
    }
}

/** Número pseudoaleatorio estable en 0..1 para un índice y una «semilla». */
internal fun hash01(i: Int, k: Int): Float {
    val x = sin(i * 12.9898f + k * 78.233f) * 43758.5453f
    return x - floor(x)
}

private const val TAU = (2 * PI).toFloat()

/** Centro y radio del HUD en pantalla (también lo usa App para saber dónde se toca el sello de resultado). */
fun hudCenter(w: Float, h: Float) = Offset(w / 2, h * 0.36f)
fun hudRadius(w: Float, h: Float) = minOf(w, h) * 0.34f

/**
 * HUD plano estilo Jarvis. `level` 1 = anillos básicos, 2 = + escala graduada, hexágono y satélites,
 * 3 = + anillos gigantes que salen de la pantalla, radar, barras de datos y esquinas. `open` es 0..1 al abrir el resultado.
 */
@Composable
fun HudCanvas(stage: Stage, progress: Float, open: Float, level: Int, time: State<Float>) {
    val pal = LocalPalette.current
    val clock = remember { MotionClock() }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val busy = stage == Stage.Analyzing || stage == Stage.Connecting
        // `tk` es el tiempo «acelerado»: ya incluye el cambio de ritmo, suavizado (sin saltos de ángulo).
        val tk = clock.step(t, busy, appear = stage == Stage.Result)
        val k = 1f
        var c = hudCenter(size.width, size.height)
        if (level >= 3) c = Offset(c.x + sin(t * 0.35f) * size.width * 0.04f, c.y + cos(t * 0.27f) * size.height * 0.015f)
        val r = hudRadius(size.width, size.height)
        val p = pal.primary; val s = pal.secondary; val a = pal.accent

        if (level >= 3) drawBigRings(c, r, tk, k, p, s)
        if (level >= 3) drawRadar(c, r, tk, k, p)

        // Anillos básicos.
        ring(c, r, tk * 26f, 100f, 3f, p.copy(alpha = 0.9f))
        ring(c, r, tk * 26f + 180f, 70f, 3f, p.copy(alpha = 0.6f))
        ring(c, r * 0.82f, -tk * 72f, 220f, 2f, s.copy(alpha = 0.8f))
        ring(c, r * 0.64f, t * 52f, 360f, 1.5f, p.copy(alpha = 0.45f), dash = true)
        ring(c, r * 1.12f, 0f, 360f, 1f, p.copy(alpha = 0.2f))
        if (stage == Stage.Analyzing) ring(c, r * 1.2f, -90f, 360f * progress, 4f, a)

        if (level >= 2) {
            drawTicks(c, r * 1.06f, t, p)
            drawHexagon(c, r * 0.5f, t * 20f, 1.5f, s.copy(alpha = 0.7f))
            drawHexagon(c, r * 0.36f, -t * 35f, 1.2f, p.copy(alpha = 0.5f))
            drawSatellites(c, r, tk, k, level, a, p)
        }
        if (level >= 3) {
            drawCornerBrackets(p)
            drawDataBars(t, if (busy) 3f else 1f, p, s)
        }

        // Retícula y barrido.
        drawLine(p.copy(alpha = 0.25f), Offset(c.x - r * 1.3f, c.y), Offset(c.x + r * 1.3f, c.y), 1f)
        drawLine(p.copy(alpha = 0.25f), Offset(c.x, c.y - r * 1.3f), Offset(c.x, c.y + r * 1.3f), 1f)
        if (busy) {
            val y = c.y - r + 2 * r * ((t / 2.2f) % 1f)
            drawLine(p.copy(alpha = 0.8f), Offset(c.x - r, y), Offset(c.x + r, y), 2f)
        }

        // Resultado pendiente de abrir: sello hexagonal que late y emite ondas.
        if (stage == Stage.Result) {
            val e = clock.appearK // entra con suavidad en vez de aparecer de golpe
            val pulse = 0.5f + 0.5f * sin(t * 4f)
            drawHexagon(c, r * (0.26f + 0.03f * pulse) * e, t * 45f, 3f, a.copy(alpha = e), fill = a.copy(alpha = (0.12f + 0.12f * pulse) * e))
            drawHexagon(c, r * 0.17f * e, -t * 70f, 2f, a.copy(alpha = 0.8f * e))
            val ping = (t * 0.9f) % 1f
            drawCircle(a.copy(alpha = 0.7f * (1f - ping) * e), r * (0.3f + 0.9f * ping), c, style = Stroke(2f))
        }
        // Apertura: onda expansiva y destello.
        if (open > 0f && open < 1f) {
            drawCircle(a.copy(alpha = 0.9f * (1f - open)), r * (0.2f + 1.6f * open), c, style = Stroke(6f * (1f - open) + 1f))
            drawCircle(p.copy(alpha = 0.5f * (1f - open)), r * (0.1f + 1.0f * open), c, style = Stroke(3f))
        }
        // Núcleo.
        val core = when (stage) { Stage.Ready, Stage.Revealed -> 0.55f; Stage.Result -> 0.9f; else -> 0.25f }
        drawCircle(p.copy(alpha = core), r * 0.07f, c)
    }
}

private fun DrawScope.ring(c: Offset, radius: Float, start: Float, sweep: Float, w: Float, color: Color, dash: Boolean = false) {
    drawArc(
        color = color, startAngle = start, sweepAngle = sweep, useCenter = false,
        topLeft = Offset(c.x - radius, c.y - radius), size = Size(radius * 2, radius * 2),
        style = Stroke(w, cap = StrokeCap.Round, pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(6f, 14f)) else null),
    )
}

private fun DrawScope.drawHexagon(c: Offset, radius: Float, rotDeg: Float, w: Float, color: Color, fill: Color? = null) {
    val path = Path()
    for (i in 0..5) {
        val ang = rotDeg * PI.toFloat() / 180f + i * TAU / 6f
        val x = c.x + radius * cos(ang); val y = c.y + radius * sin(ang)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    if (fill != null) drawPath(path, fill)
    drawPath(path, color, style = Stroke(w))
}

/** Escala graduada alrededor del anillo principal. */
private fun DrawScope.drawTicks(c: Offset, radius: Float, t: Float, color: Color) {
    rotate(t * 6f, c) {
        for (i in 0 until 72) {
            val ang = i * TAU / 72f
            val long = i % 6 == 0
            val len = if (long) 16f else 7f
            val x0 = c.x + radius * cos(ang); val y0 = c.y + radius * sin(ang)
            val x1 = c.x + (radius + len) * cos(ang); val y1 = c.y + (radius + len) * sin(ang)
            drawLine(color.copy(alpha = if (long) 0.7f else 0.35f), Offset(x0, y0), Offset(x1, y1), if (long) 2f else 1f)
        }
    }
}

/** Puntos que orbitan con estela. */
private fun DrawScope.drawSatellites(c: Offset, r: Float, t: Float, k: Float, level: Int, a: Color, p: Color) {
    val n = if (level >= 3) 6 else 3
    for (i in 0 until n) {
        val rad = r * (0.7f + 0.5f * hash01(i, 1))
        val sp = (0.5f + hash01(i, 2)) * (if (i % 2 == 0) 1f else -1f) * k
        for (tr in 0..8) {
            val ang = t * sp + hash01(i, 3) * TAU - tr * 0.05f * (if (sp > 0) 1 else -1)
            drawCircle((if (i % 3 == 0) a else p).copy(alpha = 0.9f * (1f - tr / 9f)), 4.5f - tr * 0.4f,
                Offset(c.x + rad * cos(ang), c.y + rad * sin(ang)))
        }
    }
}

/** Anillos enormes que se salen de la pantalla. */
private fun DrawScope.drawBigRings(c: Offset, r: Float, t: Float, k: Float, p: Color, s: Color) {
    ring(c, r * 2.2f, t * 8f * k, 80f, 2f, p.copy(alpha = 0.35f))
    ring(c, r * 2.2f, t * 8f * k + 180f, 120f, 2f, p.copy(alpha = 0.25f))
    ring(c, r * 3.1f, -t * 5f * k, 60f, 3f, s.copy(alpha = 0.3f))
    ring(c, r * 3.1f, -t * 5f * k + 140f, 90f, 1.5f, s.copy(alpha = 0.3f), dash = true)
    ring(c, r * 1.6f, t * 14f * k, 360f, 1f, p.copy(alpha = 0.18f), dash = true)
}

/** Sector de radar que barre alrededor del centro. */
private fun DrawScope.drawRadar(c: Offset, r: Float, t: Float, k: Float, p: Color) {
    rotate(t * 70f * k, c) {
        val brush = Brush.sweepGradient(
            0f to Color.Transparent, 0.82f to Color.Transparent, 1f to p.copy(alpha = 0.35f), center = c,
        )
        drawCircle(brush, r * 1.25f, c)
        drawLine(p.copy(alpha = 0.7f), c, Offset(c.x + r * 1.25f, c.y), 2f)
    }
}

private fun DrawScope.drawCornerBrackets(p: Color) {
    val m = 24f; val l = 46f; val col = p.copy(alpha = 0.6f)
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(col, Offset(x, y), Offset(x + dx * l, y), 2f)
        drawLine(col, Offset(x, y), Offset(x, y + dy * l), 2f)
    }
    corner(m, m + 60f, 1f, 1f); corner(size.width - m, m + 60f, -1f, 1f)
    corner(m, size.height - m - 60f, 1f, -1f); corner(size.width - m, size.height - m - 60f, -1f, -1f)
}

/** Barras de datos animadas en los bordes laterales. */
private fun DrawScope.drawDataBars(t: Float, speed: Float, p: Color, s: Color) {
    val n = 14
    for (i in 0 until n) {
        val y = size.height * (0.2f + 0.6f * i / n)
        val lenL = 14f + 46f * abs(sin(t * speed * (0.6f + hash01(i, 5)) + i))
        val lenR = 14f + 46f * abs(sin(t * speed * (0.6f + hash01(i, 6)) + i * 2f))
        drawLine(p.copy(alpha = 0.45f), Offset(10f, y), Offset(10f + lenL, y), 3f)
        drawLine(s.copy(alpha = 0.45f), Offset(size.width - 10f, y), Offset(size.width - 10f - lenR, y), 3f)
    }
}
