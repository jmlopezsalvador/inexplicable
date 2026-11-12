package com.inexplicable.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

private class AndroidSpeaker(context: Context) : Speaker {
    private class Request(val text: String, val lang: String, val rate: Float, val pitch: Float)

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Request? = null
    private var counter = 0
    private var progressSet = false
    private val main = Handler(Looper.getMainLooper())
    override var onEvent: ((SpeechEvent) -> Unit)? = null

    private fun emit(e: SpeechEvent) { main.post { onEvent?.invoke(e) } }

    /** Los avisos de la voz llegan en otro hilo: se pasan al principal. */
    private fun installProgress(t: TextToSpeech) {
        if (progressSet) return
        progressSet = true
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = emit(SpeechEvent.Start)
            override fun onDone(utteranceId: String?) = emit(SpeechEvent.End)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = emit(SpeechEvent.End)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = emit(SpeechEvent.End)
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) = emit(SpeechEvent.Word)
        })
    }

    init {
        tts = try {
            TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) pending?.let { r -> pending = null; say(r) }
            }
        } catch (e: Exception) {
            null // sin motor de voz: la app sigue en silencio
        }
    }

    private fun say(r: Request) {
        val t = tts ?: return
        installProgress(t)
        t.language = Locale(r.lang)
        t.setSpeechRate(r.rate.coerceIn(0.3f, 3f))
        t.setPitch(r.pitch.coerceIn(0.3f, 3f))
        t.speak(r.text, TextToSpeech.QUEUE_FLUSH, null, "inexplicable-" + counter++)
    }

    override fun speak(text: String, lang: String, rate: Float, pitch: Float) {
        val r = Request(text, lang, rate, pitch)
        if (ready) say(r) else pending = r // se dirá en cuanto el motor esté listo
    }

    override fun stop() { pending = null; tts?.stop() }

    override fun release() {
        pending = null
        tts?.stop(); tts?.shutdown(); tts = null; ready = false
    }
}

@Composable
actual fun rememberSpeaker(): Speaker {
    val ctx = LocalContext.current
    val speaker = remember { AndroidSpeaker(ctx) }
    DisposableEffect(speaker) { onDispose { speaker.release() } }
    return speaker
}
