package com.inexplicable.ui

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Efectos de sonido de la app. Android: sintetizador en `AudioTrack`. iOS: sintetizador en Swift (`SoundEngine.swift`). */
interface SoundEngine {
    fun volume(v: Float)
    fun ambient(on: Boolean)
    fun analyzing(on: Boolean)
    fun ping()
    fun reveal()
    fun pause()
    fun resume()
    fun release()
}

expect fun createSoundEngine(): SoundEngine

/**
 * Sintetizador mono sin recursos ni ficheros: ruido blanco filtrado de ambiente, barrido con trémolo mientras se analiza,
 * «ping» al aparecer el resultado y «whoosh» al abrirlo. Puro y comprobable; `render` rellena muestras en -1..1.
 * (La versión de iOS en Swift replica estas mismas fórmulas.)
 */
class Synth(private val sampleRate: Int) {
    var master = 0.6f
    var ambientOn = false
    var analyzeOn = false

    private var ambGain = 0f
    private var anaGain = 0f
    private var noiseLp = 0f
    private var humPhase = 0f
    private var anaPhase = 0f
    private var time = 0.0 // Double: en Float dejaría de avanzar tras unos minutos
    private var rng = 2463534242L
    private var pingT = -1f
    private var revT = -1f
    private var revLp = 0f

    fun ping() { pingT = 0f }
    fun reveal() { revT = 0f }

    private fun noise(): Float {
        rng = rng xor (rng shl 13) and 0xFFFFFFFFL
        rng = rng xor (rng shr 17)
        rng = rng xor (rng shl 5) and 0xFFFFFFFFL
        return (rng.toDouble() / 2147483648.0 - 1.0).toFloat()
    }

    fun render(out: FloatArray, count: Int) {
        val dt = 1f / sampleRate
        val slew = dt / 0.35f
        val twoPi = (2 * PI).toFloat()
        for (i in 0 until count) {
            time += dt
            // Tiempo acotado a 200 s (múltiplo de todos los periodos usados: 8 s, 1,6 s y 1/6 s) para no perder precisión.
            val tf = (time % 200.0).toFloat()
            ambGain += ((if (ambientOn) 1f else 0f) - ambGain).coerceIn(-slew, slew)
            anaGain += ((if (analyzeOn) 1f else 0f) - anaGain).coerceIn(-slew, slew)
            var s = 0f

            if (ambGain > 0f) {
                noiseLp += 0.06f * (noise() - noiseLp)
                humPhase += twoPi * 55f * dt
                if (humPhase > twoPi) humPhase -= twoPi
                val lfo = 0.75f + 0.25f * sin(twoPi * 0.125f * tf)
                s += ambGain * lfo * (noiseLp * 0.55f + sin(humPhase) * 0.05f)
            }
            if (anaGain > 0f) {
                val cycle = (tf / 1.6f) % 1f
                val f = 220f + 660f * cycle
                anaPhase += twoPi * f * dt
                if (anaPhase > 2 * twoPi) anaPhase -= 2 * twoPi // 4π: así también es continuo el armónico ×1,5
                val trem = 0.7f + 0.3f * sin(twoPi * 6f * tf)
                s += anaGain * trem * (sin(anaPhase) * 0.10f + sin(anaPhase * 1.5f) * 0.04f + noise() * 0.015f)
            }
            if (pingT >= 0f) {
                val e = exp(-pingT * 9f)
                s += e * (sin(twoPi * 1320f * pingT) * 0.30f + sin(twoPi * 1980f * pingT) * 0.10f)
                pingT += dt
                if (pingT > 0.7f) pingT = -1f
            }
            if (revT >= 0f) {
                val k = revT / 0.9f
                val bell = sin((PI * k).toFloat()).let { it * it }
                revLp += (0.02f + 0.5f * k) * (noise() - revLp)
                s += bell * revLp * 0.6f + exp(-revT * 6f) * sin(twoPi * 110f * revT) * 0.35f
                revT += dt
                if (revT > 0.9f) revT = -1f
            }
            out[i] = (s * master).coerceIn(-1f, 1f)
        }
    }
}
