package com.inexplicable.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Decodifica una imagen (JPEG/PNG) a un bitmap de Compose. Null si no se puede. Android: `BitmapFactory`; iOS: Skia. */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?

@OptIn(ExperimentalEncodingApi::class)
fun bytesToBase64(bytes: ByteArray): String = Base64.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
fun base64ToBytes(text: String): ByteArray? = try { Base64.decode(text) } catch (e: IllegalArgumentException) { null }

/**
 * Pantalla de cámara a pantalla completa con un marco guía. Devuelve la foto como JPEG derecho (`onResult(bytes)`) o `null` si se cancela
 * o no hay permiso/cámara.
 * - `frameAspect`: proporción ancho/alto del marco (una carta es 63/88).
 * - `cropToFrame`: si es true la foto se recorta al marco; si no, se devuelve entera y el marco solo guía.
 * Android: CameraX. iOS: cámara nativa de Swift (`CameraBridge`), que presenta su propia pantalla.
 */
@Composable
expect fun CameraCapture(frameAspect: Float, cropToFrame: Boolean, hint: String, cancelLabel: String, onResult: (ByteArray?) -> Unit)

/**
 * Escucha continua del micrófono con reconocimiento de voz del sistema. `onHeard` recibe frases completas (cuando el hablante hace una pausa).
 * Android: `SpeechRecognizer`. iOS: `SFSpeechRecognizer` desde Swift (`ListenBridge`). Hay que parar la escucha mientras la app habla.
 */
interface Listener {
    /** Empieza (o reanuda) a escuchar en `lang`. Pide permisos si faltan. */
    fun start(lang: String, onHeard: (String) -> Unit)
    fun stop()
    fun release()
}

@Composable
expect fun rememberListener(): Listener
