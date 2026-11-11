package com.inexplicable

import com.inexplicable.effect.EffectEngine
import com.inexplicable.effect.EffectParams
import com.inexplicable.effect.Stage
import com.inexplicable.effect.Texts
import kotlin.test.Test
import kotlin.test.assertEquals

class EffectEngineTest {
    private fun engine(n: Int = 2) = EffectEngine(Texts.forLanguage("es").phases.take(n))

    @Test fun fullFlow() {
        val e = engine()
        assertEquals(Stage.Connecting, e.state.stage)
        e.analyze() // ignorado: aún no está conectado
        assertEquals(Stage.Connecting, e.state.stage)
        e.connected()
        e.analyze(); e.revealed()
        assertEquals(Stage.Revealed, e.state.stage)
        assertEquals(0, e.state.phase)
        e.next()
        assertEquals(Stage.Ready, e.state.stage)
        assertEquals(1, e.state.phase)
        e.analyze(); e.revealed(); e.next()
        assertEquals(Stage.Finished, e.state.stage)
    }

    @Test fun resetReturnsToStart() {
        val e = engine()
        e.connected(); e.analyze(); e.revealed(); e.next()
        e.reset()
        assertEquals(Stage.Connecting, e.state.stage)
        assertEquals(0, e.state.phase)
    }

    @Test fun defaultTextsEsAndEn() {
        val es = Texts.forLanguage("es", EffectParams(24, 13, "7 de picas")).phases
        assertEquals("Hay 24 cartas cara abajo.", es[0].text)
        assertEquals("Excepto el 7 de picas.", es[3].text)
        assertEquals("Of those, 13 are red.", Texts.forLanguage("en").phases[1].text)
        assertEquals(4, Texts.forLanguage("fr").phases.size) // idioma desconocido → castellano
    }
}
