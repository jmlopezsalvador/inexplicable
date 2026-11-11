package com.inexplicable.ui

/** Almacenamiento clave-valor local. Android: SharedPreferences. iOS: NSUserDefaults (ver las `actual`). */
interface KeyValueStore {
    fun getString(key: String): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getLong(key: String, default: Long): Long
    fun putString(key: String, value: String)
    fun putBoolean(key: String, value: Boolean)
    fun putLong(key: String, value: Long)
}

/** Ajustes de la app. Ampliar aquí (y en load/save) cuando haya opciones reales. */
data class AppSettings(
    val counter: Int = 0,
)

class SettingsStore(private val prefs: KeyValueStore) {
    fun load(): AppSettings = AppSettings(counter = prefs.getLong("counter", 0L).toInt())
    fun save(s: AppSettings) { prefs.putLong("counter", s.counter.toLong()) }
}
