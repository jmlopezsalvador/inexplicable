package com.inexplicable

import com.inexplicable.effect.Stage
import com.inexplicable.script.PhraseMatcher
import com.inexplicable.script.ScriptAction
import com.inexplicable.script.ScriptBooks
import com.inexplicable.script.expectedAction
import com.inexplicable.script.hear
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationTest {
    private val es = ScriptBooks.forLanguage("es")

    @Test fun normalizeRemovesAccentsAndPunctuation() {
        assertEquals("si por favor analiza la baraja", PhraseMatcher.normalize("¡Sí, por favor: ANALIZA la baraja!"))
    }

    @Test fun matchesVariationsAndIgnoresNoise() {
        val ph = es.line(ScriptAction.Analyze).phrases
        assertTrue(PhraseMatcher.matches("sí, por favor, analiza la baraja", ph))
        assertTrue(PhraseMatcher.matches("analiza la baraja", ph))
        assertTrue(PhraseMatcher.matches("analisa la baraja", ph)) // error típico del reconocedor (una letra)
        assertFalse(PhraseMatcher.matches("hostia qué chulo", ph))
        assertFalse(PhraseMatcher.matches("la baraja está sobre la mesa", ph)) // palabras sueltas del público
        assertFalse(PhraseMatcher.matches("", ph))
    }

    @Test fun wakeWordIsRequiredWhenAsked() {
        val ph = es.line(ScriptAction.Analyze).phrases
        assertFalse(PhraseMatcher.matches("analiza la baraja", ph, wake = "jarvis"))
        assertTrue(PhraseMatcher.matches("Jarvis, analiza la baraja", ph, wake = "jarvis"))
    }

    @Test fun expectedActionFollowsStage() {
        assertEquals(ScriptAction.Greet, expectedAction(Stage.Idle, false))
        assertEquals(ScriptAction.Link, expectedAction(Stage.Idle, true))
        assertEquals(ScriptAction.Analyze, expectedAction(Stage.Ready, true))
        assertEquals(ScriptAction.Open, expectedAction(Stage.Result, true))
        assertEquals(ScriptAction.Continue, expectedAction(Stage.Revealed, true))
        assertNull(expectedAction(Stage.Analyzing, true))
        assertNull(expectedAction(Stage.Finished, true))
    }

    @Test fun onlyTheExpectedActionFires() {
        // Con la baraja lista solo cuenta «analiza»; la frase de enlazar a destiempo no hace nada.
        assertEquals(ScriptAction.Analyze, hear(es, Stage.Ready, true, "analiza la baraja"))
        assertNull(hear(es, Stage.Ready, true, "enlaza con la baraja"))
        assertNull(hear(es, Stage.Analyzing, true, "analiza la baraja"))
        assertEquals(ScriptAction.Greet, hear(es, Stage.Idle, false, "hola jarvis"))
        assertEquals(ScriptAction.Link, hear(es, Stage.Idle, true, "por favor, enlaza con la baraja"))
    }

    @Test fun englishBookWorks() {
        val en = ScriptBooks.forLanguage("en")
        assertEquals(ScriptAction.Analyze, hear(en, Stage.Ready, true, "yes please analyze the deck"))
    }
}
