package com.inexplicable.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.ui.graphics.drawscope.withTransform
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

private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k
private fun smooth01(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

/**
 * Estado de movimiento de la escena. Todo cambia de forma continua (nada salta al cambiar de etapa):
 * - `clock.phase`: fase de los anillos (su velocidad cambia con suavidad).
 * - `orbitT`: fase de las partículas (idem, con su propia velocidad).
 * - `gather` 0..1: cuánto se han reunido las partículas alrededor del cubo de resultado. Empieza a subir en el último tramo del
 *   análisis, así que llegan a esa zona subiendo desde donde estén, y se deshace despacio al abrir el resultado.
 * - `activity` 0..1: cuántas partículas se ven (entran y salen de una en una).
 */
internal class SceneAnim {
    val clock = MotionClock()
    var orbitT = 0f
    var gather = 0f
    var activity = 0.33f
    private var boost = 0.5f
    private var last = 0f

    fun step(t: Float, stage: Stage, progress: Float) {
        val dt = (t - last).coerceIn(0f, 0.1f)
        last = t
        val fast = stage == Stage.Analyzing || stage == Stage.Connecting
        clock.step(t, fast)
        val boostTarget = when (stage) { Stage.Analyzing -> 2.5f; Stage.Result -> 1.5f; Stage.Revealed -> 0.8f; else -> 0.5f }
        boost += (boostTarget - boost) * minOf(1f, dt * 2.5f)
        orbitT += dt * boost
        val gatherTarget = when (stage) {
            Stage.Analyzing -> smooth01((progress - 0.6f) / 0.4f) // empieza a subir en el último tramo del análisis
            Stage.Result -> 1f
            else -> 0f
        }
        gather += (gatherTarget - gather) * minOf(1f, dt * if (stage == Stage.Analyzing) 6f else 2.5f)
        val activityTarget = when (stage) { Stage.Analyzing, Stage.Result -> 1f; Stage.Revealed -> 0.6f; else -> 0.33f }
        activity += (activityTarget - activity) * minOf(1f, dt * 1.5f)
    }
}

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
    bubble: String?, tapLabel: String, time: State<Float>, speech: SpeechFeedback, onOpen: () -> Unit,
    backImage: ImageBitmap? = null,
) {
    val pal = LocalPalette.current
    var yaw by remember { mutableStateOf(20f) }
    var pitch by remember { mutableStateOf(42f) }
    var dist by remember { mutableStateOf(30f) }
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    val anim = remember { SceneAnim() }
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
            drawDeck(cam, backImage)
            speech.step(t); anim.step(t, stage, progress)
            drawEffects(cam, stage, progress, open, level, t, pal, anim, speech)
        }

        SceneOverlays(cam, size, stage, open, bubble, tapLabel, onOpen)
    }
}

/** Resultado pulsable (zona táctil sobre el cubo + rótulo) y burbuja de la revelación, colocados con la proyección de `cam`. */
@Composable
private fun BoxScope.SceneOverlays(
    cam: SceneCamera, size: IntSize, stage: Stage, open: Float, bubble: String?, tapLabel: String, onOpen: () -> Unit,
) {
    val pal = LocalPalette.current
    val cubeP = cam.project(V3(0f, CUBE_Y, 0f))
    if (stage == Stage.Result && cubeP != null) {
        Box(
            Modifier
                .offset { IntOffset(cubeP.x.toInt() - size.width / 2, cubeP.y.toInt() - size.height / 2) }
                .align(Alignment.Center)
                .size(120.dp)
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

/** Dónde queda la foto en pantalla (se ve entera, centrada): escala y desplazamiento. */
internal class PhotoGeometry(boxW: Float, boxH: Float, photoW: Int, photoH: Int) {
    val scale = minOf(boxW / photoW, boxH / photoH)
    val w = photoW * scale
    val h = photoH * scale
    val ox = (boxW - w) / 2
    val oy = (boxH - h) / 2
}

/** Suma un desplazamiento a otra cámara (para dibujar sobre la foto, que no ocupa toda la pantalla). */
private class OffsetCamera(private val inner: SceneCamera, private val dx: Float, private val dy: Float) : SceneCamera {
    override val position get() = inner.position
    override val focal get() = inner.focal
    override fun project(p: V3): P2? = inner.project(p)?.let { P2(it.x + dx, it.y + dy, it.depth) }
}

/**
 * Modo Foto: los mismos efectos de la escena dibujados sobre una foto de la baraja. `corners` son las 4 esquinas de la baraja en la foto
 * (0..1, orden: arriba-izq, arriba-der, abajo-der, abajo-izq); con ellas y las medidas reales se calcula desde dónde se tomó la foto.
 */
@Composable
fun PhotoScene(
    photo: ImageBitmap, corners: List<Offset>,
    stage: Stage, progress: Float, open: Float, level: Int,
    bubble: String?, tapLabel: String, time: State<Float>, speech: SpeechFeedback, onOpen: () -> Unit,
) {
    val pal = LocalPalette.current
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    val anim = remember { SceneAnim() }
    val geo = PhotoGeometry(size.width.toFloat(), size.height.toFloat(), photo.width, photo.height)
    val local = corners.map { Offset(it.x * geo.w, it.y * geo.h) }
    val cam: SceneCamera? = PhotoCamera.fromCorners(local, DECK_W, DECK_D, geo.w, geo.h)?.let { OffsetCamera(it, geo.ox, geo.oy) }

    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            drawRect(Color.Black)
            drawImage(photo, dstOffset = IntOffset(geo.ox.toInt(), geo.oy.toInt()), dstSize = IntSize(geo.w.toInt(), geo.h.toInt()))
            speech.step(t); anim.step(t, stage, progress)
            if (cam != null) {
                drawFootprint(cam, stage, pal.primary)
                drawEffects(cam, stage, progress, open, level, t, pal, anim, speech)
            }
        }
        if (cam != null) SceneOverlays(cam, size, stage, open, bubble, tapLabel, onOpen)
    }
}

/**
 * Ajuste manual de las 4 esquinas de la baraja sobre la foto: se arrastra cada esquina. `corners` va en 0..1 sobre la foto.
 */
@Composable
fun PhotoAdjust(photo: ImageBitmap, corners: List<Offset>, onChange: (List<Offset>) -> Unit) {
    val pal = LocalPalette.current
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    val geo = PhotoGeometry(size.width.toFloat(), size.height.toFloat(), photo.width, photo.height)
    val current by rememberUpdatedState(corners)
    val onChangeState by rememberUpdatedState(onChange)
    val geoState by rememberUpdatedState(geo)
    var active by remember { mutableStateOf(-1) }

    Canvas(
        Modifier.fillMaxSize().onSizeChanged { size = it }.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { pos ->
                    val g = geoState
                    var best = -1; var bestD = 110f
                    current.forEachIndexed { i, c ->
                        val d = (Offset(g.ox + c.x * g.w, g.oy + c.y * g.h) - pos).getDistance()
                        if (d < bestD) { bestD = d; best = i }
                    }
                    active = best
                },
                onDragEnd = { active = -1 },
                onDragCancel = { active = -1 },
            ) { pointer, drag ->
                val i = active
                if (i >= 0) {
                    val g = geoState
                    val c = current[i]
                    val moved = Offset((c.x + drag.x / g.w).coerceIn(0f, 1f), (c.y + drag.y / g.h).coerceIn(0f, 1f))
                    pointer.consume()
                    val list = current.toMutableList(); list[i] = moved
                    onChangeState(list)
                }
            }
        },
    ) {
        drawRect(Color.Black)
        drawImage(photo, dstOffset = IntOffset(geo.ox.toInt(), geo.oy.toInt()), dstSize = IntSize(geo.w.toInt(), geo.h.toInt()))
        val pts = corners.map { Offset(geo.ox + it.x * geo.w, geo.oy + it.y * geo.h) }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y); for (i in 1 until 4) lineTo(pts[i].x, pts[i].y); close()
        }
        drawPath(path, pal.primary.copy(alpha = 0.12f))
        drawPath(path, pal.primary, style = Stroke(3f))
        pts.forEachIndexed { i, p ->
            drawCircle(if (i == active) pal.accent else pal.primary, 22f, p, style = Stroke(4f))
            drawCircle((if (i == active) pal.accent else pal.primary).copy(alpha = 0.25f), 22f, p)
        }
        // Marca la esquina «arriba-izquierda» (el lado corto de arriba) para saber cómo está girada la selección.
        drawCircle(pal.accent, 7f, pts[0])
    }
}

// ---------- Dibujo ----------

private fun DrawScope.line(cam: SceneCamera, a: V3, b: V3, color: Color, width: Float) {
    val pa = cam.project(a) ?: return
    val pb = cam.project(b) ?: return
    drawLine(color, Offset(pa.x, pa.y), Offset(pb.x, pb.y), width, StrokeCap.Round)
}

private fun DrawScope.quad(cam: SceneCamera, pts: List<V3>, fill: Color?, stroke: Color? = null, w: Float = 1.5f) {
    val ps = pts.map { cam.project(it) ?: return }
    val path = Path().apply {
        moveTo(ps[0].x, ps[0].y)
        for (i in 1 until ps.size) lineTo(ps[i].x, ps[i].y)
        close()
    }
    if (fill != null) drawPath(path, fill)
    if (stroke != null) drawPath(path, stroke, style = Stroke(w))
}

private fun DrawScope.drawTable(cam: SceneCamera, grid: Color) {
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

private fun DrawScope.drawFootprint(cam: SceneCamera, stage: Stage, c: Color) {
    val m = 1.2f
    val a = if (stage == Stage.Connecting) 0.1f else 0.2f
    val w = DECK_W / 2 + m; val d = DECK_D / 2 + m
    quad(cam, listOf(V3(-w, 0.05f, -d), V3(w, 0.05f, -d), V3(w, 0.05f, d), V3(-w, 0.05f, d)), c.copy(alpha = a), c.copy(alpha = 0.8f))
}

private fun DrawScope.drawDeck(cam: SceneCamera, back: ImageBitmap?) {
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
    val top = listOf(V3(-w, h, -d), V3(w, h, -d), V3(w, h, d), V3(-w, h, d))
    if (back != null) {
        // Dorso con la foto del usuario: se deforma la imagen (perspectiva) sobre la cara superior.
        val ps = top.map { cam.project(it) }
        if (ps.all { it != null }) {
            val dst = ps.map { Offset(it!!.x, it.y) }
            val bw = back.width.toFloat(); val bh = back.height.toFloat()
            val hom = homography(listOf(Offset(0f, 0f), Offset(bw, 0f), Offset(bw, bh), Offset(0f, bh)), dst)
            if (hom != null) withTransform({ transform(homographyToMatrix(hom)) }) { drawImage(back) }
            quad(cam, top, null, Color(0xFFEDE8DA), 2f)
            return
        }
    }
    quad(cam, top, Color(0xFF7A1020), Color(0xFFEDE8DA), 2f)
    val i = 0.55f
    quad(cam, listOf(V3(-w + i, h, -d + i), V3(w - i, h, -d + i), V3(w - i, h, d - i), V3(-w + i, h, d - i)), null, Color(0xFFE6B3BC), 1f)
    quad(cam, listOf(V3(0f, h, -d + 1.2f), V3(w - 1.2f, h, 0f), V3(0f, h, d - 1.2f), V3(-w + 1.2f, h, 0f)), null, Color(0xFFE6B3BC), 1f)
}

/** Arco horizontal (plano XZ) a altura `y`. */
private fun DrawScope.drawRing(cam: SceneCamera, r: Float, y: Float, startDeg: Float, sweepDeg: Float, color: Color, width: Float,
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

private fun DrawScope.drawEffects(cam: SceneCamera, stage: Stage, progress: Float, open: Float, level: Int, t: Float, pal: Palette,
                                  anim: SceneAnim, speech: SpeechFeedback) {
    val ph = anim.clock.phase // tiempo «acelerado» acumulado: la velocidad cambia con suavidad
    val p = pal.primary; val s = pal.secondary; val a = pal.accent
    val sk = speech.kick   // impulso de la última palabra (0..1)
    val sl = speech.level  // intensidad mientras se habla (0..1)

    // Anillos que rodean la baraja (nunca la atraviesan). Con la voz se hinchan un poco y se avivan a cada palabra.
    drawRing(cam, 9.5f * (1f + 0.04f * sk), 0.2f, ph * 40f, 100f, p.copy(alpha = (0.9f + 0.1f * sk).coerceAtMost(1f)), 3f + 2f * sk)
    drawRing(cam, 9.5f * (1f + 0.04f * sk), 0.2f, ph * 40f + 180f, 70f, p.copy(alpha = 0.6f + 0.3f * sk), 3f + 2f * sk)
    drawRing(cam, 7f * (1f + 0.05f * sk), DECK_H + 2.5f, -ph * 100f, 220f, s.copy(alpha = 0.8f), 2.5f + 1.5f * sk)
    // Voz: onda sobre la mesa por palabra y visualizador de barras alrededor de la baraja (siempre fuera de su huella).
    if (sk > 0.02f) drawRing(cam, 6f + 10f * (1f - sk), 0.25f, 0f, 360f, a.copy(alpha = 0.75f * sk), 3f)
    if (sl > 0.02f) {
        for (i in 0 until 40) {
            val ang = i * TAU / 40f
            val h = 0.3f + 2.6f * sl * abs(sin(t * 9f + i * 1.7f) * 0.6f + sin(t * 5.3f + i * 0.9f) * 0.4f)
            val bx = 8f * cos(ang); val bz = 8f * sin(ang)
            line(cam, V3(bx, 0.3f, bz), V3(bx, 0.3f + h, bz), (if (i % 3 == 0) a else p).copy(alpha = 0.4f + 0.55f * sl), 3f)
        }
    }
    drawRing(cam, 5f, DECK_H + 5f, t * 60f, 120f, p.copy(alpha = 0.5f), 2f)
    if (level >= 2) {
        drawRing(cam, 12.5f, 0.15f, -t * 25f, 300f, s.copy(alpha = 0.35f), 1.5f)
        drawRing(cam, 8.2f, DECK_H + 7f, ph * 80f, 160f, a.copy(alpha = 0.5f), 1.5f)
    }
    if (level >= 3) {
        drawRing(cam, 15f, 0.1f, t * 15f, 200f, p.copy(alpha = 0.25f), 1.5f)
        drawRing(cam, 10f, DECK_H + 10f, -ph * 50f, 90f, s.copy(alpha = 0.4f), 1.5f)
    }
    if (stage == Stage.Analyzing) drawRing(cam, 11.5f, 0.2f, -90f, 360f * progress, a, 4f)

    // Partículas que orbitan alrededor de la baraja. La posición mezcla de forma continua «órbita libre» y «reunidas junto al cubo»
    // según `anim.gather`, y cuántas se ven según `anim.activity`: nada salta al cambiar de etapa.
    val total = when (level) { 1 -> 30; 2 -> 70; else -> 130 }
    val visible = (total * anim.activity).toInt().coerceIn(1, total)
    val g = anim.gather
    val squeeze = if (stage == Stage.Analyzing) 1f - 0.45f * sin(PI.toFloat() * progress) else 1f
    for (i in 0 until visible) {
        val r0 = 6f + 8f * hash01(i, 1)
        val y0 = 0.6f + 11f * hash01(i, 2)
        val sp = (0.4f + 0.9f * hash01(i, 3)) * (if (i % 2 == 0) 1f else -1f)
        val phase = TAU * hash01(i, 4)
        val col = when (i % 3) { 0 -> p; 1 -> s; else -> a }
        for (tr in 0..3) {
            val ang = anim.orbitT * sp + phase - tr * 0.07f * (if (sp > 0) 1 else -1) // mismo ángulo en ambas posiciones
            val rFree = r0 * squeeze
            val yFree = (y0 + 0.6f * sin(t + phase)) * squeeze
            val rGather = 2.6f + 3.4f * hash01(i, 5)
            val yGather = CUBE_Y + 2.2f * sin(ang * 1.7f + phase) * hash01(i, 6)
            val rr = lerp(rFree, rGather, g) * (1f + 0.07f * sk) // con la voz las partículas «respiran» hacia fuera
            val px = rr * cos(ang); val pz = rr * sin(ang)
            var py = lerp(yFree, yGather, g)
            if (py < DECK_H + 0.3f && abs(px) < DECK_W / 2 + 0.5f && abs(pz) < DECK_D / 2 + 0.5f) py = DECK_H + 0.3f // nunca dentro de la baraja
            val pr = cam.project(V3(px, py, pz)) ?: continue
            val rad = (0.11f * cam.focal / pr.depth * (1f - tr * 0.18f) * (1f + 0.8f * sk)).coerceIn(1f, 8f)
            drawCircle(col.copy(alpha = (0.85f - tr * 0.2f) * (if (stage == Stage.Revealed) (1f - 0.5f * open) else 1f)), rad, Offset(pr.x, pr.y))
        }
    }

    // Resultado flotante: cubo de alambre con haz de luz hasta la baraja. Se materializa mientras las partículas suben (`gather`).
    val cubeAlpha = if (stage == Stage.Revealed) (1f - open) else g
    if (cubeAlpha > 0.01f) {
        val grow = if (stage == Stage.Revealed) 1f + 1.6f * open else 0.5f + 0.5f * g
        val fade = cubeAlpha
        val bob = 0.5f * sin(t * 2.2f)
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
