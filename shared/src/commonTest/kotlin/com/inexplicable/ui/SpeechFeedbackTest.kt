package com.inexplicable.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeechFeedbackTest {
    @Test fun silentWhenNotSpeaking() {
        val f = SpeechFeedback()
        for (i in 1..30) f.step(i * 0.016f)
        assertEquals(0f, f.kick)
        assertEquals(0f, f.level, 1e-6f)
    }

    @Test fun wordGivesKickThatDecays() {
        val f = SpeechFeedback()
        f.onEvent(SpeechEvent.Start, 0f)
        f.onEvent(SpeechEvent.Word, 0.01f)
        assertTrue(f.kick > 0.9f)
        var t = 0.01f
        repeat(6) { t += 0.016f; f.step(t) } // sigue hablando, pero llegan palabras reales: sin pulsos sintéticos
        assertTrue(f.kick < 0.9f && f.kick > 0f)
    }

    @Test fun syntheticPulsesWhenNoWordEvents() {
        val f = SpeechFeedback()
        f.onEvent(SpeechEvent.Start, 0f)
        var maxKick = 0f
        var t = 0f
        repeat(120) { t += 0.016f; f.step(t); if (f.kick > maxKick) maxKick = f.kick }
        assertTrue(maxKick > 0.5f, "sin eventos por palabra debe haber pulsos sintéticos")
        assertTrue(f.level > 0f)
    }

    @Test fun fadesAfterEndAndGainScales() {
        val f = SpeechFeedback()
        f.onEvent(SpeechEvent.Start, 0f)
        f.onEvent(SpeechEvent.Word, 0f)
        f.onEvent(SpeechEvent.End, 0.1f)
        var t = 0.1f
        repeat(120) { t += 0.016f; f.step(t) }
        assertEquals(0f, f.kick)
        assertTrue(f.level < 0.01f)
        f.gain = 0f
        f.onEvent(SpeechEvent.Word, t)
        assertEquals(0f, f.kick) // con la reacción apagada nunca se mueve nada
    }
}
