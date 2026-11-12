import AVFoundation
import Shared

// Sintetizador de sonido de iOS. Replica las fórmulas de `Synth` (shared/.../ui/Sound.kt): ruido blanco filtrado de ambiente,
// barrido con trémolo al analizar, «ping» al aparecer el resultado y «whoosh» al abrirlo. Si cambias uno, cambia el otro.
// Kotlin le manda órdenes de texto a través de `IosSoundHooks.handler` (ver `SoundBridge.install()`).

final class SoundSynth {
    private let engine = AVAudioEngine()
    private var node: AVAudioSourceNode?

    var master: Float = 0.6
    var ambientOn = false
    var analyzeOn = false

    private var sampleRate: Double = 44100
    private var ambGain: Float = 0
    private var anaGain: Float = 0
    private var noiseLp: Float = 0
    private var humPhase: Float = 0
    private var anaPhase: Float = 0
    private var time: Double = 0
    private var rng: UInt32 = 2463534242
    private var pingT: Float = -1
    private var revT: Float = -1
    private var revLp: Float = 0
    private var started = false

    func ping() { pingT = 0 }
    func reveal() { revT = 0 }

    private func noise() -> Float {
        rng ^= rng << 13
        rng ^= rng >> 17
        rng ^= rng << 5
        return Float(rng) / 2147483648.0 - 1.0
    }

    private func nextSample() -> Float {
        let dt = Float(1.0 / sampleRate)
        let slew = dt / 0.35
        let twoPi = Float.pi * 2
        time += Double(dt)
        let tf = Float(time.truncatingRemainder(dividingBy: 200.0))
        ambGain += min(max((ambientOn ? 1 : 0) - ambGain, -slew), slew)
        anaGain += min(max((analyzeOn ? 1 : 0) - anaGain, -slew), slew)
        var s: Float = 0

        if ambGain > 0 {
            noiseLp += 0.06 * (noise() - noiseLp)
            humPhase += twoPi * 55 * dt
            if humPhase > twoPi { humPhase -= twoPi }
            let lfo = 0.75 + 0.25 * sin(twoPi * 0.125 * tf)
            s += ambGain * lfo * (noiseLp * 0.55 + sin(humPhase) * 0.05)
        }
        if anaGain > 0 {
            let cycle = (tf / 1.6).truncatingRemainder(dividingBy: 1)
            let f = 220 + 660 * cycle
            anaPhase += twoPi * f * dt
            if anaPhase > 2 * twoPi { anaPhase -= 2 * twoPi }
            let trem = 0.7 + 0.3 * sin(twoPi * 6 * tf)
            s += anaGain * trem * (sin(anaPhase) * 0.10 + sin(anaPhase * 1.5) * 0.04 + noise() * 0.015)
        }
        if pingT >= 0 {
            let e = exp(-pingT * 9)
            s += e * (sin(twoPi * 1320 * pingT) * 0.30 + sin(twoPi * 1980 * pingT) * 0.10)
            pingT += dt
            if pingT > 0.7 { pingT = -1 }
        }
        if revT >= 0 {
            let k = revT / 0.9
            let bell = sin(Float.pi * k) * sin(Float.pi * k)
            revLp += (0.02 + 0.5 * k) * (noise() - revLp)
            s += bell * revLp * 0.6 + exp(-revT * 6) * sin(twoPi * 110 * revT) * 0.35
            revT += dt
            if revT > 0.9 { revT = -1 }
        }
        return min(max(s * master, -1), 1)
    }

    func start() {
        if started { engine.prepare(); try? engine.start(); return }
        started = true
        // `.ambient` se mezcla con otras apps y respeta el interruptor de silencio.
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        let outRate = engine.outputNode.outputFormat(forBus: 0).sampleRate
        sampleRate = outRate > 0 ? outRate : 44100
        guard let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1) else { return }
        let source = AVAudioSourceNode(format: format) { [weak self] _, _, frames, audioBufferList in
            let buffers = UnsafeMutableAudioBufferListPointer(audioBufferList)
            guard let self = self else { return noErr }
            for frame in 0..<Int(frames) {
                let v = self.nextSample()
                for buffer in buffers {
                    buffer.mData?.assumingMemoryBound(to: Float.self)[frame] = v
                }
            }
            return noErr
        }
        node = source
        engine.attach(source)
        engine.connect(source, to: engine.mainMixerNode, format: format)
        engine.prepare()
        try? engine.start()
    }

    func pause() { engine.pause() }
    func resume() { try? engine.start() }
    func stop() { engine.stop() }
}

/// Une las órdenes de texto de Kotlin con el sintetizador.
enum SoundBridge {
    static let synth = SoundSynth()

    static func install() {
        IosSoundHooks.shared.handler = { cmd in
            DispatchQueue.main.async { handle(cmd) }
        }
    }

    /// Orden de sonido desde Swift (la RA nativa la usa para el análisis, el resultado y la apertura).
    static func send(_ cmd: String) { handle(cmd) }

    private static func handle(_ cmd: String) {
        switch cmd {
        case "ambient_on": synth.start(); synth.ambientOn = true
        case "ambient_off": synth.ambientOn = false
        case "analyzing_on": synth.analyzeOn = true
        case "analyzing_off": synth.analyzeOn = false
        case "ping": synth.ping()
        case "reveal": synth.reveal()
        case "pause": synth.pause()
        case "resume": synth.resume()
        case "release": synth.ambientOn = false; synth.analyzeOn = false
        default:
            if cmd.hasPrefix("volume:"), let v = Float(cmd.dropFirst("volume:".count)) { synth.master = v }
        }
    }
}
