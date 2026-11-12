package com.inexplicable.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inexplicable.effect.Stage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val DECK_W = 6.3f   // cm
private const val DECK_D = 8.8f
private const val DECK_H = 2.4f
private const val CUBE_Y = DECK_H + 9f      // altura del «resultado» flotante
private const val BUBBLE_Y = DECK_H + 14f   // altura de la burbuja con la revelación
private const val TAU = (2 * PI).toFloat()

/**
 * Mesa con una baraja encima y los efectos de RA dibujados con proyección propia (todo rodea o está sobre la baraja;
 * nada la atraviesa). Arrastrar gira la cámara, pellizcar acerca o aleja.
 *
 * - `Analyzing`: partículas que orbitan y se cierran sobre la baraja, anillos acelerados y anillo de progreso.
 * - `Result`: un cubo luminoso flota sobre la baraja; al tocarlo (`onOpen`) se abre.
 * - `Revealed`: `open` (0..1) anima la apertura y después aparece la burbuja con `bubble`.
 * `level` (1..3) escala la cantidad de partículas y detalles.
 */
@Composable
fun DeckScene(
    stage: Stage, progress: Float, open: Float, level: Int,
    bubble: String?, tapLabel: String, time: State<Float>, onOpen: () -> Unit,
) {
    val pal = LocalPalette.current
    var yaw by remember { mutableStateOf(20f) }
    var pitch by remember { mutableStateOf(42f) }
    var dist by remember { mutableStateOf(30f) }
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    val cam = OrbitCamera(yaw, pitch, dist, size.width.toFloat(), size.height.toFloat())

    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    yaw -= pan.x * 0.4f
                    pitch = (pitch + pan.y * 0.3f).coerceIn(8f, 88f)
                    dist = (dist / zoom).coerceIn(14f, 70f)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            drawRect(Color(0xFF00050E))
            drawTable(cam, pal.primary)
            drawFootprint(cam, stage, pal.primary)
            drawDeck(cam)
            drawEffects(cam, stage, progress, open, level, t, pal)
        }

        val cubeP = cam.project(V3(0f, CUBE_Y, 0f))
        // Resultado pulsable: zona táctil sobre el cubo y rótulo.
        if (stage == Stage.Result && cubeP != null) {
            val tapSize = 120
            Box(
                Modifier
                    .offset { IntOffset(cubeP.x.toInt() - size.width / 2, cubeP.y.toInt() - size.height / 2) }
                    .align(Alignment.Center)
                    .size(tapSize.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen),
            )
            Text(
                tapLabel, color = pal.accent, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .offset { IntOffset(cubeP.x.toInt() - size.width / 2, cubeP.y.toInt() - size.height / 2 + 150) }
                    .align(Alignment.Center),
            )
        }
        // Burbuja de la revelación: crece al abrirse y mira siempre a la pantalla.
        val anchor = cam.project(V3(0f, BUBBLE_Y, 0f))
        if (bubble != null && anchor != null && open > 0.45f) {
            val grow = ((open - 0.45f) / 0.55f).coerceIn(0f, 1f)
            Box(
                Modifier
                    .offset { IntOffset(anchor.x.toInt() - size.width / 2, anchor.y.toInt() - size.height / 2) }
                    .align(Alignment.Center)
                    .scale(0.4f + 0.6f * grow)
                    .alpha(grow),
            ) {
                Text(
                    bubble,
                    color = Color.White, fontSize = 18.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .background(Color(0xCC00131F), RoundedCornerShape(10.dp))
                        .border(1.5.dp, pal.primary, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

// ---------- Dibujo ----------

private fun DrawScope.line(cam: OrbitCamera, a: V3, b: V3, color: Color, width: Float) {
    val pa = cam.project(a) ?: return
    val pb = cam.project(b) ?: return
    drawLine(color, Offset(pa.x, pa.y), Offset(pb.x, pb.y), width, StrokeCap.Round)
}

private fun DrawScope.quad(cam: OrbitCamera, pts: List<V3>, fill: Color?, stroke: Color? = null, w: Float = 1.5f) {
    val ps = pts.map { cam.project(it) ?: return }
    val path = Path().apply {
        moveTo(ps[0].x, ps[0].y)
        for (i in 1 until ps.size) lineTo(ps[i].x, ps[i].y)
        close()
    }
    if (fill != null) drawPath(path, fill)
    if (stroke != null) drawPath(path, stroke, style = Stroke(w))
}

private fun DrawScope.drawTable(cam: OrbitCamera, grid: Color) {
    val s = 70f
    quad(cam, listOf(V3(-s, 0f, -s), V3(s, 0f, -s), V3(s, 0f, s), V3(-s, 0f, s)), Color(0xFF0A1B26))
    var g = -40f
    while (g <= 40f) {
        val a = if (g == 0f) 0.28f else 0.12f
        line(cam, V3(g, 0f, -40f), V3(g, 0f, 40f), grid.copy(alpha = a), 1f)
        line(cam, V3(-40f, 0f, g), V3(40f, 0f, g), grid.copy(alpha = a), 1f)
        g += 5f
    }
}

private fun DrawScope.drawFootprint(cam: OrbitCamera, stage: Stage, c: Color) {
    val m = 1.2f
    val a = if (stage == Stage.Connecting) 0.1f else 0.2f
    val w = DECK_W / 2 + m; val d = DECK_D / 2 + m
    quad(cam, listOf(V3(-w, 0.05f, -d), V3(w, 0.05f, -d), V3(w, 0.05f, d), V3(-w, 0.05f, d)), c.copy(alpha = a), c.copy(alpha = 0.8f))
}

private fun DrawScope.drawDeck(cam: OrbitCamera) {
    val w = DECK_W / 2; val d = DECK_D / 2; val h = DECK_H
    val sides = listOf(
        listOf(V3(-w, 0f, d), V3(w, 0f, d), V3(w, h, d), V3(-w, h, d)),
        listOf(V3(-w, 0f, -d), V3(w, 0f, -d), V3(w, h, -d), V3(-w, h, -d)),
        listOf(V3(w, 0f, -d), V3(w, 0f, d), V3(w, h, d), V3(w, h, -d)),
        listOf(V3(-w, 0f, -d), V3(-w, 0f, d), V3(-w, h, d), V3(-w, h, -d)),
    )
    fun depth(face: List<V3>) = face.mapNotNull { cam.project(it)?.depth }.average().toFloat()
    for (face in sides.sortedByDescending { depth(it) }) {
        var n = (face[1] - face[0]).cross(face[3] - face[0]).norm()
        val center = (face[0] + face[2]) * 0.5f
        if (n.dot(center - V3(0f, h / 2, 0f)) < 0f) n = n * -1f // normal hacia fuera
        if (n.dot((cam.position - face[0]).norm()) <= 0f) continue // cara de atrás
        val shade = 0.78f + 0.2f * abs(n.x)
        quad(cam, face, Color(0.93f * shade, 0.91f * shade, 0.85f * shade), Color(0xFF9A9684), 1f)
    }
    quad(cam, listOf(V3(-w, h, -d), V3(w, h, -d), V3(w, h, d), V3(-w, h, d)), Color(0xFF7A1020), Color(0xFFEDE8DA), 2f)
    val i = 0.55f
    quad(cam, listOf(V3(-w + i, h, -d + i), V3(w - i, h, -d + i), V3(w - i, h, d - i), V3(-w + i, h, d - i)), null, Color(0xFFE6B3BC), 1f)
    quad(cam, listOf(V3(0f, h, -d + 1.2f), V3(w - 1.2f, h, 0f), V3(0f, h, d - 1.2f), V3(-w + 1.2f, h, 0f)), null, Color(0xFFE6B3BC), 1f)
}

/** Arco horizontal (plano XZ) a altura `y`. */
private fun DrawScope.drawRing(cam: OrbitCamera, r: Float, y: Float, startDeg: Float, sweepDeg: Float, color: Color, width: Float,
                               cx: Float = 0f, cz: Float = 0f) {
    val steps = maxOf(6, (sweepDeg / 6f).toInt())
    var prev: P2? = null
    for (i in 0..steps) {
        val a = (startDeg + sweepDeg * i / steps) * PI.toFloat() / 180f
        val p = cam.project(V3(cx + r * cos(a), y, cz + r * sin(a)))
        if (prev != null && p != null) drawLine(color, Offset(prev.x, prev.y), Offset(p.x, p.y), width, StrokeCap.Round)
        prev = p
    }
}

private fun DrawScope.drawEffects(cam: OrbitCamera, stage: Stage, progress: Float, open: Float, level: Int, t: Float, pal: Palette) {
    val busy = stage == Stage.Analyzing || stage == Stage.Connecting
    val k = if (busy) 2.2f else 1f
    val p = pal.primary; val s = pal.secondary; val a = pal.accent

    // Anillos que rodean la baraja (nunca la atraviesan).
    drawRing(cam, 9.5f, 0.2f, t * 40f * k, 100f, p.copy(alpha = 0.9f), 3f)
    drawRing(cam, 9.5f, 0.2f, t * 40f * k + 180f, 70f, p.copy(alpha = 0.6f), 3f)
    drawRing(cam, 7f, DECK_H + 2.5f, -t * 100f * k, 220f, s.copy(alpha = 0.8f), 2.5f)
    drawRing(cam, 5f, DECK_H + 5f, t * 60f, 120f, p.copy(alpha = 0.5f), 2f)
    if (level >= 2) {
        drawRing(cam, 12.5f, 0.15f, -t * 25f, 300f, s.copy(alpha = 0.35f), 1.5f)
        drawRing(cam, 8.2f, DECK_H + 7f, t * 80f * k, 160f, a.copy(alpha = 0.5f), 1.5f)
    }
    if (level >= 3) {
        drawRing(cam, 15f, 0.1f, t * 15f, 200f, p.copy(alpha = 0.25f), 1.5f)
        drawRing(cam, 10f, DECK_H + 10f, -t * 50f * k, 90f, s.copy(alpha = 0.4f), 1.5f)
    }
    if (stage == Stage.Analyzing) drawRing(cam, 11.5f, 0.2f, -90f, 360f * progress, a, 4f)

    // Partículas que orbitan alrededor de la baraja.
    val total = when (level) { 1 -> 30; 2 -> 70; else -> 130 }
    val visible = when (stage) {
        Stage.Analyzing, Stage.Result -> total
        Stage.Revealed -> (total * (0.3f + 0.7f * (1f - open))).toInt().coerceAtLeast(total / 4)
        else -> total / 3
    }
    val boost = if (stage == Stage.Analyzing) 2.5f else if (stage == Stage.Result) 1.5f else 0.5f
    for (i in 0 until visible) {
        val r0 = 6f + 8f * hash01(i, 1)
        val y0 = 0.6f + 11f * hash01(i, 2)
        val sp = (0.4f + 0.9f * hash01(i, 3)) * (if (i % 2 == 0) 1f else -1f)
        val ph = TAU * hash01(i, 4)
        val col = when (i % 3) { 0 -> p; 1 -> s; else -> a }
        for (tr in 0..3) {
            val ang = t * sp * boost + ph - tr * 0.07f * (if (sp > 0) 1 else -1)
            var px: Float; var py: Float; var pz: Float
            if (stage == Stage.Result) {
                // Se reúnen alrededor del cubo flotante.
                val rr = 2.6f + 3.4f * hash01(i, 5)
                px = rr * cos(ang); pz = rr * sin(ang); py = CUBE_Y + 2.2f * sin(ang * 1.7f + ph) * hash01(i, 6)
            } else {
                val squeeze = if (stage == Stage.Analyzing) 1f - 0.45f * sin(PI.toFloat() * progress) else 1f
                px = r0 * squeeze * cos(ang); pz = r0 * squeeze * sin(ang); py = (y0 + 0.6f * sin(t + ph)) * (if (stage == Stage.Analyzing) squeeze else 1f)
            }
            if (py < DECK_H + 0.3f && abs(px) < DECK_W / 2 + 0.5f && abs(pz) < DECK_D / 2 + 0.5f) py = DECK_H + 0.3f // nunca dentro de la baraja
            val pr = cam.project(V3(px, py, pz)) ?: continue
            val rad = (0.11f * cam.focal / pr.depth * (1f - tr * 0.18f)).coerceIn(1f, 6f)
            drawCircle(col.copy(alpha = (0.85f - tr * 0.2f) * (if (stage == Stage.Revealed) (1f - 0.5f * open) else 1f)), rad, Offset(pr.x, pr.y))
        }
    }

    // Resultado flotante: cubo de alambre con haz de luz hasta la baraja.
    if (stage == Stage.Result || (stage == Stage.Revealed && open < 1f)) {
        val grow = if (stage == Stage.Revealed) 1f + 1.6f * open else 1f
        val fade = if (stage == Stage.Revealed) (1f - open) else 1f
        val bob = if (stage == Stage.Result) 0.5f * sin(t * 2.2f) else 0f
        val c = V3(0f, CUBE_Y + bob, 0f)
        val half = 2.1f * grow
        val ang = t * 0.9f
        fun rot(x: Float, z: Float) = V3(c.x + x * cos(ang) - z * sin(ang), 0f, c.z + x * sin(ang) + z * cos(ang))
        val corners = ArrayList<V3>()
        for (iy in listOf(-1f, 1f)) for (iz in listOf(-1f, 1f)) for (ix in listOf(-1f, 1f)) {
            val r = rot(ix * half, iz * half); corners.add(V3(r.x, c.y + iy * half, r.z))
        }
        val edges = listOf(0 to 1, 2 to 3, 4 to 5, 6 to 7, 0 to 2, 1 to 3, 4 to 6, 5 to 7, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
        val glow = 0.7f + 0.3f * sin(t * 5f)
        for ((i, j) in edges) line(cam, corners[i], corners[j], a.copy(alpha = fade * glow), 2.5f)
        // Haz de luz que sube desde la superficie de la baraja hasta el cubo.
        line(cam, V3(0f, DECK_H + 0.2f, 0f), V3(0f, c.y - half, 0f), a.copy(alpha = 0.35f * fade), 2f)
        cam.project(c)?.let { drawCircle(a.copy(alpha = 0.25f * fade * glow), 0.9f * cam.focal / it.depth * grow * 2f, Offset(it.x, it.y)) }
    }
    // Apertura: onda expansiva a la altura del cubo y sobre la mesa.
    if (stage == Stage.Revealed && open < 1f) {
        drawRing(cam, 2f + 16f * open, CUBE_Y, 0f, 360f, a.copy(alpha = 0.9f * (1f - open)), 4f * (1f - open) + 1f)
        drawRing(cam, 3f + 20f * open, 0.2f, 0f, 360f, p.copy(alpha = 0.8f * (1f - open)), 3f)
    }
}
