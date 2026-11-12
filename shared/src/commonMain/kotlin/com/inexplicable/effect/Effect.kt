package com.inexplicable.effect

/** Una fase del efecto: lo que la app «revela» al analizar. */
data class Phase(val text: String)

/** Etapas del lector. */
enum class Stage {
    /** Efecto de «conexión» con la baraja. */
    Connecting,

    /** Conectado: el botón Analizar está activo. */
    Ready,

    /** Animación de análisis de la fase actual. */
    Analyzing,

    /** Análisis terminado: aparece un «resultado» que el mago debe pulsar para abrirlo. */
    Result,

    /** Resultado abierto: predicción de la fase actual mostrada. */
    Revealed,

    /** Todas las fases reveladas. */
    Finished,
}

data class EffectState(val stage: Stage = Stage.Connecting, val phase: Int = 0)

/**
 * Máquina de estados pura (sin UI ni tiempo): la interfaz lanza los eventos cuando acaban sus animaciones.
 * Connecting → Ready → Analyzing → Result → Revealed → (Ready de la siguiente fase | Finished).
 */
class EffectEngine(val phases: List<Phase>) {
    var state: EffectState = EffectState()
        private set

    val current: Phase? get() = phases.getOrNull(state.phase)

    /** Acaba el efecto de conexión. */
    fun connected() {
        if (state.stage == Stage.Connecting) state = state.copy(stage = Stage.Ready)
    }

    /** El mago pulsa «Analizar». */
    fun analyze() {
        if (state.stage == Stage.Ready) state = state.copy(stage = Stage.Analyzing)
    }

    /** Acaba la animación de análisis: aparece el resultado pulsable. */
    fun resultReady() {
        if (state.stage == Stage.Analyzing) state = state.copy(stage = Stage.Result)
    }

    /** Se pulsa el resultado: se abre y se muestra la predicción. */
    fun revealed() {
        if (state.stage == Stage.Result) state = state.copy(stage = Stage.Revealed)
    }

    /** Pasa a la siguiente fase (o termina). */
    fun next() {
        if (state.stage != Stage.Revealed) return
        val n = state.phase + 1
        state = if (n < phases.size) EffectState(Stage.Ready, n) else state.copy(stage = Stage.Finished)
    }

    /** Reset oculto: deja el efecto preparado de nuevo. */
    fun reset() {
        state = EffectState()
    }
}

/** Parámetros del efecto por defecto (editables en la configuración en fases posteriores). */
data class EffectParams(
    val faceDown: Int = 24,
    val red: Int = 13,
    val extraCard: String = "",
)

/** Textos de la interfaz del lector por idioma. */
data class UiTexts(
    val title: String,
    val connecting: List<String>,
    val ready: String,
    val analyzeButton: String,
    val analyzing: String,
    val nextButton: String,
    val tapToReveal: String,
    val finished: String,
    val phases: List<Phase>,
)

object Texts {
    fun forLanguage(lang: String, p: EffectParams = EffectParams()): UiTexts = when (lang) {
        "en" -> UiTexts(
            title = "INEXPLICABLE",
            connecting = listOf("Searching for deck…", "Syncing with the deck…", "Reading card orientation…", "Link established"),
            ready = "DECK LINKED",
            analyzeButton = "ANALYZE",
            analyzing = "Analyzing…",
            nextButton = "CONTINUE",
            tapToReveal = "TAP TO REVEAL",
            finished = "ANALYSIS COMPLETE",
            phases = listOf(
                Phase("${p.faceDown} cards are face down."),
                Phase("Of those, ${p.red} are red."),
                Phase("Of the black ones, all are even."),
                Phase("Except the ${p.extraCard.ifBlank { "7 of spades" }}."),
            ),
        )
        else -> UiTexts(
            title = "INEXPLICABLE",
            connecting = listOf("Buscando la baraja…", "Sincronizando con el mazo…", "Leyendo la orientación de las cartas…", "Enlace establecido"),
            ready = "BARAJA ENLAZADA",
            analyzeButton = "ANALIZAR",
            analyzing = "Analizando…",
            nextButton = "CONTINUAR",
            tapToReveal = "TOCA PARA REVELAR",
            finished = "ANÁLISIS COMPLETO",
            phases = listOf(
                Phase("Hay ${p.faceDown} cartas cara abajo."),
                Phase("De esas, ${p.red} son rojas."),
                Phase("De las negras, todas son pares."),
                Phase("Excepto el ${p.extraCard.ifBlank { "7 de picas" }}."),
            ),
        )
    }
}
