package com.inexplicable.ui

import com.inexplicable.effect.EffectParams
import com.inexplicable.effect.Phase
import com.inexplicable.effect.UiTexts

const val MODE_READER = "reader"
const val MODE_AR = "ar"

/** Almacenamiento clave-valor local. Android: SharedPreferences. iOS: NSUserDefaults (ver las `actual`). */
interface KeyValueStore {
    fun getString(key: String): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getLong(key: String, default: Long): Long
    fun putString(key: String, value: String)
    fun putBoolean(key: String, value: Boolean)
    fun putLong(key: String, value: Long)
}

/**
 * Ajustes de la app (configuración secreta).
 * `customPhases` vacío = se usan las fases por defecto del idioma, rellenadas con `faceDown`, `red` y `extraCard`.
 */
data class AppSettings(
    val faceDown: Int = 24,
    val red: Int = 13,
    val extraCard: String = "",
    val customPhases: List<String> = emptyList(),
    val analyzeMs: Long = 2600,
    val connectPauseMs: Long = 650,
    // Detección del mazo en RA (la lee el código Swift de iOS desde UserDefaults con estas mismas claves).
    val detTries: Int = 3,            // detecciones estables seguidas necesarias para anclar
    val detTolerancePct: Int = 30,    // margen de error (%) sobre las medidas 6,3 x 8,8 cm
    val detIntervalMs: Int = 300,     // cada cuánto se intenta detectar
    val detMinConfidencePct: Int = 60,// confianza mínima de Vision
    val detMinSizePct: Int = 5,       // tamaño mínimo del rectángulo en pantalla (%); más bajo = detecta más lejos
    val detMaxObservations: Int = 3,  // candidatos que evalúa por intento
    val detMoveCm: Int = 3,           // cuánto puede moverse el centro entre intentos para contar como estable
    val detShowDebug: Boolean = true, // dibuja los candidatos que se están evaluando
    val detManualTap: Boolean = true, // permite anclar a mano tocando la pantalla
    val mode: String = MODE_READER,   // modo de juego: MODE_READER o MODE_AR (se aplica al empezar un juego nuevo)
) {
    fun params() = EffectParams(faceDown, red, extraCard)

    /** Fases que se usan realmente: las personalizadas o, si no hay, las del idioma. */
    fun effectivePhases(texts: UiTexts): List<Phase> =
        if (customPhases.isEmpty()) texts.phases else customPhases.map { Phase(it) }
}

class SettingsStore(private val prefs: KeyValueStore) {
    fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            faceDown = prefs.getLong("faceDown", d.faceDown.toLong()).toInt(),
            red = prefs.getLong("red", d.red.toLong()).toInt(),
            extraCard = prefs.getString("extraCard") ?: d.extraCard,
            customPhases = prefs.getString("phases")?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList(),
            analyzeMs = prefs.getLong("analyzeMs", d.analyzeMs),
            connectPauseMs = prefs.getLong("connectPauseMs", d.connectPauseMs),
            detTries = prefs.getLong("detTries", d.detTries.toLong()).toInt(),
            detTolerancePct = prefs.getLong("detTolerancePct", d.detTolerancePct.toLong()).toInt(),
            detIntervalMs = prefs.getLong("detIntervalMs", d.detIntervalMs.toLong()).toInt(),
            detMinConfidencePct = prefs.getLong("detMinConfidencePct", d.detMinConfidencePct.toLong()).toInt(),
            detMinSizePct = prefs.getLong("detMinSizePct", d.detMinSizePct.toLong()).toInt(),
            detMaxObservations = prefs.getLong("detMaxObservations", d.detMaxObservations.toLong()).toInt(),
            detMoveCm = prefs.getLong("detMoveCm", d.detMoveCm.toLong()).toInt(),
            detShowDebug = prefs.getBoolean("detShowDebug", d.detShowDebug),
            detManualTap = prefs.getBoolean("detManualTap", d.detManualTap),
            mode = prefs.getString("mode")?.takeIf { it == MODE_READER || it == MODE_AR } ?: d.mode,
        )
    }

    fun save(s: AppSettings) {
        prefs.putLong("faceDown", s.faceDown.toLong())
        prefs.putLong("red", s.red.toLong())
        prefs.putString("extraCard", s.extraCard)
        prefs.putString("phases", s.customPhases.filter { it.isNotBlank() }.joinToString(SEP.toString()))
        prefs.putLong("analyzeMs", s.analyzeMs)
        prefs.putLong("connectPauseMs", s.connectPauseMs)
        prefs.putLong("detTries", s.detTries.toLong())
        prefs.putLong("detTolerancePct", s.detTolerancePct.toLong())
        prefs.putLong("detIntervalMs", s.detIntervalMs.toLong())
        prefs.putLong("detMinConfidencePct", s.detMinConfidencePct.toLong())
        prefs.putLong("detMinSizePct", s.detMinSizePct.toLong())
        prefs.putLong("detMaxObservations", s.detMaxObservations.toLong())
        prefs.putLong("detMoveCm", s.detMoveCm.toLong())
        prefs.putBoolean("detShowDebug", s.detShowDebug)
        prefs.putBoolean("detManualTap", s.detManualTap)
        prefs.putString("mode", s.mode)
    }

    private companion object { const val SEP = '\u001F' }
}

/** Mueve el elemento `i` una posición (`delta` = -1 arriba, +1 abajo). Si no se puede, devuelve la lista igual. */
fun <T> List<T>.moved(i: Int, delta: Int): List<T> {
    val j = i + delta
    if (i !in indices || j !in indices) return this
    val m = toMutableList()
    val x = m.removeAt(i); m.add(j, x)
    return m
}

fun <T> List<T>.removedAt(i: Int): List<T> = filterIndexed { k, _ -> k != i }
