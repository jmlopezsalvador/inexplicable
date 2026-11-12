package com.inexplicable.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inexplicable.effect.EffectEngine
import com.inexplicable.effect.Stage
import com.inexplicable.effect.Texts
import kotlinx.coroutines.delay

private val HudBg = Color(0xFF00050E)

/** Escribe `text` carácter a carácter (efecto máquina de escribir). */
@Composable
private fun typed(text: String, key: Any?, msPerChar: Long = 32): String {
    var shown by remember(key, text) { mutableStateOf("") }
    LaunchedEffect(key, text) {
        for (i in 1..text.length) { shown = text.take(i); delay(msPerChar) }
    }
    return shown
}

@Composable
private fun InexplicableTheme(palette: Palette, content: @Composable () -> Unit) {
    WithPalette(palette) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = palette.primary, onPrimary = HudBg, background = HudBg, surface = HudBg, onSurface = Color.White,
                onBackground = Color.White, outline = palette.primary.copy(alpha = 0.3f), secondary = palette.primary,
            ),
            content = content,
        )
    }
}

/** Solo la pantalla de configuración (iOS la muestra encima de la RA nativa). Guarda y llama a `onClose`. */
@Composable
fun ConfigHost(store: SettingsStore, arAvailable: Boolean, onClose: () -> Unit) {
    val lang = remember { deviceLanguage() }
    val initial = remember { store.load() }
    InexplicableTheme(initial.palette()) {
        ConfigScreen(lang, initial, arAvailable, onSave = { store.save(it); onClose() }, onCancel = onClose)
    }
}

/**
 * Raíz Compose común (Android e iOS). Arranca en el menú principal (anillos + EMPEZAR); «Empezar» inicia un juego nuevo
 * en el modo elegido en la configuración: HUD plano, baraja 3D (mesa virtual) o RA (nativa en iOS, vía `onStartAR`).
 * Pulsación larga arriba a la izquierda = reset (vuelve al menú); arriba a la derecha = configuración.
 *
 * Cada fase: Analizar → animación → aparece un resultado que se pulsa → se abre → se lee la predicción → Continuar.
 */
@Composable
fun InexplicableRoot(store: SettingsStore, onStartAR: (() -> Unit)? = null) {
    val lang = remember { deviceLanguage() }
    var settings by remember { mutableStateOf(store.load()) }
    var started by remember { mutableStateOf(false) }
    // Modo del juego en curso (fijado al pulsar EMPEZAR).
    var playMode by remember { mutableStateOf(MODE_READER) }
    val sim3d = started && playMode == MODE_DECK3D
    val texts = remember(settings) { Texts.forLanguage(lang, settings.params()) }
    val phases = remember(settings) { settings.effectivePhases(texts) }
    val engine = remember(phases) { EffectEngine(phases) }
    var state by remember(engine) { mutableStateOf(engine.state) }
    var resetCount by remember { mutableStateOf(0) }
    var showConfig by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val open = remember { Animatable(0f) }
    var connectLine by remember { mutableStateOf("") }
    val time = rememberTime()

    fun sync() { state = engine.state }
    fun openResult() { engine.revealed(); sync() }

    // Efecto de conexión con la baraja.
    LaunchedEffect(engine, resetCount, started) {
        if (!started || engine.state.stage != Stage.Connecting) return@LaunchedEffect
        for (line in texts.connecting) {
            for (i in 1..line.length) { connectLine = line.take(i); delay(28) }
            delay(settings.connectPauseMs)
        }
        engine.connected(); sync()
    }
    // Animación de análisis → aparece el resultado pulsable.
    LaunchedEffect(state, resetCount) {
        if (state.stage == Stage.Analyzing) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(settings.analyzeMs.toInt(), easing = LinearEasing))
            engine.resultReady(); sync()
        }
    }
    // Animación de apertura del resultado.
    LaunchedEffect(state, resetCount) {
        if (state.stage == Stage.Revealed) {
            open.snapTo(0f)
            open.animateTo(1f, tween(settings.openMs.toInt(), easing = LinearEasing))
        } else {
            open.snapTo(0f)
        }
    }

    SystemBarsEffect()
    InexplicableTheme(settings.palette()) {
        val pal = LocalPalette.current
        Box(Modifier.fillMaxSize().background(HudBg)) {
            val shownPhase = phases.getOrNull(state.phase)?.text ?: ""
            if (sim3d) {
                DeckScene(
                    stage = state.stage, progress = progress.value, open = open.value, level = settings.hudLevel,
                    bubble = if (state.stage == Stage.Revealed) typed(shownPhase, state) else null,
                    tapLabel = texts.tapToReveal, time = time, onOpen = ::openResult,
                )
            } else {
                HudCanvas(if (started) state.stage else Stage.Ready, progress.value, open.value, settings.hudLevel, time)
                // El sello de resultado del HUD se pulsa en la zona central.
                if (started && state.stage == Stage.Result) {
                    Box(
                        Modifier.fillMaxSize().pointerInput(state.stage) {
                            detectTapGestures { off ->
                                val c = hudCenter(size.width.toFloat(), size.height.toFloat())
                                if ((off - c).getDistance() < hudRadius(size.width.toFloat(), size.height.toFloat())) openResult()
                            }
                        },
                    )
                }
            }

            if (!started) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(texts.title, color = pal.primary, fontSize = 28.sp, letterSpacing = 10.sp, fontWeight = FontWeight.Light)
                    Box(Modifier.weight(1f))
                    HudButton(if (lang == "en") "START" else "EMPEZAR", true) {
                        // Se relee la configuración (pudo cambiarse desde la RA) y se fija el modo de este juego.
                        val fresh = store.load()
                        settings = fresh
                        if (fresh.mode == MODE_AR && onStartAR != null) {
                            onStartAR()
                        } else {
                            engine.reset(); resetCount++; connectLine = ""; sync()
                            // Sin RA nativa (Android) el modo RA cae al HUD plano.
                            playMode = if (fresh.mode == MODE_AR) MODE_READER else fresh.mode
                            started = true
                        }
                    }
                }
            } else Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(texts.title, color = pal.primary, fontSize = 18.sp, letterSpacing = 8.sp, fontWeight = FontWeight.Light)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val message = when (state.stage) {
                        Stage.Connecting -> connectLine
                        Stage.Ready -> texts.ready
                        Stage.Analyzing -> texts.analyzing + " " + (progress.value * 100).toInt() + "%"
                        Stage.Result -> if (sim3d) "" else texts.tapToReveal
                        Stage.Revealed -> if (sim3d || open.value < 0.35f) "" else typed(shownPhase, state)
                        Stage.Finished -> texts.finished
                    }
                    val pulse by rememberInfiniteTransition().animateFloat(
                        0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    )
                    Text(
                        message,
                        color = if (state.stage == Stage.Result) pal.accent else Color.White,
                        fontSize = if (state.stage == Stage.Revealed) 26.sp else 16.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = if (state.stage == Stage.Revealed) 36.sp else 22.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 32.dp).alpha(if (state.stage == Stage.Result) pulse else 1f),
                    )
                }
                val label = if (state.stage == Stage.Revealed) texts.nextButton else texts.analyzeButton
                val active = state.stage == Stage.Ready || (state.stage == Stage.Revealed && open.value > 0.9f)
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

            // Zonas ocultas: pulsación larga arriba a la izquierda = reset; arriba a la derecha = configuración.
            Box(
                Modifier.align(Alignment.TopStart).size(88.dp).pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        engine.reset(); resetCount++; connectLine = ""; sync()
                        started = false // vuelve al menú principal
                    })
                },
            )
            Box(
                Modifier.align(Alignment.TopEnd).size(88.dp).pointerInput(Unit) {
                    detectTapGestures(onLongPress = { settings = store.load(); showConfig = true })
                },
            )
            if (showConfig) {
                ConfigScreen(
                    lang = lang,
                    initial = settings,
                    arAvailable = onStartAR != null,
                    onCancel = { showConfig = false },
                    onSave = { new ->
                        store.save(new)
                        settings = new
                        connectLine = ""
                        resetCount++
                        showConfig = false
                    },
                )
            }
        }
    }
}

@Composable
private fun HudButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val glow by rememberInfiniteTransition().animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse),
    )
    val c = if (enabled) pal.primary else pal.primary.copy(alpha = 0.3f)
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
