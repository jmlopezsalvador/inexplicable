package com.inexplicable.ui

import com.inexplicable.effect.EffectParams
import com.inexplicable.effect.Phase
import com.inexplicable.effect.UiTexts

const val MODE_READER = "reader"
const val MODE_AR = "ar"
const val MODE_DECK3D = "deck3d"

val DEFAULT_PRIMARY = 0xFF3DDCFFL
val DEFAULT_SECONDARY = 0xFF3D7BFFL
val DEFAULT_ACCENT = 0xFFFFC857L

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
    val mode: String = MODE_READER,   // modo de juego: MODE_READER, MODE_DECK3D o MODE_AR (se aplica al empezar un juego nuevo)
    val openMs: Long = 1400,          // duración de la animación al abrir el resultado
    val hudLevel: Int = 2,            // complejidad visual del HUD y de los efectos: 1 sencillo, 2 medio, 3 complejo
    val colorPrimary: Long = DEFAULT_PRIMARY,     // paleta (ARGB); la lee también el código nativo de iOS
    val colorSecondary: Long = DEFAULT_SECONDARY,
    val colorAccent: Long = DEFAULT_ACCENT,
    val soundOn: Boolean = true,      // efectos de sonido (ambiente, análisis, resultado y apertura)
    val soundVolume: Int = 60,        // 0..100
    val voiceOn: Boolean = true,      // voz del sistema (TTS)
    val voiceStatus: Boolean = true,  // dice los mensajes de estado («baraja enlazada», «analizando…», «análisis completo»)
    val voiceReveal: Boolean = true,  // lee la predicción al abrir el resultado
    val voiceRate: Int = 100,         // velocidad en % (50..200)
    val voicePitch: Int = 100,        // tono en % (50..200)
) {
    fun palette() = Palette(colorFrom(colorPrimary), colorFrom(colorSecondary), colorFrom(colorAccent))

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
            mode = prefs.getString("mode")?.takeIf { it == MODE_READER || it == MODE_AR || it == MODE_DECK3D } ?: d.mode,
            openMs = prefs.getLong("openMs", d.openMs),
            hudLevel = prefs.getLong("hudLevel", d.hudLevel.toLong()).toInt().coerceIn(1, 3),
            colorPrimary = prefs.getLong("colorPrimary", d.colorPrimary),
            colorSecondary = prefs.getLong("colorSecondary", d.colorSecondary),
            colorAccent = prefs.getLong("colorAccent", d.colorAccent),
            soundOn = prefs.getBoolean("soundOn", d.soundOn),
            soundVolume = prefs.getLong("soundVolume", d.soundVolume.toLong()).toInt().coerceIn(0, 100),
            voiceOn = prefs.getBoolean("voiceOn", d.voiceOn),
            voiceStatus = prefs.getBoolean("voiceStatus", d.voiceStatus),
            voiceReveal = prefs.getBoolean("voiceReveal", d.voiceReveal),
            voiceRate = prefs.getLong("voiceRate", d.voiceRate.toLong()).toInt().coerceIn(50, 200),
            voicePitch = prefs.getLong("voicePitch", d.voicePitch.toLong()).toInt().coerceIn(50, 200),
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
        prefs.putLong("openMs", s.openMs)
        prefs.putLong("hudLevel", s.hudLevel.toLong())
        prefs.putLong("colorPrimary", s.colorPrimary)
        prefs.putLong("colorSecondary", s.colorSecondary)
        prefs.putLong("colorAccent", s.colorAccent)
        prefs.putBoolean("soundOn", s.soundOn)
        prefs.putLong("soundVolume", s.soundVolume.toLong())
        prefs.putBoolean("voiceOn", s.voiceOn)
        prefs.putBoolean("voiceStatus", s.voiceStatus)
        prefs.putBoolean("voiceReveal", s.voiceReveal)
        prefs.putLong("voiceRate", s.voiceRate.toLong())
        prefs.putLong("voicePitch", s.voicePitch.toLong())
    }

    /**
     * Publica las fases y textos ya resueltos (idioma, números, personalización) para la RA nativa de iOS, que los lee de
     * UserDefaults con estas claves: `arPhases` (separadas por U+001F), `arAnalyze`, `arNext`, `arTapReveal`, `arAnalyzing`, `arFinished`.
     */
    fun publishForAR(phases: List<String>, texts: UiTexts) {
        prefs.putString("arPhases", phases.joinToString(SEP.toString()))
        prefs.putString("arAnalyze", texts.analyzeButton)
        prefs.putString("arNext", texts.nextButton)
        prefs.putString("arTapReveal", texts.tapToReveal)
        prefs.putString("arAnalyzing", texts.analyzing)
        prefs.putString("arFinished", texts.finished)
        prefs.putString("arReady", texts.ready)
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
