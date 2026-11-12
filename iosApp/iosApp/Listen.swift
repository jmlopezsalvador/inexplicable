import Speech
import AVFoundation
import Shared

// Reconocimiento de voz continuo para el control por conversación. Kotlin manda «start»/«stop» por `IosListenHooks` y esta clase
// devuelve cada frase completa (cuando el hablante hace una pausa) con `IosListenHooks.deliver`.
// Limitaciones: el reconocedor de Apple corta cada petición a ~1 min, así que se reinicia tras cada frase; el micrófono abierto muestra
// el indicador de iOS; el audio de la propia app (voz de Jarvis) lo filtra Kotlin ignorando lo oído mientras la app habla.

final class SpeechListener {
    private var recognizer: SFSpeechRecognizer?
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private let engine = AVAudioEngine()
    private var active = false
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
                    DispatchQueue.main.async { if granted { self.begin() } }
                }
            }
        }
    }

    private func begin() {
        if active { return }
        active = true
        // Grabar y reproducir a la vez (la voz de Jarvis y los efectos siguen sonando).
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .mixWithOthers, .allowBluetooth])
        try? session.setActive(true)
        startTask()
    }

    private func startTask() {
        guard active else { return }
        generation += 1
        let gen = generation
        let rec = SFSpeechRecognizer(locale: Locale(identifier: lang == "en" ? "en-US" : "es-ES"))
        recognizer = rec
        guard let recognizer = rec, recognizer.isAvailable else { active = false; return }

        let req = SFSpeechAudioBufferRecognitionRequest()
        req.shouldReportPartialResults = true
        if recognizer.supportsOnDeviceRecognition { req.requiresOnDeviceRecognition = true }
        request = req

        let input = engine.inputNode
        input.removeTap(onBus: 0)
        let format = input.outputFormat(forBus: 0)
        input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
            self?.request?.append(buffer)
        }
        engine.prepare()
        do { try engine.start() } catch { active = false; return }

        task = recognizer.recognitionTask(with: req) { [weak self] result, error in
            DispatchQueue.main.async {
                guard let self = self, self.generation == gen else { return }
                if let r = result { self.onPartial(r.bestTranscription.formattedString) }
                if error != nil || (result?.isFinal ?? false) { self.flush() }
            }
        }
    }

    /// Cada texto parcial reinicia un temporizador de silencio; al cumplirse, la frase se da por terminada.
    private func onPartial(_ text: String) {
        lastText = text
        silenceTimer?.invalidate()
        silenceTimer = Timer.scheduledTimer(withTimeInterval: 1.2, repeats: false) { [weak self] _ in self?.flush() }
    }

    private func flush() {
        silenceTimer?.invalidate()
        let text = lastText.trimmingCharacters(in: .whitespacesAndNewlines)
        lastText = ""
        if !text.isEmpty { IosListenHooks.shared.deliver(text: text) }
        teardown()
        guard active else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
            if self?.active == true { self?.startTask() }
        }
    }

    private func teardown() {
        generation += 1
        task?.cancel(); task = nil
        request?.endAudio(); request = nil
        if engine.isRunning { engine.stop() }
        engine.inputNode.removeTap(onBus: 0)
    }

    func stop() {
        active = false
        silenceTimer?.invalidate()
        lastText = ""
        teardown()
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
        if cmd == "stop" { listener.stop(); return }
        let p = cmd.components(separatedBy: "\u{1F}")
        if p.count >= 2, p[0] == "start" { listener.start(lang: p[1]) }
    }
}
