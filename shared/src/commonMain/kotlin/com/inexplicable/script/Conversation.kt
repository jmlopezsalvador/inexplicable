package com.inexplicable.script

import com.inexplicable.effect.Stage
import kotlin.math.max
import kotlin.math.min

/** Acciones que puede disparar el mago en una conversación guionizada. */
enum class ScriptAction { Greet, Link, Analyze, Open, Continue }

/**
 * Una línea del guion: el texto del botón de respaldo, las frases que la disparan (lo que dice el mago)
 * y lo que responde la app (se dice con la voz y se muestra como subtítulo).
 */
data class ScriptLine(val label: String, val phrases: List<String>, val reply: String)

/** Guion completo de un idioma. `wake` es la palabra de activación («jarvis»). */
data class ScriptBook(
    val wake: String,
    val lines: Map<ScriptAction, ScriptLine>,
    val linked: String,      // lo que dice la app al terminar de enlazar con la baraja
    val finished: String,    // lo que dice al acabar todas las fases
) {
    fun line(a: ScriptAction): ScriptLine = lines.getValue(a)
}

object ScriptBooks {
    fun forLanguage(lang: String): ScriptBook = when (lang) {
        "en" -> ScriptBook(
            wake = "jarvis",
            lines = mapOf(
                ScriptAction.Greet to ScriptLine("GREET", listOf("hello jarvis", "hi jarvis", "hello jarvis how are you"),
                    "Hello. How are you? How can I help?"),
                ScriptAction.Link to ScriptLine("LINK", listOf("link with the deck", "please link with the deck", "connect to the deck", "find the deck"),
                    "Understood. Looking for the deck."),
                ScriptAction.Analyze to ScriptLine("ANALYZE", listOf("analyze the deck", "please analyze the deck", "yes please analyze the deck"),
                    "Analyzing the deck."),
                ScriptAction.Open to ScriptLine("REVEAL", listOf("show me the result", "reveal the result", "open it", "tell me the result"), ""),
                ScriptAction.Continue to ScriptLine("CONTINUE", listOf("analyze the rest of the deck", "analyze again what is left of the deck", "continue", "go on"),
                    "Of course. Analyzing the rest."),
            ),
            linked = "Deck linked. Where shall we go from here?",
            finished = "Analysis complete. Anything else?",
        )
        else -> ScriptBook(
            wake = "jarvis",
            lines = mapOf(
                ScriptAction.Greet to ScriptLine("SALUDAR", listOf("hola jarvis", "buenas jarvis", "hola jarvis que tal"),
                    "Hola. ¿Qué tal estás? ¿En qué puedo ayudarte?"),
                ScriptAction.Link to ScriptLine("ENLAZAR", listOf("enlaza con la baraja", "por favor enlaza con la baraja", "vincula la baraja", "busca la baraja"),
                    "Entendido. Buscando la baraja."),
                ScriptAction.Analyze to ScriptLine("ANALIZAR", listOf("analiza la baraja", "por favor analiza la baraja", "si por favor analiza la baraja"),
                    "Analizando la baraja."),
                ScriptAction.Open to ScriptLine("REVELAR", listOf("muestrame el resultado", "revela el resultado", "abrelo", "dime el resultado"), ""),
                ScriptAction.Continue to ScriptLine("CONTINUAR", listOf("analiza el resto de la baraja", "vuelve a analizar lo que queda de la baraja", "continua", "sigue"),
                    "De acuerdo. Analizo el resto."),
            ),
            linked = "Baraja enlazada. ¿Por dónde seguimos?",
            finished = "Análisis completo. ¿Algo más?",
        )
    }
}

/**
 * Qué acción espera el guion ahora, según la etapa del efecto. Solo se acepta esa: así lo que diga el público en otro momento
 * (o la misma frase a destiempo) no cambia nada. `null` = ahora no se espera nada (conectando, analizando, terminado).
 */
fun expectedAction(stage: Stage, greeted: Boolean): ScriptAction? = when (stage) {
    Stage.Idle -> if (greeted) ScriptAction.Link else ScriptAction.Greet
    Stage.Ready -> ScriptAction.Analyze
    Stage.Result -> ScriptAction.Open
    Stage.Revealed -> ScriptAction.Continue
    else -> null
}

/** Comparación tolerante de frases dichas con las esperadas (para el reconocimiento de voz). */
object PhraseMatcher {
    private val accents = mapOf('á' to 'a', 'à' to 'a', 'ä' to 'a', 'â' to 'a', 'é' to 'e', 'è' to 'e', 'ë' to 'e', 'ê' to 'e',
        'í' to 'i', 'ì' to 'i', 'ï' to 'i', 'î' to 'i', 'ó' to 'o', 'ò' to 'o', 'ö' to 'o', 'ô' to 'o',
        'ú' to 'u', 'ù' to 'u', 'ü' to 'u', 'û' to 'u', 'ñ' to 'n')

    /** Minúsculas, sin acentos ni signos, palabras separadas por un espacio. */
    fun normalize(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            val c = accents[ch] ?: ch
            sb.append(if (c in 'a'..'z' || c in '0'..'9') c else ' ')
        }
        return sb.toString().trim().split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun distance(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = min(min(d[j] + 1, d[j - 1] + 1), prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }

    /** Dos palabras valen igual si son iguales o (si son largas) difieren en una letra: los reconocedores se equivocan poco. */
    private fun sameWord(a: String, b: String) = a == b || (min(a.length, b.length) >= 5 && distance(a, b) <= 1)

    /** 0..1: media armónica entre cuántas palabras de la frase esperada se han oído y cuántas de las oídas eran de la frase. */
    fun score(heard: String, phrase: String): Double {
        val h = normalize(heard).split(' ').filter { it.isNotEmpty() }
        val p = normalize(phrase).split(' ').filter { it.isNotEmpty() }
        if (h.isEmpty() || p.isEmpty()) return 0.0
        val matched = p.count { w -> h.any { sameWord(it, w) } }
        val usedHeard = h.count { w -> p.any { sameWord(it, w) } }
        val recall = matched.toDouble() / p.size
        val precision = usedHeard.toDouble() / h.size
        return if (recall + precision == 0.0) 0.0 else 2 * recall * precision / (recall + precision)
    }

    fun best(heard: String, phrases: List<String>): Double = phrases.fold(0.0) { acc, ph -> max(acc, score(heard, ph)) }

    /**
     * Si `heard` encaja con alguna de `phrases`. Con `wake` (palabra de activación) la frase debe incluirla y se quita antes de comparar.
     */
    fun matches(heard: String, phrases: List<String>, threshold: Double = 0.8, wake: String? = null): Boolean {
        var text = normalize(heard)
        if (wake != null) {
            val w = normalize(wake)
            val words = text.split(' ')
            if (words.none { sameWord(it, w) }) return false
            text = words.filterNot { sameWord(it, w) }.joinToString(" ")
        }
        val candidates = if (wake != null) phrases.map { normalize(it).split(' ').filterNot { w -> sameWord(w, normalize(wake)) }.joinToString(" ") } else phrases
        return best(text, candidates) >= threshold
    }
}

/**
 * Decide qué hacer con una frase oída. `greeted` indica si ya hubo saludo. Devuelve la acción a ejecutar o null si no encaja
 * con lo que se espera ahora.
 */
fun hear(book: ScriptBook, stage: Stage, greeted: Boolean, heard: String, requireWake: Boolean = false, threshold: Double = 0.8): ScriptAction? {
    val action = expectedAction(stage, greeted) ?: return null
    val line = book.line(action)
    // El saludo ya lleva la palabra de activación dentro de la frase; para el resto se exige solo si se pide.
    val wake = if (requireWake && action != ScriptAction.Greet) book.wake else null
    return if (PhraseMatcher.matches(heard, line.phrases, threshold, wake)) action else null
}
