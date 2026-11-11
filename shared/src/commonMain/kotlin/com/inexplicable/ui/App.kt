package com.inexplicable.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import com.inexplicable.effect.EffectEngine
import com.inexplicable.effect.EffectParams
import com.inexplicable.effect.EffectState
import com.inexplicable.effect.Stage
import com.inexplicable.effect.Texts
import kotlinx.coroutines.delay

private val HudBg = Color(0xFF00050E)
private val HudCyan = Color(0xFF3DDCFF)
private val HudDim = Color(0xFF123B4A)

/** Escribe `text` carácter a carácter (efecto máquina de escribir). */
@Composable
private fun typed(text: String, key: Any?, msPerChar: Long = 32): String {
    var shown by remember(key, text) { mutableStateOf("") }
    LaunchedEffect(key, text) {
        for (i in 1..text.length) { shown = text.take(i); delay(msPerChar) }
    }
    return shown
}

/** Raíz Compose común (Android e iOS): modo «Lector» (Fase 1). La configuración secreta llega en la Fase 2. */
@Composable
fun InexplicableRoot(store: SettingsStore) {
    val texts = remember { Texts.forLanguage(deviceLanguage(), EffectParams()) }
    val engine = remember { EffectEngine(texts.phases) }
    var state by remember { mutableStateOf(EffectState()) }
    var resetCount by remember { mutableStateOf(0) }
    val progress = remember { Animatable(0f) }
    var connectLine by remember { mutableStateOf("") }

    fun sync() { state = engine.state }

    // Efecto de conexión con la baraja.
    LaunchedEffect(resetCount) {
        if (engine.state.stage != Stage.Connecting) return@LaunchedEffect
        for (line in texts.connecting) {
            for (i in 1..line.length) { connectLine = line.take(i); delay(28) }
            delay(650)
        }
        engine.connected(); sync()
    }
    // Animación de análisis.
    LaunchedEffect(state, resetCount) {
        if (state.stage == Stage.Analyzing) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(2600, easing = LinearEasing))
            engine.revealed(); sync()
        }
    }

    SystemBarsEffect()
    MaterialTheme {
        Box(Modifier.fillMaxSize().background(HudBg)) {
            HudRings(state.stage, progress.value)

            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(texts.title, color = HudCyan, fontSize = 18.sp, letterSpacing = 8.sp, fontWeight = FontWeight.Light)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val message = when (state.stage) {
                        Stage.Connecting -> connectLine
                        Stage.Ready -> texts.ready
                        Stage.Analyzing -> texts.analyzing + " " + (progress.value * 100).toInt() + "%"
                        Stage.Revealed -> typed(texts.phases[state.phase].text, state)
                        Stage.Finished -> texts.finished
                    }
                    Text(
                        message,
                        color = Color.White,
                        fontSize = if (state.stage == Stage.Revealed) 26.sp else 16.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = if (state.stage == Stage.Revealed) 36.sp else 22.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 32.dp),
                    )
                }
                val label = if (state.stage == Stage.Revealed) texts.nextButton else texts.analyzeButton
                val active = state.stage == Stage.Ready || state.stage == Stage.Revealed
                if (state.stage != Stage.Finished) {
                    HudButton(label, active) {
                        when (state.stage) {
                            Stage.Ready -> engine.analyze()
                            Stage.Revealed -> engine.next()
                            else -> {}
                        }
                        sync()
                    }
                }
            }

            // Zona oculta: pulsación larga arriba a la izquierda = reset. (Arriba a la derecha irá la configuración, Fase 2.)
            Box(
                Modifier.align(Alignment.TopStart).size(88.dp).pointerInput(Unit) {
                    detectTapGestures(onLongPress = { engine.reset(); resetCount++; connectLine = ""; sync() })
                },
            )
        }
    }
}

@Composable
private fun HudButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val glow by rememberInfiniteTransition().animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse),
    )
    val c = if (enabled) HudCyan else HudDim
    Box(
        Modifier
            .alpha(if (enabled) glow else 0.6f)
            .border(1.5.dp, c, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 44.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = c, fontSize = 18.sp, letterSpacing = 6.sp, fontWeight = FontWeight.Medium)
    }
}

/** Anillos giratorios y línea de barrido tipo HUD. Su intensidad depende de la etapa. */
@Composable
private fun HudRings(stage: Stage, progress: Float) {
    val t = rememberInfiniteTransition()
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)))
    val spinFast by t.animateFloat(360f, 0f, infiniteRepeatable(tween(5000, easing = LinearEasing)))
    val sweep by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)))
    val speedUp = stage == Stage.Analyzing || stage == Stage.Connecting

    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width / 2, size.height * 0.36f)
        val r = size.minDimension * 0.34f
        fun ring(radius: Float, start: Float, sweepDeg: Float, w: Float, alpha: Float, dash: Boolean = false) {
            drawArc(
                color = HudCyan.copy(alpha = alpha),
                startAngle = start, sweepAngle = sweepDeg, useCenter = false,
                topLeft = Offset(c.x - radius, c.y - radius), size = Size(radius * 2, radius * 2),
                style = Stroke(
                    width = w, cap = StrokeCap.Round,
                    pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(6f, 14f)) else null,
                ),
            )
        }
        val k = if (speedUp) 2f else 1f
        ring(r, spin * k, 100f, 3f, 0.9f)
        ring(r, spin * k + 180f, 70f, 3f, 0.6f)
        ring(r * 0.82f, spinFast * k, 220f, 2f, 0.7f)
        ring(r * 0.64f, spin * 2f, 360f, 1.5f, 0.45f, dash = true)
        ring(r * 1.12f, 0f, 360f, 1f, 0.2f)
        // Anillo de progreso durante el análisis.
        if (stage == Stage.Analyzing) ring(r * 1.2f, -90f, 360f * progress, 4f, 1f)
        // Retícula y línea de barrido.
        drawLine(HudCyan.copy(alpha = 0.25f), Offset(c.x - r * 1.3f, c.y), Offset(c.x + r * 1.3f, c.y), 1f)
        drawLine(HudCyan.copy(alpha = 0.25f), Offset(c.x, c.y - r * 1.3f), Offset(c.x, c.y + r * 1.3f), 1f)
        if (speedUp) {
            val y = c.y - r + 2 * r * sweep
            drawLine(HudCyan.copy(alpha = 0.8f), Offset(c.x - r, y), Offset(c.x + r, y), 2f)
        }
        // Núcleo.
        drawCircle(HudCyan.copy(alpha = if (stage == Stage.Ready || stage == Stage.Revealed) 0.55f else 0.25f), r * 0.08f, c)
    }
}
