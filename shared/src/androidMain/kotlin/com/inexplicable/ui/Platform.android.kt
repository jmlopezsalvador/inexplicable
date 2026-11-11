package com.inexplicable.ui

import android.content.Context
import androidx.compose.runtime.Composable

/** Android: sin comportamiento especial (aquí iría el modo inmersivo, pantalla siempre encendida, etc.). */
@Composable
actual fun SystemBarsEffect() {}

actual fun deviceLanguage(): String = java.util.Locale.getDefault().language.lowercase()

class SharedPrefsStore(context: Context) : KeyValueStore {
    private val p = context.getSharedPreferences("inexplicable", Context.MODE_PRIVATE)
    override fun getString(key: String): String? = p.getString(key, null)
    override fun getBoolean(key: String, default: Boolean) = p.getBoolean(key, default)
    override fun getLong(key: String, default: Long) = p.getLong(key, default)
    override fun putString(key: String, value: String) = p.edit().putString(key, value).apply()
    override fun putBoolean(key: String, value: Boolean) = p.edit().putBoolean(key, value).apply()
    override fun putLong(key: String, value: Long) = p.edit().putLong(key, value).apply()
}
