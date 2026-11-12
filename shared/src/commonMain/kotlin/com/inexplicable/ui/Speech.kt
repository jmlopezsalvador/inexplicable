package com.inexplicable.ui

import androidx.compose.runtime.Composable

/**
 * Voz de salida (TTS) del sistema. `rate` y `pitch` son multiplicadores (1,0 = normal). `lang` es el código del idioma («es», «en»).
 * Android: `TextToSpeech`. iOS: `AVSpeechSynthesizer` desde Swift (`SpeechBridge` en `SoundEngine.swift`).
 */
interface Speaker {
    /** Habla `text` cortando lo que estuviera diciendo. */
    fun speak(text: String, lang: String, rate: Float = 1f, pitch: Float = 1f)
    fun stop()
    fun release()
}

/** Un `Speaker` ligado a la composición (se libera solo). */
@Composable
expect fun rememberSpeaker(): Speaker
