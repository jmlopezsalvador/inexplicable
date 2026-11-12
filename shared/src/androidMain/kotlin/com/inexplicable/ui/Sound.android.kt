package com.inexplicable.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** Android: el `Synth` común alimenta un `AudioTrack` en streaming desde un hilo propio. */
private class AndroidSoundEngine : SoundEngine {
    private val sampleRate = 22050
    private val synth = Synth(sampleRate)
    @Volatile private var running = true
    @Volatile private var paused = false
    private var track: AudioTrack? = null
    private val thread: Thread

    init {
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuf, 4096) * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build().also { it.play() }
        } catch (e: Exception) {
            null // sin audio disponible: la app sigue sin sonido
        }
        thread = Thread {
            val floats = FloatArray(1024)
            val shorts = ShortArray(1024)
            while (running) {
                val t = track
                if (t == null || paused) { Thread.sleep(50); continue }
                synth.render(floats, floats.size)
                for (i in floats.indices) shorts[i] = (floats[i] * 32767f).toInt().toShort()
                t.write(shorts, 0, shorts.size) // bloquea hasta que hay hueco: marca el ritmo
            }
        }.apply { isDaemon = true; name = "inexplicable-sound"; start() }
    }

    override fun volume(v: Float) { synth.master = v.coerceIn(0f, 1f) }
    override fun ambient(on: Boolean) { synth.ambientOn = on }
    override fun analyzing(on: Boolean) { synth.analyzeOn = on }
    override fun ping() = synth.ping()
    override fun reveal() = synth.reveal()
    override fun pause() { paused = true; track?.pause() }
    override fun resume() { paused = false; track?.play() }
    override fun release() {
        running = false
        try { thread.join(300) } catch (_: InterruptedException) {}
        track?.release(); track = null
    }
}

actual fun createSoundEngine(): SoundEngine = AndroidSoundEngine()

/** Avisa de que la actividad pasa a segundo plano o vuelve (para parar el sonido). */
@Composable
actual fun LifecycleEffect(onPause: () -> Unit, onResume: () -> Unit) {
    val ctx = LocalContext.current
    DisposableEffect(ctx) {
        val owner = ctx as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> onPause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> onResume()
                else -> {}
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
}
