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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
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
import com.inexplicable.script.ScriptAction
import com.inexplicable.script.ScriptBooks
import com.inexplicable.script.expectedAction
import com.inexplicable.script.hear
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

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
    val speaker = rememberSpeaker()
    var backBytes by remember { mutableStateOf(store.loadBackPhoto()?.let(::base64ToBytes)) }
    val backImage = remember(backBytes) { backBytes?.let(::decodeImage) }
    InexplicableTheme(initial.palette()) {
        ConfigScreen(
            lang, initial, arAvailable,
            onTestVoice = { text, rate, pitch -> speaker.speak(text, lang, rate, pitch) },
            backImage = backImage,
            onBackPhoto = { store.saveBackPhoto(bytesToBase64(it)); backBytes = it },
            onClearBack = { store.saveBackPhoto(null); backBytes = null },
            onSave = { speaker.stop(); store.save(it); onClose() },
            onCancel = { speaker.stop(); onClose() },
        )
    }
}

private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

/** Pantalla para ajustar las 4 esquinas de la baraja sobre la foto recién hecha. */
@Composable
private fun PhotoAdjustScreen(
    photo: ImageBitmap, corners: List<Offset>, lang: String,
    onChange: (List<Offset>) -> Unit, onRotate: () -> Unit, onRetake: () -> Unit, onConfirm: () -> Unit,
) {
    val pal = LocalPalette.current
    fun t(es: String, en: String) = if (lang == "en") en else es
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PhotoAdjust(photo, corners, onChange)
        Text(
            t("Arrastra las 4 esquinas hasta ajustarlas a la baraja. El punto amarillo marca la esquina de arriba a la izquierda (lado corto arriba).",
                "Drag the 4 corners to fit the deck. The yellow dot marks the top-left corner (short side on top)."),
            color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp, start = 24.dp, end = 24.dp),
        )
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onRetake) { Text(t("REPETIR FOTO", "RETAKE"), color = pal.primary) }
                TextButton(onRotate) { Text(t("GIRAR ↻", "ROTATE ↻"), color = pal.primary) }
            }
            HudButton(t("CONFIRMAR", "CONFIRM"), true, onConfirm)
        }
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
    // Control por conversación: el efecto espera órdenes (saludar, enlazar, analizar…) y la app responde con su guion.
    val scriptMode = settings.control == CONTROL_SCRIPT
    val book = remember(lang) { ScriptBooks.forLanguage(lang) }
    val photoMode = settings.mode == MODE_PHOTO
    // Modo Foto: antes de enlazar hay que hacer y ajustar la foto de la baraja.
    var photoStep by remember { mutableStateOf(0) }            // 0 sin foto, 1 cámara, 2 ajustando esquinas, 3 listo
    var photoImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var corners by remember { mutableStateOf(defaultDeckCorners()) }
    // Foto del dorso de la baraja 3D.
    var backVersion by remember { mutableStateOf(0) }
    val backImage = remember(settings, backVersion) { store.loadBackPhoto()?.let(::base64ToBytes)?.let(::decodeImage) }
    val engine = remember(phases, scriptMode, photoMode) { EffectEngine(phases, startIdle = scriptMode || photoMode) }
    var state by remember(engine) { mutableStateOf(engine.state) }
    var resetCount by remember { mutableStateOf(0) }
    var showConfig by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val open = remember { Animatable(0f) }
    var connectLine by remember { mutableStateOf("") }
    var greeted by remember { mutableStateOf(false) }
    var subtitle by remember { mutableStateOf("") }
    val time = rememberTime()

    // Sonido: ambiente siempre (menú incluido); análisis, resultado y apertura siguen las etapas.
    val sound = remember { createSoundEngine() }
    DisposableEffect(Unit) { onDispose { sound.release() } }
    LifecycleEffect(onPause = { sound.pause() }, onResume = { sound.resume() })
    LaunchedEffect(settings.soundOn, settings.soundVolume) {
        sound.volume(settings.soundVolume / 100f)
        sound.ambient(settings.soundOn)
    }
    LaunchedEffect(state.stage, started, settings.soundOn) {
        val on = settings.soundOn && started
        sound.analyzing(on && state.stage == Stage.Analyzing)
        if (on && state.stage == Stage.Result) sound.ping()
        if (on && state.stage == Stage.Revealed) sound.reveal()
    }

    // Voz: estado al enlazar, al empezar a analizar y al terminar; la predicción se lee cuando aparece el cartel.
    val speaker = rememberSpeaker()
    // Mientras la app habla se ignora lo que oiga el micrófono (si no, se oiría a sí misma). La duración es una estimación.
    val mutedUntil = remember { LongArray(1) }
    fun say(text: String, rate: Float, pitch: Float) {
        mutedUntil[0] = nowMs() + text.length * 80L + 900L
        speaker.speak(text, lang, rate, pitch)
    }
    // Reacción visual a la voz. Los eventos reales de la voz también afinan el silenciado del micrófono:
    // mientras suena no se oye nada y, al terminar, se deja un margen corto por el eco.
    val speech = remember { SpeechFeedback() }
    speech.gain = if (settings.speechFx) settings.speechFxPct / 100f else 0f
    LaunchedEffect(speaker) {
        speaker.onEvent = { e ->
            speech.onEvent(e, time.value)
            when (e) {
                SpeechEvent.Start -> mutedUntil[0] = nowMs() + 60_000L
                SpeechEvent.End -> mutedUntil[0] = nowMs() + 500L
                SpeechEvent.Word -> {}
            }
        }
    }
    LaunchedEffect(state, started, settings) {
        if (!started || !settings.voiceOn) { speaker.stop(); return@LaunchedEffect }
        val rate = settings.voiceRate / 100f
        val pitch = settings.voicePitch / 100f
        when (state.stage) {
            Stage.Ready -> if (settings.voiceStatus && state.phase == 0) say(if (scriptMode) book.linked else texts.ready, rate, pitch)
            // En el guion, «analizando» lo dice la respuesta del propio paso.
            Stage.Analyzing -> if (settings.voiceStatus && !scriptMode) say(texts.analyzing, rate, pitch)
            Stage.Revealed -> if (settings.voiceReveal) {
                delay((settings.openMs * 0.45).toLong())
                phases.getOrNull(state.phase)?.let { say(it.text, rate, pitch) }
            }
            Stage.Finished -> if (settings.voiceStatus) say(if (scriptMode) book.finished else texts.finished, rate, pitch)
            else -> {}
        }
    }
    // Subtítulo de lo que «dice» Jarvis cuando es la propia etapa quien habla (enlazado y final).
    LaunchedEffect(state.stage, state.phase, scriptMode) {
        if (scriptMode) when (state.stage) {
            Stage.Ready -> if (state.phase == 0) subtitle = book.linked
            Stage.Finished -> subtitle = book.finished
            else -> {}
        }
    }

    fun sync() { state = engine.state }

    /** «Enlazar»: en el modo Foto primero hay que hacer la foto de la baraja; en los demás se enlaza ya. */
    fun requestLink() {
        if (photoMode && photoStep < 3) photoStep = 1 else { connectLine = ""; engine.link() }
    }

    /** Ejecuta un paso del guion: lo llaman el botón de respaldo y el reconocimiento de voz. */
    fun perform(action: ScriptAction) {
        val line = book.line(action)
        when (action) {
            ScriptAction.Greet -> greeted = true
            ScriptAction.Link -> requestLink()
            ScriptAction.Analyze -> engine.analyze()
            ScriptAction.Open -> engine.revealed()
            ScriptAction.Continue -> engine.next()
        }
        sync()
        subtitle = line.reply
        if (settings.voiceOn && line.reply.isNotBlank()) say(line.reply, settings.voiceRate / 100f, settings.voicePitch / 100f)
    }
    fun openResult() { if (scriptMode) perform(ScriptAction.Open) else { engine.revealed(); sync() } }

    // Escucha por voz (solo con el control por conversación): lo oído solo cuenta si encaja con la orden que toca ahora.
    val listener = rememberListener()
    var heard by remember { mutableStateOf("") }
    val onHeard: (String) -> Unit = handler@{ text ->
        heard = text
        if (nowMs() < mutedUntil[0]) return@handler // la app está hablando
        val action = hear(book, state.stage, greeted, text, settings.wakeRequired, settings.matchPct / 100.0)
        if (action != null) perform(action)
    }
    val onHeardState by rememberUpdatedState(onHeard)
    LaunchedEffect(started, scriptMode, settings.voiceListen, lang) {
        if (started && scriptMode && settings.voiceListen) listener.start(lang) { onHeardState(it) } else listener.stop()
    }

    // Efecto de conexión con la baraja.
    LaunchedEffect(engine, resetCount, started, state.stage) {
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
            val photo = photoImage
            val photoShown = started && photoMode && photoStep == 3 && photo != null
            val scene3d = sim3d || photoShown // escena con burbuja propia en lugar del HUD plano
            if (sim3d) {
                DeckScene(
                    stage = state.stage, progress = progress.value, open = open.value, level = settings.hudLevel,
                    bubble = if (state.stage == Stage.Revealed) typed(shownPhase, state) else null,
                    tapLabel = texts.tapToReveal, time = time, speech = speech, onOpen = ::openResult, backImage = backImage,
                )
            } else if (photoShown && photo != null) {
                PhotoScene(
                    photo = photo, corners = corners,
                    stage = state.stage, progress = progress.value, open = open.value, level = settings.hudLevel,
                    bubble = if (state.stage == Stage.Revealed) typed(shownPhase, state) else null,
                    tapLabel = texts.tapToReveal, time = time, speech = speech, onOpen = ::openResult,
                )
            } else {
                HudCanvas(if (started) state.stage else Stage.Ready, progress.value, open.value, settings.hudLevel, time, speech)
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
                            val ft = Texts.forLanguage(lang, fresh.params())
                            store.publishForAR(fresh.effectivePhases(ft).map { it.text }, ft)
                            onStartAR()
                        } else {
                            engine.reset(); resetCount++; connectLine = ""; greeted = false; subtitle = ""; heard = ""; sync()
                            photoStep = 0; photoImage = null; corners = defaultDeckCorners()
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
                if (scriptMode && settings.voiceListen && settings.listenDebug) {
                    // Ayuda para ajustar el reconocimiento: lo último que se oyó (se quitará).
                    Text("● " + heard.ifBlank { "…" }, color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val message = when (state.stage) {
                        Stage.Idle -> if (photoMode && !scriptMode) (if (lang == "en") "Take a photo of the deck to link." else "Haz una foto de la baraja para enlazar.") else ""
                        Stage.Connecting -> connectLine
                        Stage.Ready -> texts.ready
                        Stage.Analyzing -> texts.analyzing + " " + (progress.value * 100).toInt() + "%"
                        Stage.Result -> if (scene3d) "" else texts.tapToReveal
                        Stage.Revealed -> if (scene3d || open.value < 0.35f) "" else typed(shownPhase, state)
                        Stage.Finished -> texts.finished
                    }
                    val pulse by rememberInfiniteTransition().animateFloat(
                        0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Subtítulo: lo que «dice» Jarvis en el guion.
                        if (scriptMode && subtitle.isNotBlank() && state.stage != Stage.Revealed) {
                            Text(
                                "“$subtitle”", color = pal.primary, fontSize = 15.sp, fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 14.dp),
                            )
                        }
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
                }
                if (scriptMode) {
                    // Botón de respaldo del guion: pone lo que se espera ahora y dispara el mismo paso que la voz.
                    val action = expectedAction(state.stage, greeted)
                    when {
                        action != null -> HudButton(
                            book.line(action).label,
                            action != ScriptAction.Continue || open.value > 0.9f,
                        ) { perform(action) }
                        state.stage != Stage.Finished -> HudButton("…", false) {}
                    }
                } else {
                    val label = if (state.stage == Stage.Revealed) texts.nextButton else texts.analyzeButton
                    val active = state.stage == Stage.Ready || (state.stage == Stage.Revealed && open.value > 0.9f)
                    if (state.stage == Stage.Idle) {
                        // Modo Foto sin conversación: el botón pide la foto de la baraja.
                        HudButton(if (lang == "en") "TAKE PHOTO" else "HACER FOTO", true) { requestLink(); sync() }
                    } else if (state.stage != Stage.Finished) {
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
            }

            // Modo Foto: cámara y ajuste de esquinas ocupan toda la pantalla (las zonas ocultas siguen por encima).
            if (started && photoMode && photoStep == 1) {
                CameraCapture(
                    frameAspect = 63f / 88f, cropToFrame = false,
                    hint = if (lang == "en") "Take a photo of the deck on the table, from above or at a 3/4 angle. Keep it inside the frame."
                           else "Haz una foto de la baraja sobre la mesa, desde arriba o en diagonal (3/4). Colócala dentro del marco.",
                    cancelLabel = if (lang == "en") "Cancel" else "Cancelar",
                ) { bytes ->
                    val img = bytes?.let(::decodeImage)
                    if (img != null) { photoImage = img; corners = defaultDeckCorners(img.width, img.height); photoStep = 2 } else photoStep = 0
                }
            }
            if (started && photoMode && photoStep == 2 && photo != null) {
                PhotoAdjustScreen(
                    photo, corners, lang,
                    onChange = { corners = it },
                    onRotate = { corners = listOf(corners[1], corners[2], corners[3], corners[0]) },
                    onRetake = { photoStep = 1 },
                    onConfirm = { photoStep = 3; connectLine = ""; engine.link(); sync() },
                )
            }

            // Zonas ocultas: pulsación larga arriba a la izquierda = reset; arriba a la derecha = configuración.
            Box(
                Modifier.align(Alignment.TopStart).size(88.dp).pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        engine.reset(); resetCount++; connectLine = ""; greeted = false; subtitle = ""; heard = ""; sync()
                        photoStep = 0; photoImage = null; corners = defaultDeckCorners()
                        listener.stop()
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
                    onTestVoice = { text, rate, pitch -> speaker.speak(text, lang, rate, pitch) },
                    backImage = backImage,
                    onBackPhoto = { store.saveBackPhoto(bytesToBase64(it)); backVersion++ },
                    onClearBack = { store.saveBackPhoto(null); backVersion++ },
                    onCancel = { speaker.stop(); showConfig = false },
                    onSave = { new ->
                        speaker.stop()
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
