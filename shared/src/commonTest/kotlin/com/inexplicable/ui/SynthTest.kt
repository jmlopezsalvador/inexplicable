package com.inexplicable.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SynthTest {
    private fun energy(s: Synth, n: Int = 22050): Float {
        val buf = FloatArray(n); s.render(buf, n)
        assertTrue(buf.all { abs(it) <= 1f }, "fuera de rango")
        return buf.sumOf { (it * it).toDouble() }.toFloat() / n
    }

    @Test fun silentByDefault() = assertEquals(0f, energy(Synth(22050)))

    @Test fun ambientMakesNoiseAndFadesOut() {
        val s = Synth(22050)
        s.ambientOn = true
        assertTrue(energy(s) > 1e-5f)
        s.ambientOn = false
        energy(s) // desvanecimiento
        assertEquals(0f, energy(s), 1e-9f)
    }

    @Test fun pingAndRevealAreOneShots() {
        val s = Synth(22050)
        s.ping()
        assertTrue(energy(s, 4000) > 1e-4f)
        energy(s, 20000) // deja pasar el resto del ping (0,7 s)
        assertEquals(0f, energy(s), 1e-9f) // ya terminó
        s.reveal()
        assertTrue(energy(s, 8000) > 1e-5f)
    }

    @Test fun analyzeAddsEnergy() {
        val s = Synth(22050)
        s.analyzeOn = true
        assertTrue(energy(s) > 1e-5f)
    }
}
