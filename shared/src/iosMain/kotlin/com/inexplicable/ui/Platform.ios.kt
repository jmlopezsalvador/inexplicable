@file:OptIn(ExperimentalForeignApi::class)

package com.inexplicable.ui

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

/** iOS: sin comportamiento especial (la barra de estado y el indicador de inicio se ocultan en ContentView.swift). */
@Composable
actual fun SystemBarsEffect() {}

@Composable
actual fun LifecycleEffect(onPause: () -> Unit, onResume: () -> Unit) {}

/**
 * Puente con el sintetizador de sonido de Swift (`SoundEngine.swift`): Swift asigna `handler` al arrancar y Kotlin le manda
 * órdenes de texto («ambient_on», «analyzing_off», «ping», «reveal», «pause», «resume», «volume:0.6», «release»).
 */
object IosSoundHooks {
    var handler: ((String) -> Unit)? = null
}

private class IosSoundEngine : SoundEngine {
    private fun send(cmd: String) { IosSoundHooks.handler?.invoke(cmd) }
    override fun volume(v: Float) = send("volume:" + v.coerceIn(0f, 1f))
    override fun ambient(on: Boolean) = send(if (on) "ambient_on" else "ambient_off")
    override fun analyzing(on: Boolean) = send(if (on) "analyzing_on" else "analyzing_off")
    override fun ping() = send("ping")
    override fun reveal() = send("reveal")
    override fun pause() = send("pause")
    override fun resume() = send("resume")
    override fun release() = send("release")
}

actual fun createSoundEngine(): SoundEngine = IosSoundEngine()

actual fun deviceLanguage(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String)?.substringBefore('-')?.lowercase() ?: "es"

class UserDefaultsStore : KeyValueStore {
    private val d = NSUserDefaults.standardUserDefaults
    override fun getString(key: String): String? = d.stringForKey(key)
    override fun getBoolean(key: String, default: Boolean) =
        if (d.objectForKey(key) == null) default else d.boolForKey(key)
    override fun getLong(key: String, default: Long) =
        if (d.objectForKey(key) == null) default else d.integerForKey(key)
    override fun putString(key: String, value: String) = d.setObject(value, key)
    override fun putBoolean(key: String, value: Boolean) = d.setBool(value, key)
    override fun putLong(key: String, value: Long) = d.setInteger(value, key)
}
