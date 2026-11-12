package com.inexplicable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inexplicable.effect.Texts

/** Configuración secreta. Se abre con pulsación larga arriba a la derecha. */
@Composable
fun ConfigScreen(
    lang: String,
    initial: AppSettings,
    arAvailable: Boolean,
    onSave: (AppSettings) -> Unit,
    onCancel: () -> Unit,
) {
    val en = lang == "en"
    fun t(es: String, enText: String) = if (en) enText else es

    var faceDown by remember { mutableStateOf(initial.faceDown.toString()) }
    var red by remember { mutableStateOf(initial.red.toString()) }
    var extra by remember { mutableStateOf(initial.extraCard) }
    var analyzeSec by remember { mutableStateOf((initial.analyzeMs / 1000.0).toString().removeSuffix(".0")) }
    var pauseMs by remember { mutableStateOf(initial.connectPauseMs.toString()) }
    // Si no hay fases personalizadas se parte de las del idioma, para poder editarlas.
    val phases = remember {
        val base = initial.effectivePhases(Texts.forLanguage(lang, initial.params())).map { it.text }
        mutableStateListOf<String>().apply { addAll(base) }
    }
    val original = remember { phases.toList() }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
    var detTries by remember { mutableStateOf(initial.detTries.toString()) }
    var detTol by remember { mutableStateOf(initial.detTolerancePct.toString()) }
    var detInterval by remember { mutableStateOf(initial.detIntervalMs.toString()) }
    var detConf by remember { mutableStateOf(initial.detMinConfidencePct.toString()) }
    var detMinSize by remember { mutableStateOf(initial.detMinSizePct.toString()) }
    var detMaxObs by remember { mutableStateOf(initial.detMaxObservations.toString()) }
    var detMove by remember { mutableStateOf(initial.detMoveCm.toString()) }
    var detDebug by remember { mutableStateOf(initial.detShowDebug) }
    var detManual by remember { mutableStateOf(initial.detManualTap) }
    var mode by remember { mutableStateOf(initial.mode) }

    Box(Modifier.fillMaxSize().background(Color(0xFF00050E))) {
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(t("Configuración", "Settings"), color = Color(0xFF3DDCFF), fontSize = 22.sp)
            Text(
                t("Los números y la carta rellenan las fases por defecto. Si editas las fases, se usan tal cual.",
                    "Numbers and card fill the default phases. If you edit the phases, they are used as written."),
                color = Color.Gray, fontSize = 12.sp,
            )
            if (arAvailable) {
                Text(t("Modo de juego (se aplica al empezar un juego nuevo)", "Game mode (applies when a new game starts)"),
                    color = Color(0xFF3DDCFF), fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(mode == MODE_READER, { mode = MODE_READER })
                    Text(t("Lector (HUD)", "Reader (HUD)"), color = Color.White)
                    Spacer(Modifier.width(16.dp))
                    RadioButton(mode == MODE_AR, { mode = MODE_AR })
                    Text(t("Realidad aumentada", "Augmented reality"), color = Color.White)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(faceDown,{ faceDown = it }, Modifier.weight(1f), label = { Text(t("Boca abajo", "Face down")) },
                    singleLine = true, keyboardOptions = num)
                OutlinedTextField(red, { red = it }, Modifier.weight(1f), label = { Text(t("Rojas", "Red")) },
                    singleLine = true, keyboardOptions = num)
            }
            OutlinedTextField(extra, { extra = it }, Modifier.fillMaxWidth(),
                label = { Text(t("Carta extra / excepción", "Extra card / exception")) }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(analyzeSec, { analyzeSec = it }, Modifier.weight(1f),
                    label = { Text(t("Análisis (s)", "Analysis (s)")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(pauseMs, { pauseMs = it }, Modifier.weight(1f),
                    label = { Text(t("Pausa conexión (ms)", "Link pause (ms)")) }, singleLine = true, keyboardOptions = num)
            }

            Text(t("Fases", "Phases"), color = Color(0xFF3DDCFF), fontSize = 16.sp)
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

            Text(t("Detección RA (pruebas)", "AR detection (testing)"), color = Color(0xFF3DDCFF), fontSize = 16.sp)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(detDebug, { detDebug = it })
                Text(t("  Dibujar lo que evalúa", "  Draw what it evaluates"), color = Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(detManual, { detManual = it })
                Text(t("  Anclar a mano tocando la pantalla", "  Anchor by tapping the screen"), color = Color.White)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onCancel,Modifier.weight(1f)) { Text(t("Cancelar", "Cancel")) }
                Button({
                    val d = AppSettings()
                    val params = initial.copy(
                        faceDown = faceDown.toIntOrNull() ?: d.faceDown, red = red.toIntOrNull() ?: d.red, extraCard = extra)
                    val defaults = Texts.forLanguage(lang, params.params()).phases.map { it.text }
                    val clean = phases.filter { it.isNotBlank() }
                    onSave(params.copy(
                        // Fases sin tocar (partiendo de las por defecto) o iguales a las por defecto: no son personalizadas,
                        // así se regeneran con los números y la carta nuevos.
                        customPhases = if (clean == defaults || (initial.customPhases.isEmpty() && clean == original)) emptyList() else clean,
                        analyzeMs = ((analyzeSec.replace(',', '.').toDoubleOrNull() ?: 2.6) * 1000).toLong().coerceIn(500, 30000),
                        connectPauseMs = pauseMs.toLongOrNull()?.coerceIn(0, 5000) ?: d.connectPauseMs,
                        detTries = detTries.toIntOrNull()?.coerceIn(1, 20) ?: d.detTries,
                        detTolerancePct = detTol.toIntOrNull()?.coerceIn(0, 80) ?: d.detTolerancePct,
                        detIntervalMs = detInterval.toIntOrNull()?.coerceIn(100, 2000) ?: d.detIntervalMs,
                        detMinConfidencePct = detConf.toIntOrNull()?.coerceIn(10, 100) ?: d.detMinConfidencePct,
                        detMinSizePct = detMinSize.toIntOrNull()?.coerceIn(1, 50) ?: d.detMinSizePct,
                        detMaxObservations = detMaxObs.toIntOrNull()?.coerceIn(1, 10) ?: d.detMaxObservations,
                        detMoveCm = detMove.toIntOrNull()?.coerceIn(1, 20) ?: d.detMoveCm,
                        detShowDebug = detDebug,
                        detManualTap = detManual,
                        mode = mode,
                    ))
                }, Modifier.weight(1f)) { Text(t("Guardar", "Save")) }
            }
        }
    }
}
