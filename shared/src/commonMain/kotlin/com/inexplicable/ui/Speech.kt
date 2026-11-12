package com.inexplicable.ui

import androidx.compose.runtime.Composable
import kotlin.math.max
import kotlin.math.sin

/** Lo que cuenta la voz mientras habla: empieza, dice una palabra, termina. */
enum class SpeechEvent { Start, Word, End }

/**
 * Voz de salida (TTS) del sistema. `rate` y `pitch` son multiplicadores (1,0 = normal). `lang` es el código del idioma («es», «en»).
 * Android: `TextToSpeech`. iOS: `AVSpeechSynthesizer` desde Swift (`SpeechBridge` en `SoundEngine.swift`).
 */
interface Speaker {
    /** Habla `text` cortando lo que estuviera diciendo. */
    fun speak(text: String, lang: String, rate: Float = 1f, pitch: Float = 1f)
    fun stop()
    fun release()

    /** Recibe los eventos de la voz (siempre en el hilo principal). Lo asigna la interfaz para animar los efectos. */
    var onEvent: ((SpeechEvent) -> Unit)?
}

/** Un `Speaker` ligado a la composición (se libera solo). */
@Composable
expect fun rememberSpeaker(): Speaker

/**
 * Reacción visual a la voz. Cada palabra da un impulso (`kick`) que decae rápido; mientras se habla hay además un nivel de base (`level`).
 * Ninguno de los dos TTS deja leer el audio, así que no hay amplitud real: si la plataforma no avisa de las palabras
 * (o van muy espaciadas) se generan impulsos sintéticos a un ritmo de habla normal.
 * `gain` (0 = apagado) escala todo; se ajusta en la configuración.
 */
class SpeechFeedback {
    var gain = 1f
    private var speaking = false
    private var kickRaw = 0f
    private var baseRaw = 0f
    private var lastT = 0f
    private var lastWordT = -10f
    private var nextSynthT = 0f

    /** Impulso de la última palabra, 0..1 (ya con `gain`). */
    val kick: Float get() = kickRaw * gain
    /** Intensidad general mientras se habla, 0..1 (ya con `gain`). */
    val level: Float get() = max(baseRaw * 0.5f, kickRaw) * gain
    val isSpeaking: Boolean get() = speaking

    fun onEvent(e: SpeechEvent, t: Float) {
        when (e) {
            SpeechEvent.Start -> { speaking = true; lastWordT = t; nextSynthT = t + 0.3f }
            SpeechEvent.Word -> { lastWordT = t; kickRaw = 1f }
            SpeechEvent.End -> speaking = false
        }
    }

    /** Avanza la animación hasta el instante `t` (segundos). Se puede llamar varias veces por fotograma. */
    fun step(t: Float) {
        val dt = (t - lastT).coerceIn(0f, 0.1f)
        lastT = t
        kickRaw *= (1f - minOf(1f, dt * 9f))
        baseRaw += ((if (speaking) 1f else 0f) - baseRaw) * minOf(1f, dt * 6f)
        if (speaking && t - lastWordT > 0.5f && t >= nextSynthT) {
            // Sin eventos reales: pulsos con ritmo de habla (unas 3-4 sílabas por segundo, con algo de irregularidad).
            kickRaw = 0.75f
            nextSynthT = t + 0.22f + 0.16f * (0.5f + 0.5f * sin(t * 13.37f))
        }
        if (!speaking && kickRaw < 0.001f) kickRaw = 0f
    }
}
