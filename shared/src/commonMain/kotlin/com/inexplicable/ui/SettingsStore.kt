package com.inexplicable.ui

import com.inexplicable.effect.EffectParams
import com.inexplicable.effect.Phase
import com.inexplicable.effect.UiTexts

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
        )
    }

    fun save(s: AppSettings) {
        prefs.putLong("faceDown", s.faceDown.toLong())
        prefs.putLong("red", s.red.toLong())
        prefs.putString("extraCard", s.extraCard)
        prefs.putString("phases", s.customPhases.filter { it.isNotBlank() }.joinToString(SEP.toString()))
        prefs.putLong("analyzeMs", s.analyzeMs)
        prefs.putLong("connectPauseMs", s.connectPauseMs)
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
