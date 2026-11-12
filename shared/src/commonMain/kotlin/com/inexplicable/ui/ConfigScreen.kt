package com.inexplicable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inexplicable.effect.Texts

/** Configuración secreta, organizada en secciones plegables. Se abre con pulsación larga arriba a la derecha. */
@Composable
fun ConfigScreen(
    lang: String,
    initial: AppSettings,
    arAvailable: Boolean,
    onTestVoice: (text: String, rate: Float, pitch: Float) -> Unit,
    onSave: (AppSettings) -> Unit,
    onCancel: () -> Unit,
) {
    val en = lang == "en"
    fun t(es: String, enText: String) = if (en) enText else es
    val accent = LocalPalette.current.primary

    // Juego
    var mode by remember { mutableStateOf(initial.mode) }
    var level by remember { mutableStateOf(initial.hudLevel) }
    var control by remember { mutableStateOf(initial.control) }
    // Efecto
    var faceDown by remember { mutableStateOf(initial.faceDown.toString()) }
    var red by remember { mutableStateOf(initial.red.toString()) }
    var extra by remember { mutableStateOf(initial.extraCard) }
    // Si no hay fases personalizadas se parte de las del idioma, para poder editarlas.
    val phases = remember {
        val base = initial.effectivePhases(Texts.forLanguage(lang, initial.params())).map { it.text }
        mutableStateListOf<String>().apply { addAll(base) }
    }
    val original = remember { phases.toList() }
    // Tiempos
    var analyzeSec by remember { mutableStateOf((initial.analyzeMs / 1000.0).toString().removeSuffix(".0")) }
    var pauseMs by remember { mutableStateOf(initial.connectPauseMs.toString()) }
    var openSec by remember { mutableStateOf((initial.openMs / 1000.0).toString().removeSuffix(".0")) }
    // Colores
    var cPrimary by remember { mutableStateOf(initial.colorPrimary) }
    var cSecondary by remember { mutableStateOf(initial.colorSecondary) }
    var cAccent by remember { mutableStateOf(initial.colorAccent) }
    // Sonido
    var soundOn by remember { mutableStateOf(initial.soundOn) }
    var soundVolume by remember { mutableStateOf(initial.soundVolume / 100f) }
    // Voz
    var voiceOn by remember { mutableStateOf(initial.voiceOn) }
    var voiceStatus by remember { mutableStateOf(initial.voiceStatus) }
    var voiceReveal by remember { mutableStateOf(initial.voiceReveal) }
    var voiceRate by remember { mutableStateOf(initial.voiceRate / 100f) }
    var voicePitch by remember { mutableStateOf(initial.voicePitch / 100f) }
    // Detección RA
    var detTries by remember { mutableStateOf(initial.detTries.toString()) }
    var detTol by remember { mutableStateOf(initial.detTolerancePct.toString()) }
    var detInterval by remember { mutableStateOf(initial.detIntervalMs.toString()) }
    var detConf by remember { mutableStateOf(initial.detMinConfidencePct.toString()) }
    var detMinSize by remember { mutableStateOf(initial.detMinSizePct.toString()) }
    var detMaxObs by remember { mutableStateOf(initial.detMaxObservations.toString()) }
    var detMove by remember { mutableStateOf(initial.detMoveCm.toString()) }
    var detDebug by remember { mutableStateOf(initial.detShowDebug) }
    var detManual by remember { mutableStateOf(initial.detManualTap) }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
    val dec = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    Column(Modifier.fillMaxSize().background(Color(0xFF00050E)).imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(t("Configuración", "Settings"), color = accent, fontSize = 22.sp, modifier = Modifier.padding(top = 28.dp))

            Section(t("Juego y presentación", "Game and presentation"), true) {
                Label(t("Modo de juego (se aplica al empezar un juego nuevo)", "Game mode (applies when a new game starts)"))
                ModeOption(mode == MODE_READER, t("HUD plano (lector)", "Flat HUD (reader)")) { mode = MODE_READER }
                ModeOption(mode == MODE_DECK3D, t("Baraja 3D (mesa virtual)", "3D deck (virtual table)")) { mode = MODE_DECK3D }
                if (arAvailable) ModeOption(mode == MODE_AR, t("Realidad aumentada", "Augmented reality")) { mode = MODE_AR }
                Label(t("Control del avance (se aplica al empezar un juego nuevo)", "How the effect advances (applies when a new game starts)"))
                ModeOption(control == CONTROL_BUTTONS, t("Botones de la pantalla", "On-screen buttons")) { control = CONTROL_BUTTONS }
                ModeOption(control == CONTROL_SCRIPT, t("Conversación guionizada (Jarvis habla)", "Scripted conversation (Jarvis talks)")) { control = CONTROL_SCRIPT }
                Label(t("Complejidad visual (HUD y efectos)", "Visual complexity (HUD and effects)"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for (l in 1..3) {
                        RadioButton(level == l, { level = l })
                        Text(listOf("", t("Sencillo", "Simple"), t("Medio", "Medium"), t("Complejo", "Complex"))[l], color = Color.White)
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }

            Section(t("Efecto y fases", "Effect and phases"), false) {
                Label(t("Los números y la carta rellenan las fases por defecto. Si editas las fases, se usan tal cual.",
                    "Numbers and card fill the default phases. If you edit the phases, they are used as written."))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(faceDown, { faceDown = it }, Modifier.weight(1f), label = { Text(t("Boca abajo", "Face down")) },
                        singleLine = true, keyboardOptions = num)
                    OutlinedTextField(red, { red = it }, Modifier.weight(1f), label = { Text(t("Rojas", "Red")) },
                        singleLine = true, keyboardOptions = num)
                }
                OutlinedTextField(extra, { extra = it }, Modifier.fillMaxWidth(),
                    label = { Text(t("Carta extra / excepción", "Extra card / exception")) }, singleLine = true)
                phases.forEachIndexed { i, text ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(text, { phases[i] = it }, Modifier.weight(1f), label = { Text("${i + 1}") })
                        Column {
                            TextButton({ phases.moved(i, -1).let { phases.clear(); phases.addAll(it) } }, Modifier.width(48.dp)) { Text("▲") }
                            TextButton({ phases.moved(i, 1).let { phases.clear(); phases.addAll(it) } }, Modifier.width(48.dp)) { Text("▼") }
                        }
                        TextButton({ phases.removeAt(i) }, Modifier.width(48.dp)) { Text("✕") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ phases.add("") }) { Text(t("+ Añadir fase", "+ Add phase")) }
                    TextButton({
                        val d = Texts.forLanguage(lang, AppSettings(
                            faceDown = faceDown.toIntOrNull() ?: 24, red = red.toIntOrNull() ?: 13, extraCard = extra).params()).phases
                        phases.clear(); phases.addAll(d.map { it.text })
                    }) { Text(t("Restaurar fases", "Reset phases")) }
                }
            }

            Section(t("Tiempos", "Timing"), false) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(analyzeSec, { analyzeSec = it }, Modifier.weight(1f),
                        label = { Text(t("Análisis (s)", "Analysis (s)")) }, singleLine = true, keyboardOptions = dec)
                    OutlinedTextField(openSec, { openSec = it }, Modifier.weight(1f),
                        label = { Text(t("Apertura (s)", "Opening (s)")) }, singleLine = true, keyboardOptions = dec)
                }
                OutlinedTextField(pauseMs, { pauseMs = it }, Modifier.fillMaxWidth(),
                    label = { Text(t("Pausa en la conexión (ms)", "Link pause (ms)")) }, singleLine = true, keyboardOptions = num)
            }

            Section(t("Colores", "Colors"), false) {
                Label(t("Pulsa el cuadro de color para abrir el selector, o escribe el valor #RRGGBB.",
                    "Tap the swatch to open the picker, or type the #RRGGBB value."))
                ColorPickerField(t("Principal (HUD, anillos)", "Primary (HUD, rings)"), cPrimary) { cPrimary = it }
                ColorPickerField(t("Secundario (partículas, detalles)", "Secondary (particles, details)"), cSecondary) { cSecondary = it }
                ColorPickerField(t("Acento (resultado, destacados)", "Accent (result, highlights)"), cAccent) { cAccent = it }
                TextButton({ cPrimary = DEFAULT_PRIMARY; cSecondary = DEFAULT_SECONDARY; cAccent = DEFAULT_ACCENT }) {
                    Text(t("Restaurar colores", "Reset colors"))
                }
            }

            Section(t("Sonido", "Sound"), false) {
                SwitchRow(soundOn, t("Efectos de sonido", "Sound effects")) { soundOn = it }
                Label(t("Volumen", "Volume") + ": " + (soundVolume * 100).toInt() + "%")
                Slider(soundVolume, { soundVolume = it })
            }

            Section(t("Voz", "Voice"), false) {
                Label(t("Usa la voz del sistema en el idioma del móvil.", "Uses the system voice in the phone's language."))
                SwitchRow(voiceOn, t("Voz activada", "Voice on")) { voiceOn = it }
                SwitchRow(voiceStatus, t("Decir mensajes de estado", "Speak status messages")) { voiceStatus = it }
                SwitchRow(voiceReveal, t("Leer la predicción al abrir", "Read the prediction when opened")) { voiceReveal = it }
                Label(t("Velocidad", "Speed") + ": " + (voiceRate * 100).toInt() + "%")
                Slider(voiceRate, { voiceRate = it }, valueRange = 0.5f..2f)
                Label(t("Tono", "Pitch") + ": " + (voicePitch * 100).toInt() + "%")
                Slider(voicePitch, { voicePitch = it }, valueRange = 0.5f..2f)
                TextButton({
                    onTestVoice(t("Hola. Soy el lector de la baraja.", "Hello. I am the deck reader."), voiceRate, voicePitch)
                }) { Text(t("Probar voz", "Test voice")) }
            }

            if (arAvailable) Section(t("Detección RA (pruebas)", "AR detection (testing)"), false) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(detTries, { detTries = it }, Modifier.weight(1f),
                        label = { Text(t("Intentos estables", "Stable tries")) }, singleLine = true, keyboardOptions = num)
                    OutlinedTextField(detTol, { detTol = it }, Modifier.weight(1f),
                        label = { Text(t("Margen medidas %", "Size margin %")) }, singleLine = true, keyboardOptions = num)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(detInterval, { detInterval = it }, Modifier.weight(1f),
                        label = { Text(t("Intervalo (ms)", "Interval (ms)")) }, singleLine = true, keyboardOptions = num)
                    OutlinedTextField(detConf, { detConf = it }, Modifier.weight(1f),
                        label = { Text(t("Confianza mín. %", "Min confidence %")) }, singleLine = true, keyboardOptions = num)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(detMinSize, { detMinSize = it }, Modifier.weight(1f),
                        label = { Text(t("Tamaño mín. % (menos = más lejos)", "Min size % (lower = farther)")) },
                        singleLine = true, keyboardOptions = num)
                    OutlinedTextField(detMaxObs, { detMaxObs = it }, Modifier.weight(1f),
                        label = { Text(t("Candidatos", "Candidates")) }, singleLine = true, keyboardOptions = num)
                }
                OutlinedTextField(detMove, { detMove = it }, Modifier.fillMaxWidth(),
                    label = { Text(t("Movimiento máx. entre intentos (cm)", "Max move between tries (cm)")) },
                    singleLine = true, keyboardOptions = num)
                SwitchRow(detDebug, t("Dibujar lo que evalúa", "Draw what it evaluates")) { detDebug = it }
                SwitchRow(detManual, t("Anclar a mano tocando la pantalla", "Anchor by tapping the screen")) { detManual = it }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onCancel, Modifier.weight(1f)) { Text(t("Cancelar", "Cancel")) }
            Button({
                val d = AppSettings()
                val params = initial.copy(
                    faceDown = faceDown.toIntOrNull() ?: d.faceDown, red = red.toIntOrNull() ?: d.red, extraCard = extra)
                val defaults = Texts.forLanguage(lang, params.params()).phases.map { it.text }
                val clean = phases.filter { it.isNotBlank() }
                fun secs(s: String, def: Long) = ((s.replace(',', '.').toDoubleOrNull() ?: (def / 1000.0)) * 1000).toLong()
                onSave(params.copy(
                    // Fases sin tocar (partiendo de las por defecto) o iguales a las por defecto: no son personalizadas,
                    // así se regeneran con los números y la carta nuevos.
                    customPhases = if (clean == defaults || (initial.customPhases.isEmpty() && clean == original)) emptyList() else clean,
                    analyzeMs = secs(analyzeSec, d.analyzeMs).coerceIn(500, 60000),
                    openMs = secs(openSec, d.openMs).coerceIn(300, 10000),
                    connectPauseMs = pauseMs.toLongOrNull()?.coerceIn(0, 5000) ?: d.connectPauseMs,
                    mode = mode, hudLevel = level, control = control,
                    colorPrimary = cPrimary, colorSecondary = cSecondary, colorAccent = cAccent,
                    soundOn = soundOn, soundVolume = (soundVolume * 100).toInt().coerceIn(0, 100),
                    voiceOn = voiceOn, voiceStatus = voiceStatus, voiceReveal = voiceReveal,
                    voiceRate = (voiceRate * 100).toInt().coerceIn(50, 200), voicePitch = (voicePitch * 100).toInt().coerceIn(50, 200),
                    detTries = detTries.toIntOrNull()?.coerceIn(1, 20) ?: d.detTries,
                    detTolerancePct = detTol.toIntOrNull()?.coerceIn(0, 80) ?: d.detTolerancePct,
                    detIntervalMs = detInterval.toIntOrNull()?.coerceIn(100, 2000) ?: d.detIntervalMs,
                    detMinConfidencePct = detConf.toIntOrNull()?.coerceIn(10, 100) ?: d.detMinConfidencePct,
                    detMinSizePct = detMinSize.toIntOrNull()?.coerceIn(1, 50) ?: d.detMinSizePct,
                    detMaxObservations = detMaxObs.toIntOrNull()?.coerceIn(1, 10) ?: d.detMaxObservations,
                    detMoveCm = detMove.toIntOrNull()?.coerceIn(1, 20) ?: d.detMoveCm,
                    detShowDebug = detDebug,
                    detManualTap = detManual,
                ))
            }, Modifier.weight(1f)) { Text(t("Guardar", "Save")) }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, color = Color.Gray, fontSize = 12.sp)

@Composable
private fun ModeOption(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(Modifier.clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
        Text(label, color = Color.White)
    }
}

@Composable
private fun SwitchRow(value: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(value, onChange)
        Text("  $label", color = Color.White)
    }
}

/** Sección plegable: cabecera pulsable y contenido. */
@Composable
private fun Section(title: String, startOpen: Boolean, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    val accent = LocalPalette.current.primary
    Column(
        Modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
    ) {
        Text(
            (if (open) "▾  " else "▸  ") + title, color = accent, fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp),
        )
        if (open) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

/** Muestra de color + campo #RRGGBB + selector de matiz / saturación / brillo. */
@Composable
private fun ColorPickerField(label: String, value: Long, onChange: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var hex by remember(value) { mutableStateOf(colorToHex(value)) }
    val hsv0 = remember { rgbToHsv(value) }
    var h by remember { mutableStateOf(hsv0.h) }
    var s by remember { mutableStateOf(hsv0.s) }
    var v by remember { mutableStateOf(hsv0.v) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(44.dp).background(colorFrom(value), RoundedCornerShape(8.dp))
                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .clickable { open = !open },
            )
            OutlinedTextField(
                hex,
                { txt ->
                    hex = txt
                    parseHex(txt)?.let { c -> rgbToHsv(c).let { h = it.h; s = it.s; v = it.v }; onChange(c) }
                },
                Modifier.weight(1f), label = { Text(label) }, singleLine = true,
            )
        }
        if (open) {
            fun apply() = onChange(hsvToRgb(h, s, v))
            Text("H", color = Color.Gray, fontSize = 11.sp)
            Slider(h, { h = it; apply() }, valueRange = 0f..359.9f)
            Text("S", color = Color.Gray, fontSize = 11.sp)
            Slider(s, { s = it; apply() })
            Text("V", color = Color.Gray, fontSize = 11.sp)
            Slider(v, { v = it; apply() })
        }
    }
}
