package com.inexplicable.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

actual fun decodeImage(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is LifecycleOwner) return c
        c = c.baseContext
    }
    return null
}

private val mainHandler = Handler(Looper.getMainLooper())
private val mainExecutor = Executor { mainHandler.post(it) }

// ---------- Cámara ----------

/** Rota, recorta (opcional, `crop` en píxeles del bitmap ya rotado) y reduce una foto; devuelve JPEG. */
private fun processPhoto(src: Bitmap, rotation: Int, crop: android.graphics.Rect?, maxSide: Int): ByteArray {
    var bmp = src
    if (rotation != 0) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
    if (crop != null) {
        val r = android.graphics.Rect(max(0, crop.left), max(0, crop.top), min(bmp.width, crop.right), min(bmp.height, crop.bottom))
        if (r.width() > 8 && r.height() > 8) bmp = Bitmap.createBitmap(bmp, r.left, r.top, r.width(), r.height())
    }
    val longest = max(bmp.width, bmp.height)
    if (longest > maxSide) {
        val k = maxSide.toFloat() / longest
        bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true)
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return out.toByteArray()
}

@Composable
actual fun CameraCapture(frameAspect: Float, cropToFrame: Boolean, hint: String, cancelLabel: String, onResult: (ByteArray?) -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) onResult(null)
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    if (!granted) { Box(Modifier.fillMaxSize().background(Color.Black)); return }

    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val worker = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { worker.shutdown() } }
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    var busy by remember { mutableStateOf(false) }

    // Marco guía: centrado, algo por encima del centro.
    val frameW = min(size.width * 0.8f, size.height * 0.62f * frameAspect)
    val frameH = frameW / frameAspect
    val frameLeft = (size.width - frameW) / 2
    val frameTop = size.height * 0.44f - frameH / 2

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { size = it }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                val view = PreviewView(c).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                val owner = c.findLifecycleOwner()
                val future = ProcessCameraProvider.getInstance(c)
                future.addListener({
                    try {
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        provider.unbindAll()
                        val selector = try {
                            if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA
                            else CameraSelector.DEFAULT_FRONT_CAMERA
                        } catch (e: Exception) { CameraSelector.DEFAULT_BACK_CAMERA }
                        if (owner != null) provider.bindToLifecycle(owner, selector, preview, imageCapture) else onResult(null)
                    } catch (e: Exception) {
                        onResult(null) // sin cámara disponible
                    }
                }, mainExecutor)
                view
            },
        )
        // Oscurece fuera del marco y lo dibuja.
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            val corner = CornerRadius(18f, 18f)
            drawRect(Color.Black.copy(alpha = 0.55f))
            drawRoundRect(Color.Transparent, Offset(frameLeft, frameTop), Size(frameW, frameH), corner, blendMode = BlendMode.Clear)
            drawRoundRect(Color(0xFF3DDCFF), Offset(frameLeft, frameTop), Size(frameW, frameH), corner, style = Stroke(4f))
        }
        Text(
            hint, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp, start = 24.dp, end = 24.dp),
        )
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 40.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton({ onResult(null) }) { Text(cancelLabel, color = Color.White) }
            Box(
                Modifier.size(76.dp).border(4.dp, Color.White, CircleShape).padding(8.dp)
                    .background(if (busy) Color.Gray else Color.White, CircleShape)
                    .clickable(enabled = !busy) {
                        busy = true
                        imageCapture.takePicture(worker, object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bytes = try {
                                    val rotation = image.imageInfo.rotationDegrees
                                    val bmp = image.toBitmap()
                                    image.close()
                                    var crop: android.graphics.Rect? = null
                                    if (cropToFrame) {
                                        // Del marco en la vista a píxeles de la foto (la vista recorta la foto con «fill center»).
                                        val iw = if (rotation % 180 == 0) bmp.width else bmp.height
                                        val ih = if (rotation % 180 == 0) bmp.height else bmp.width
                                        val scale = max(size.width / iw.toFloat(), size.height / ih.toFloat())
                                        val ox = (iw * scale - size.width) / 2
                                        val oy = (ih * scale - size.height) / 2
                                        crop = android.graphics.Rect(
                                            ((frameLeft + ox) / scale).toInt(), ((frameTop + oy) / scale).toInt(),
                                            ((frameLeft + frameW + ox) / scale).toInt(), ((frameTop + frameH + oy) / scale).toInt(),
                                        )
                                    }
                                    processPhoto(bmp, rotation, crop, if (cropToFrame) 720 else 1280)
                                } catch (e: Exception) { null }
                                mainHandler.post { busy = false; onResult(bytes) }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                mainHandler.post { busy = false; onResult(null) }
                            }
                        })
                    },
            )
            Box(Modifier.size(64.dp)) // equilibra la fila
        }
    }
}

// ---------- Escucha ----------

private class AndroidListener(private val ctx: Context) : Listener {
    var askPermission: (() -> Unit)? = null
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var pendingStart = false
    private var lang = "es"
    private var onHeard: ((String) -> Unit)? = null

    private fun hasPermission() = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun start(lang: String, onHeard: (String) -> Unit) {
        this.lang = lang
        this.onHeard = onHeard
        if (active) return
        if (!hasPermission()) { pendingStart = true; askPermission?.invoke(); return }
        active = true
        listenOnce()
    }

    fun onPermissionGranted() {
        if (pendingStart) { pendingStart = false; start(lang, onHeard ?: return) }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onResults(results: Bundle?) {
            val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!best.isNullOrBlank()) onHeard?.invoke(best)
            restart(200)
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> active = false
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> { recognizer?.destroy(); recognizer = null; restart(800) }
                else -> restart(500) // silencio, no se entendió, red…: se vuelve a escuchar
            }
        }
    }

    private fun restart(delayMs: Long) {
        mainHandler.postDelayed({ if (active) listenOnce() }, delayMs)
    }

    private fun listenOnce() {
        if (!active) return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { active = false; return }
        try {
            val rec = recognizer ?: SpeechRecognizer.createSpeechRecognizer(ctx).also {
                it.setRecognitionListener(listener); recognizer = it
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            }
            rec.startListening(intent)
        } catch (e: Exception) {
            restart(1500)
        }
    }

    override fun stop() {
        active = false
        pendingStart = false
        try { recognizer?.cancel() } catch (_: Exception) {}
    }

    override fun release() {
        active = false
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }
}

@Composable
actual fun rememberListener(): Listener {
    val ctx = LocalContext.current
    val listener = remember { AndroidListener(ctx) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) listener.onPermissionGranted()
    }
    listener.askPermission = { launcher.launch(Manifest.permission.RECORD_AUDIO) }
    DisposableEffect(listener) { onDispose { listener.release() } }
    return listener
}
