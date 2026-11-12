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
 * Abre la galería para elegir una imagen y devuelve un JPEG derecho y reducido (lado mayor ≤ 1280 px), o `null` si se cancela.
 * Android: selector del sistema. iOS: `PHPickerViewController` desde Swift (`Gallery.swift`).
 */
@Composable
expect fun GalleryPicker(onResult: (ByteArray?) -> Unit)

/**
 * Escucha continua del micrófono con reconocimiento de voz del sistema.
 * - `onPartial` recibe el texto mientras se está diciendo (se va completando): permite reaccionar sin esperar al silencio.
 * - `onHeard` recibe la frase ya completa (cuando el hablante hace una pausa).
 * Android: `SpeechRecognizer`. iOS: `SFSpeechRecognizer` desde Swift (`ListenBridge`).
 * Mientras la app habla hay que **pausar** la escucha: así el micrófono se apaga de verdad (la luz del sistema desaparece) y el reconocedor no
 * transcribe la voz de la propia app.
 */
interface Listener {
    /** Empieza (o reanuda) a escuchar en `lang`. Pide permisos si faltan. */
    fun start(lang: String, onHeard: (String) -> Unit, onPartial: (String) -> Unit)
    fun stop()
    /** Apaga el micrófono sin cerrar la sesión de escucha (se reanuda con `resume`). */
    fun pause()
    fun resume()
    /** Descarta lo que lleva reconocido y empieza una frase nueva (por ejemplo, tras haber actuado con una frase parcial). */
    fun reset()
    fun release()
}

@Composable
expect fun rememberListener(): Listener
