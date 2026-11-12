@file:OptIn(ExperimentalForeignApi::class)

package com.inexplicable.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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

/**
 * Puente con la voz de Swift (`SpeechBridge`): órdenes de texto «speak», «stop». En «speak» el resto va separado por U+001F:
 * idioma, velocidad, tono y texto.
 */
object IosSpeechHooks {
    var handler: ((String) -> Unit)? = null
}

private class IosSpeaker : Speaker {
    override fun speak(text: String, lang: String, rate: Float, pitch: Float) {
        IosSpeechHooks.handler?.invoke("speak\u001F$lang\u001F$rate\u001F$pitch\u001F$text")
    }
    override fun stop() { IosSpeechHooks.handler?.invoke("stop") }
    override fun release() { IosSpeechHooks.handler?.invoke("stop") }
}

@Composable
actual fun rememberSpeaker(): Speaker {
    val speaker = remember { IosSpeaker() }
    DisposableEffect(speaker) { onDispose { speaker.release() } }
    return speaker
}

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
