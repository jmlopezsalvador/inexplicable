import Speech
import AVFoundation
import Shared

// Reconocimiento de voz continuo para el control por conversación. Kotlin manda órdenes de texto por `IosListenHooks` («start», «stop»,
// «pause», «resume», «reset») y esta clase devuelve el texto a medias con `deliverPartial` y cada frase completa con `deliver`.
//
// Diseño:
// - El motor de audio (micrófono) se mantiene abierto mientras se escucha y solo se renuevan la petición y la tarea de reconocimiento entre
//   frases: así no se pierde audio al reiniciar (antes había un hueco y por eso a veces había que repetir).
// - «pause» apaga el micrófono de verdad (la luz del sistema desaparece) y «resume» lo vuelve a encender. Kotlin pausa mientras la app habla,
//   para que el reconocedor no transcriba la voz de la propia app.
// - Cada texto parcial se entrega enseguida; la frase se da por completa tras 1,2 s de silencio.
// - Robustez: el motor se crea solo al empezar, nunca se toca `inputNode` sin escuchar y antes de instalar el «tap» se comprueba que el
//   formato del micrófono sea válido (un formato inválido lanza una excepción de Objective-C que cierra la app).

final class SpeechListener {
    private var engine: AVAudioEngine?
    private var recognizer: SFSpeechRecognizer?
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var active = false
    private var paused = false
    private var lang = "es"
    private var lastText = ""
    private var silenceTimer: Timer?
    private var generation = 0   // invalida las respuestas de tareas ya canceladas

    func start(lang: String) {
        self.lang = lang
        if active { return }
        SFSpeechRecognizer.requestAuthorization { status in
            DispatchQueue.main.async {
                guard status == .authorized else { return }
                AVAudioSession.sharedInstance().requestRecordPermission { granted in
                    // Se espera un instante tras el aviso de permiso: la sesión de audio aún se está reconfigurando.
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { if granted { self.begin() } }
                }
            }
        }
    }

    private func begin() {
        if active { return }
        // Grabar y reproducir a la vez; por el altavoz (no por el auricular) y mezclando con otras apps.
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .mixWithOthers, .allowBluetooth])
            try session.setActive(true)
        } catch {
            return
        }
        AppAudio.recording = true
        active = true
        paused = false
        if startEngine() { startTask() } else { stop() }
    }

    /// Enciende el micrófono. Devuelve false si no hay un micrófono utilizable.
    private func startEngine() -> Bool {
        if engine != nil { return true }
        let newEngine = AVAudioEngine()
        let input = newEngine.inputNode
        let format = input.outputFormat(forBus: 0)
        // Sin micrófono utilizable (formato a 0 Hz o sin canales) no se instala nada: evita la excepción.
        guard format.sampleRate > 0, format.channelCount > 0 else { return false }
        input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
            self?.request?.append(buffer)   // la petición actual (puede cambiar entre frases sin tocar el motor)
        }
        newEngine.prepare()
        do { try newEngine.start() } catch {
            input.removeTap(onBus: 0)
            return false
        }
        engine = newEngine
        return true
    }

    /// Apaga el micrófono.
    private func stopEngine() {
        if let engine = engine {
            if engine.isRunning { engine.stop() }
            engine.inputNode.removeTap(onBus: 0)
        }
        engine = nil
    }

    /// Empieza una petición de reconocimiento nueva (el micrófono ya debe estar encendido).
    private func startTask() {
        guard active, !paused, engine != nil else { return }
        generation += 1
        let gen = generation
        if recognizer == nil {
            recognizer = SFSpeechRecognizer(locale: Locale(identifier: lang == "en" ? "en-US" : "es-ES"))
        }
        guard let recognizer = recognizer, recognizer.isAvailable else { stop(); return }

        let req = SFSpeechAudioBufferRecognitionRequest()
        req.shouldReportPartialResults = true
        if recognizer.supportsOnDeviceRecognition { req.requiresOnDeviceRecognition = true }
        request = req

        task = recognizer.recognitionTask(with: req) { [weak self] result, error in
            DispatchQueue.main.async {
                guard let self = self, self.generation == gen else { return }
                if let r = result { self.onPartial(r.bestTranscription.formattedString) }
                if error != nil || (result?.isFinal ?? false) { self.finishPhrase() }
            }
        }
    }

    /// Cierra la petición actual (sin tocar el micrófono).
    private func stopTask() {
        generation += 1
        silenceTimer?.invalidate()
        task?.cancel(); task = nil
        request?.endAudio(); request = nil
        lastText = ""
    }

    /// Cada texto parcial se entrega enseguida y reinicia un temporizador de silencio; al cumplirse, la frase se da por completa.
    private func onPartial(_ text: String) {
        lastText = text
        IosListenHooks.shared.deliverPartial(text: text)
        silenceTimer?.invalidate()
        silenceTimer = Timer.scheduledTimer(withTimeInterval: 1.2, repeats: false) { [weak self] _ in self?.finishPhrase() }
    }

    /// La frase terminó: se entrega completa y se prepara una petición nueva, con el micrófono siempre encendido.
    private func finishPhrase() {
        let text = lastText.trimmingCharacters(in: .whitespacesAndNewlines)
        stopTask()
        if !text.isEmpty { IosListenHooks.shared.deliver(text: text) }
        guard active, !paused else { return }
        startTask()
    }

    // MARK: Órdenes de Kotlin

    /// Apaga el micrófono sin cerrar la escucha (Kotlin lo usa mientras la app habla).
    func pause() {
        guard active, !paused else { return }
        paused = true
        stopTask()
        stopEngine()
    }

    func resume() {
        guard active, paused else { return }
        paused = false
        if startEngine() { startTask() } else { stop() }
    }

    /// Descarta lo reconocido y empieza una frase nueva (Kotlin lo usa tras actuar con una frase parcial).
    func reset() {
        guard active, !paused else { return }
        stopTask()
        startTask()
    }

    func stop() {
        let wasActive = active
        active = false
        paused = false
        stopTask()
        stopEngine()
        recognizer = nil
        if wasActive {
            AppAudio.recording = false
            AppAudio.playback() // vuelve a la sesión de solo reproducción
        }
    }
}

/// Une las órdenes de texto de Kotlin con el reconocedor.
enum ListenBridge {
    static let listener = SpeechListener()

    static func install() {
        IosListenHooks.shared.handler = { cmd in
            DispatchQueue.main.async { handle(cmd) }
        }
    }

    private static func handle(_ cmd: String) {
        switch cmd {
        case "stop": listener.stop()
        case "pause": listener.pause()
        case "resume": listener.resume()
        case "reset": listener.reset()
        default:
            let p = cmd.components(separatedBy: "\u{1F}")
            if p.count >= 2, p[0] == "start" { listener.start(lang: p[1]) }
        }
    }
}
