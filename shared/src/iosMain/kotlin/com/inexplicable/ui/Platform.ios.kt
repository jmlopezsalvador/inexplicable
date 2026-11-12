@file:OptIn(ExperimentalForeignApi::class)

package com.inexplicable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

/** iOS: sin comportamiento especial (la barra de estado y el indicador de inicio se ocultan en ContentView.swift). */
@Composable
actual fun SystemBarsEffect() {}

@Composable
actual fun LifecycleEffect(onPause: () -> Unit, onResume: () -> Unit) {}

actual fun decodeImage(bytes: ByteArray): ImageBitmap? =
    try { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() } catch (e: Exception) { null }

/**
 * Puente con la cámara de Swift (`CameraBridge` en `Camera.swift`). Swift asigna `handler`; Kotlin pide una foto con `request` y Swift
 * contesta con `deliver` (JPEG en base64, o vacío si se cancela). La orden va como texto separado por U+001F: «capture», proporción del
 * marco, recortar (true/false), texto de ayuda y texto de cancelar.
 */
object IosCameraHooks {
    var handler: ((String) -> Unit)? = null
    private var callback: ((String) -> Unit)? = null

    fun request(aspect: Float, crop: Boolean, hint: String, cancel: String, cb: (String) -> Unit) {
        callback = cb
        val h = handler
        if (h == null) { callback = null; cb("") } else h("capture\u001F$aspect\u001F$crop\u001F$hint\u001F$cancel")
    }

    fun deliver(base64: String) {
        val cb = callback
        callback = null
        cb?.invoke(base64)
    }
}

@Composable
actual fun CameraCapture(frameAspect: Float, cropToFrame: Boolean, hint: String, cancelLabel: String, onResult: (ByteArray?) -> Unit) {
    // La cámara real la presenta Swift encima; aquí solo se pide y se espera la respuesta.
    Box(Modifier.fillMaxSize().background(Color.Black))
    LaunchedEffect(Unit) {
        IosCameraHooks.request(frameAspect, cropToFrame, hint, cancelLabel) { b64 ->
            onResult(if (b64.isBlank()) null else base64ToBytes(b64))
        }
    }
}

/**
 * Puente con el reconocimiento de voz de Swift (`ListenBridge`): órdenes «start» (con el idioma) y «stop»; Swift devuelve cada frase
 * con `deliver`.
 */
object IosListenHooks {
    var handler: ((String) -> Unit)? = null
    private var callback: ((String) -> Unit)? = null

    fun start(lang: String, cb: (String) -> Unit) {
        callback = cb
        handler?.invoke("start\u001F$lang")
    }

    fun stop() { handler?.invoke("stop") }

    fun deliver(text: String) { callback?.invoke(text) }
}

private class IosListener : Listener {
    override fun start(lang: String, onHeard: (String) -> Unit) = IosListenHooks.start(lang, onHeard)
    override fun stop() = IosListenHooks.stop()
    override fun release() = IosListenHooks.stop()
}

@Composable
actual fun rememberListener(): Listener {
    val listener = remember { IosListener() }
    DisposableEffect(listener) { onDispose { listener.release() } }
    return listener
}

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
    private val sinks = mutableListOf<(String) -> Unit>()

    fun addSink(s: (String) -> Unit) { sinks.add(s) }
    fun removeSink(s: (String) -> Unit) { sinks.remove(s) }

    /** Swift avisa de lo que hace la voz: «start», «word» o «end». */
    fun deliver(event: String) { for (s in sinks.toList()) s(event) }
}

private class IosSpeaker : Speaker {
    override var onEvent: ((SpeechEvent) -> Unit)? = null
    private val sink: (String) -> Unit = { e ->
        when (e) {
            "start" -> onEvent?.invoke(SpeechEvent.Start)
            "word" -> onEvent?.invoke(SpeechEvent.Word)
            "end" -> onEvent?.invoke(SpeechEvent.End)
        }
    }
    init { IosSpeechHooks.addSink(sink) }

    override fun speak(text: String, lang: String, rate: Float, pitch: Float) {
        IosSpeechHooks.handler?.invoke("speak\u001F$lang\u001F$rate\u001F$pitch\u001F$text")
    }
    override fun stop() { IosSpeechHooks.handler?.invoke("stop") }
    override fun release() { IosSpeechHooks.handler?.invoke("stop"); IosSpeechHooks.removeSink(sink) }
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
