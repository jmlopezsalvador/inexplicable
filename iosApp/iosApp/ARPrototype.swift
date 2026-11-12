import SwiftUI
import ARKit
import SceneKit
import Vision
import simd

// MARK: - RA (iOS)
// (1) Detecta el mazo como objeto (rectángulo ~63x88 mm) con Vision + raycast y lo ancla en el mundo con ARKit.
// (2) Sobre el ancla corre el efecto por fases: Analizar → partículas y anillos → aparece un cubo (resultado) → se toca →
//     se abre → cartel con la predicción (siempre legible: se ajusta a la pantalla) → Continuar.
// Se abre con EMPEZAR cuando el modo elegido es RA (ver ContentView.swift). Los ajustes los escribe Kotlin en UserDefaults.

// MARK: Ajustes

/// Color guardado por Kotlin como ARGB en un entero.
private func color(_ key: String, _ def: Int) -> UIColor {
    let d = UserDefaults.standard
    let v = d.object(forKey: key) == nil ? def : d.integer(forKey: key)
    return UIColor(red: CGFloat((v >> 16) & 0xFF) / 255, green: CGFloat((v >> 8) & 0xFF) / 255,
                   blue: CGFloat(v & 0xFF) / 255, alpha: 1)
}

/// Parámetros de detección y presentación. Los escribe la configuración secreta (Kotlin) con estas mismas claves.
struct DetConfig {
    var tries = 3
    var tolerance: Float = 0.30
    var interval: TimeInterval = 0.3
    var minConfidence: Float = 0.60
    var minSize: Float = 0.05
    var maxObservations = 3
    var moveMeters: Float = 0.03
    var showDebug = true
    var manualTap = true
    // Presentación
    var level = 2
    var analyzeSeconds: Double = 2.6
    var openSeconds: Double = 1.4
    var soundOn = true
    var primary = UIColor(red: 0.24, green: 0.86, blue: 1.0, alpha: 1)
    var secondary = UIColor(red: 0.24, green: 0.48, blue: 1.0, alpha: 1)
    var accent = UIColor(red: 1.0, green: 0.78, blue: 0.34, alpha: 1)

    static func load() -> DetConfig {
        let d = UserDefaults.standard
        func int(_ key: String, _ def: Int) -> Int { d.object(forKey: key) == nil ? def : d.integer(forKey: key) }
        func bool(_ key: String, _ def: Bool) -> Bool { d.object(forKey: key) == nil ? def : d.bool(forKey: key) }
        var c = DetConfig()
        c.tries = max(1, int("detTries", 3))
        c.tolerance = Float(int("detTolerancePct", 30)) / 100
        c.interval = TimeInterval(int("detIntervalMs", 300)) / 1000
        c.minConfidence = Float(int("detMinConfidencePct", 60)) / 100
        c.minSize = Float(int("detMinSizePct", 5)) / 100
        c.maxObservations = max(1, int("detMaxObservations", 3))
        c.moveMeters = Float(int("detMoveCm", 3)) / 100
        c.showDebug = bool("detShowDebug", true)
        c.manualTap = bool("detManualTap", true)
        c.level = min(3, max(1, int("hudLevel", 2)))
        c.analyzeSeconds = Double(int("analyzeMs", 2600)) / 1000
        c.openSeconds = Double(int("openMs", 1400)) / 1000
        c.soundOn = bool("soundOn", true)
        c.primary = color("colorPrimary", 0x3DDCFF)
        c.secondary = color("colorSecondary", 0x3D7BFF)
        c.accent = color("colorAccent", 0xFFC857)
        return c
    }
}

/// Fases y textos ya resueltos por Kotlin (idioma, números, personalización) al pulsar EMPEZAR.
struct ARTexts {
    var phases: [String] = ["Hay 24 cartas cara abajo."]
    var analyze = "ANALIZAR"
    var next = "CONTINUAR"
    var tapReveal = "TOCA PARA REVELAR"
    var analyzing = "Analizando…"
    var finished = "ANÁLISIS COMPLETO"
    var ready = "BARAJA ENLAZADA"

    static func load() -> ARTexts {
        let d = UserDefaults.standard
        var t = ARTexts()
        if let s = d.string(forKey: "arPhases") {
            let list = s.components(separatedBy: "\u{1F}").filter { !$0.isEmpty }
            if !list.isEmpty { t.phases = list }
        }
        t.analyze = d.string(forKey: "arAnalyze") ?? t.analyze
        t.next = d.string(forKey: "arNext") ?? t.next
        t.tapReveal = d.string(forKey: "arTapReveal") ?? t.tapReveal
        t.analyzing = d.string(forKey: "arAnalyzing") ?? t.analyzing
        t.finished = d.string(forKey: "arFinished") ?? t.finished
        t.ready = d.string(forKey: "arReady") ?? t.ready
        return t
    }
}

enum ARStage { case searching, ready, analyzing, result, revealed, finished }

// MARK: Modelo y pantalla

/// Estado que se muestra en el HUD y órdenes hacia el coordinador.
final class ARModel: ObservableObject {
    @Published var status = "Buscando el mazo…"
    @Published var tracking = "—"
    @Published var resetToken = 0
    @Published var stage: ARStage = .searching
    @Published var phaseIndex = 0
    @Published var progress: Double = 0
    @Published var canContinue = false
    @Published var cfg = DetConfig.load()
    @Published var texts = ARTexts.load()

    var onAnalyze: (() -> Void)?
    var onNext: (() -> Void)?

    /// Recarga los ajustes (al cerrar la configuración).
    func reloadConfig() { cfg = DetConfig.load() }
}

struct ARScreen: View {
    @StateObject private var model = ARModel()
    @State private var showConfig = false
    let onClose: () -> Void

    private var primary: Color { Color(model.cfg.primary) }

    var body: some View {
        ZStack {
            ARSceneView(model: model).ignoresSafeArea()
            // Zonas ocultas: pulsación larga arriba a la izquierda = reset (vuelve al menú); arriba a la derecha = configuración.
            VStack {
                HStack {
                    Color.clear.frame(width: 88, height: 88).contentShape(Rectangle())
                        .onLongPressGesture(minimumDuration: 1.0) { onClose() }
                    Spacer()
                    Color.clear.frame(width: 88, height: 88).contentShape(Rectangle())
                        .onLongPressGesture(minimumDuration: 1.0) { showConfig = true }
                }
                Spacer()
            }
            .ignoresSafeArea()
            VStack {
                VStack(spacing: 4) {
                    Text("INEXPLICABLE")
                        .font(.system(size: 14, weight: .light, design: .monospaced))
                        .foregroundColor(primary)
                    Text(statusLine)
                        .font(.system(size: 16, design: .monospaced))
                        .foregroundColor(model.stage == .result ? Color(model.cfg.accent) : .white)
                        .multilineTextAlignment(.center)
                    Text("Seguimiento: " + model.tracking)
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundColor(.gray)
                }
                .padding(.top, 60)
                .padding(.horizontal, 24)
                Spacer()
                actionButton
                HStack(spacing: 24) {
                    Button("REINICIAR") { model.resetToken += 1 }
                    Button("CERRAR") { onClose() }
                }
                .font(.system(size: 13, weight: .medium, design: .monospaced))
                .foregroundColor(primary.opacity(0.7))
                .padding(.bottom, 40)
                .padding(.top, 12)
            }
        }
        .background(Color.black)
        // La configuración se abre encima sin cerrar la sesión de RA; al cerrarla se recargan los ajustes.
        .fullScreenCover(isPresented: $showConfig) {
            ConfigComposeView(onClose: { showConfig = false; model.reloadConfig() })
                .ignoresSafeArea(.all)
                .background(Color.black)
        }
    }

    private var statusLine: String {
        switch model.stage {
        case .searching: return model.status
        case .ready: return model.texts.ready
        case .analyzing: return model.texts.analyzing + " " + String(Int(model.progress * 100)) + "%"
        case .result: return model.texts.tapReveal
        case .revealed: return model.status
        case .finished: return model.texts.finished
        }
    }

    @ViewBuilder private var actionButton: some View {
        if model.stage == .ready {
            hudButton(model.texts.analyze, enabled: true) { model.onAnalyze?() }
        } else if model.stage == .revealed {
            hudButton(model.texts.next, enabled: model.canContinue) { model.onNext?() }
        } else if model.stage == .analyzing || model.stage == .result {
            hudButton(model.texts.analyze, enabled: false) {}
        }
    }

    private func hudButton(_ label: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 18, weight: .medium, design: .monospaced))
                .kerning(5)
                .foregroundColor(enabled ? primary : primary.opacity(0.3))
                .padding(.horizontal, 36).padding(.vertical, 14)
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(enabled ? primary : primary.opacity(0.3), lineWidth: 1.5))
        }
        .disabled(!enabled)
    }
}

// MARK: Vista de RA

struct ARSceneView: UIViewRepresentable {
    @ObservedObject var model: ARModel

    func makeCoordinator() -> Coordinator { Coordinator(model: model) }

    func makeUIView(context: Context) -> ARSCNView {
        let view = ARSCNView(frame: .zero)
        view.delegate = context.coordinator
        view.session.delegate = context.coordinator
        view.autoenablesDefaultLighting = true
        view.automaticallyUpdatesLighting = true
        context.coordinator.view = view
        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleTap(_:)))
        view.addGestureRecognizer(tap)
        context.coordinator.startSession()
        return view
    }

    func updateUIView(_ uiView: ARSCNView, context: Context) {
        if context.coordinator.lastResetToken != model.resetToken {
            context.coordinator.lastResetToken = model.resetToken
            context.coordinator.reset()
        }
    }

    static func dismantleUIView(_ uiView: ARSCNView, coordinator: Coordinator) {
        coordinator.shutdown()
        uiView.session.pause()
    }

    final class Coordinator: NSObject, ARSCNViewDelegate, ARSessionDelegate {
        let model: ARModel
        weak var view: ARSCNView?
        var lastResetToken = 0
        private var cfg: DetConfig { model.cfg }
        private var placed = false
        private var busy = false
        private var lastDetection: TimeInterval = 0
        private var deckAnchor: ARAnchor?
        private var stableCount = 0
        private var lastCenter: simd_float3?
        private let okLayer = CAShapeLayer()
        private let badLayer = CAShapeLayer()

        // Contenido 3D sobre el ancla.
        private var deckRoot: SCNNode?
        private var particlesRoot: SCNNode?
        private var ringsRoot: SCNNode?
        private var cubeNode: SCNNode?
        private var cardNode: SCNNode?
        private var generation = 0          // sube al reiniciar: invalida temporizadores pendientes
        private var analysisTimer: Timer?

        init(model: ARModel) {
            self.model = model
            super.init()
            model.onAnalyze = { [weak self] in self?.startAnalysis() }
            model.onNext = { [weak self] in self?.nextPhase() }
        }

        func shutdown() {
            generation += 1
            analysisTimer?.invalidate()
            send("analyzing_off")
            SpeechBridge.stop()
        }

        // MARK: Sonido (lo genera SoundEngine.swift)

        private func send(_ cmd: String) {
            if cfg.soundOn || cmd.hasSuffix("_off") { SoundBridge.send(cmd) }
        }

        // MARK: Depuración visual

        /// Dibuja los candidatos evaluados: verde = encaja con un mazo, rojo = descartado. Con un círculo en el centro.
        private func drawDebug(ok: [[CGPoint]], bad: [[CGPoint]]) {
            guard let view = view else { return }
            for (layer, color) in [(okLayer, UIColor.green), (badLayer, UIColor.red)] {
                if layer.superlayer == nil {
                    layer.fillColor = UIColor.clear.cgColor
                    layer.strokeColor = color.cgColor
                    layer.lineWidth = 2
                    view.layer.addSublayer(layer)
                }
            }
            func path(_ polys: [[CGPoint]]) -> CGPath {
                let p = UIBezierPath()
                for poly in polys where poly.count == 4 {
                    // Orden Vision: topLeft, topRight, bottomLeft, bottomRight → contorno.
                    p.move(to: poly[0]); p.addLine(to: poly[1]); p.addLine(to: poly[3]); p.addLine(to: poly[2]); p.close()
                    let c = CGPoint(x: poly.map { $0.x }.reduce(0, +) / 4, y: poly.map { $0.y }.reduce(0, +) / 4)
                    p.append(UIBezierPath(arcCenter: c, radius: 16, startAngle: 0, endAngle: .pi * 2, clockwise: true))
                }
                return p.cgPath
            }
            okLayer.path = cfg.showDebug ? path(ok) : nil
            badLayer.path = cfg.showDebug ? path(bad) : nil
        }

        private func clearDebug() { okLayer.path = nil; badLayer.path = nil }

        // MARK: Sesión

        func startSession() {
            guard let view = view else { return }
            let config = ARWorldTrackingConfiguration()
            config.planeDetection = [.horizontal]
            view.session.run(config)
        }

        func reset() {
            guard let view = view else { return }
            generation += 1
            analysisTimer?.invalidate()
            send("analyzing_off")
            SpeechBridge.stop()
            placed = false
            deckAnchor = nil
            deckRoot = nil; particlesRoot = nil; ringsRoot = nil; cubeNode = nil; cardNode = nil
            stableCount = 0
            lastCenter = nil
            clearDebug()
            model.reloadConfig()
            model.texts = ARTexts.load()
            model.stage = .searching
            model.phaseIndex = 0
            model.progress = 0
            model.canContinue = false
            model.status = "Buscando el mazo…"
            let config = ARWorldTrackingConfiguration()
            config.planeDetection = [.horizontal]
            view.session.run(config, options: [.resetTracking, .removeExistingAnchors])
        }

        // MARK: Anclaje

        private func place(at transform: simd_float4x4, how: String) {
            guard let view = view else { return }
            if let old = deckAnchor { view.session.remove(anchor: old) }
            let anchor = ARAnchor(name: "deck", transform: transform)
            deckAnchor = anchor
            view.session.add(anchor: anchor)
            placed = true
            clearDebug()
            model.texts = ARTexts.load()
            model.phaseIndex = 0
            model.canContinue = false
            model.status = "Anclado (" + how + ")"
            model.stage = .ready
            SpeechBridge.say(model.texts.ready, reveal: false)
        }

        @objc func handleTap(_ gr: UITapGestureRecognizer) {
            guard let view = view else { return }
            if placed {
                // Con el resultado flotando, tocar la pantalla lo abre.
                if model.stage == .result { openResult() }
                return
            }
            guard cfg.manualTap else { return }
            let point = gr.location(in: view)
            if let query = view.raycastQuery(from: point, allowing: .estimatedPlane, alignment: .any),
               let result = view.session.raycast(query).first {
                place(at: result.worldTransform, how: "manual")
            }
        }

        // MARK: Flujo por fases

        private func startAnalysis() {
            guard model.stage == .ready else { return }
            let gen = generation
            model.stage = .analyzing
            model.progress = 0
            send("analyzing_on")
            SpeechBridge.say(model.texts.analyzing, reveal: false)
            particlesRoot?.speed = 2.5
            ringsRoot?.speed = 2.2
            let total = max(0.5, cfg.analyzeSeconds)
            let start = Date()
            analysisTimer?.invalidate()
            analysisTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] timer in
                guard let self = self, self.generation == gen else { timer.invalidate(); return }
                let p = min(1, Date().timeIntervalSince(start) / total)
                self.model.progress = p
                if p >= 1 { timer.invalidate(); self.finishAnalysis() }
            }
        }

        private func finishAnalysis() {
            model.stage = .result
            send("analyzing_off")
            send("ping")
            particlesRoot?.speed = 1.5
            ringsRoot?.speed = 1.0
            showCube()
        }

        private func showCube() {
            guard let root = deckRoot else { return }
            cubeNode?.removeFromParentNode()
            let box = SCNBox(width: 0.04, height: 0.04, length: 0.04, chamferRadius: 0)
            let m = SCNMaterial()
            m.diffuse.contents = cfg.accent
            m.emission.contents = cfg.accent
            m.fillMode = .lines
            m.lightingModel = .constant
            box.materials = [m]
            let node = SCNNode(geometry: box)
            node.position = SCNVector3(0, 0.12, 0)
            node.opacity = 0
            node.runAction(.fadeIn(duration: 0.4))
            node.runAction(.repeatForever(.rotateBy(x: 0, y: CGFloat.pi * 2, z: 0, duration: 4)))
            // Flota arriba y abajo.
            let up = SCNAction.moveBy(x: 0, y: 0.008, z: 0, duration: 0.9)
            up.timingMode = .easeInEaseOut
            node.runAction(.repeatForever(.sequence([up, up.reversed()])))
            root.addChildNode(node)
            cubeNode = node
        }

        private func openResult() {
            guard model.stage == .result else { return }
            let gen = generation
            let open = max(0.3, cfg.openSeconds)
            model.stage = .revealed
            model.canContinue = false
            model.status = ""
            send("reveal")
            // El cubo crece y se desvanece; ondas expansivas desde él y sobre la mesa.
            if let cube = cubeNode {
                cube.removeAllActions()
                cube.runAction(.sequence([.group([.scale(to: 3, duration: open), .fadeOut(duration: open)]), .removeFromParentNode()]))
                cubeNode = nil
            }
            addShock(y: 0.12, maxScale: 8, duration: open, color: cfg.accent)
            addShock(y: 0.012, maxScale: 6, duration: open * 1.2, color: cfg.primary)
            particlesRoot?.speed = 0.8
            ringsRoot?.speed = 1.0
            // Aparece el cartel con la predicción a mitad de la apertura.
            DispatchQueue.main.asyncAfter(deadline: .now() + open * 0.45) { [weak self] in
                guard let self = self, self.generation == gen else { return }
                self.showCard()
                let phases = self.model.texts.phases
                if self.model.phaseIndex < phases.count { SpeechBridge.say(phases[self.model.phaseIndex], reveal: true) }
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + open) { [weak self] in
                guard let self = self, self.generation == gen else { return }
                self.model.canContinue = true
            }
        }

        private func addShock(y: Float, maxScale: CGFloat, duration: Double, color: UIColor) {
            guard let root = deckRoot else { return }
            let torus = SCNTorus(ringRadius: 0.02, pipeRadius: 0.0015)
            let m = SCNMaterial()
            m.diffuse.contents = color
            m.emission.contents = color
            m.lightingModel = .constant
            torus.materials = [m]
            let node = SCNNode(geometry: torus)
            node.position = SCNVector3(0, y, 0)
            node.runAction(.sequence([.group([.scale(to: maxScale, duration: duration), .fadeOut(duration: duration)]), .removeFromParentNode()]))
            root.addChildNode(node)
        }

        private func showCard() {
            guard let root = deckRoot else { return }
            cardNode?.removeFromParentNode()
            let phases = model.texts.phases
            let text = model.phaseIndex < phases.count ? phases[model.phaseIndex] : ""
            let image = ARCard.image(text: text, border: cfg.primary)
            let plane = SCNPlane(width: 1.0, height: 1.0 * image.size.height / image.size.width)
            let m = SCNMaterial()
            m.diffuse.contents = image
            m.lightingModel = .constant
            m.isDoubleSided = true
            plane.materials = [m]
            let node = SCNNode(geometry: plane)
            node.position = SCNVector3(0, 0.17, 0)
            node.constraints = [SCNBillboardConstraint()]
            node.simdScale = simd_float3(repeating: 0.2)
            node.opacity = 0
            node.runAction(.fadeIn(duration: 0.35))
            root.addChildNode(node)
            cardNode = node
        }

        private func nextPhase() {
            guard model.stage == .revealed, model.canContinue else { return }
            cardNode?.removeFromParentNode()
            cardNode = nil
            particlesRoot?.speed = 0.5
            if model.phaseIndex + 1 < model.texts.phases.count {
                model.phaseIndex += 1
                model.stage = .ready
            } else {
                model.stage = .finished
                SpeechBridge.say(model.texts.finished, reveal: false)
            }
        }

        // MARK: Detección del mazo (rectángulo) con Vision

        func session(_ session: ARSession, didUpdate frame: ARFrame) {
            if placed || busy { return }
            let now = frame.timestamp
            if now - lastDetection < cfg.interval { return }
            lastDetection = now
            guard let view = view else { return }

            busy = true
            let size = view.bounds.size
            let transform = frame.displayTransform(for: .portrait, viewportSize: size)
            let buffer = frame.capturedImage
            let imgW = CGFloat(CVPixelBufferGetWidth(buffer))
            let imgH = CGFloat(CVPixelBufferGetHeight(buffer))

            let request = VNDetectRectanglesRequest()
            request.minimumAspectRatio = 0.3
            request.maximumAspectRatio = 1.0
            request.minimumSize = cfg.minSize
            request.minimumConfidence = cfg.minConfidence
            request.quadratureTolerance = 30
            request.maximumObservations = cfg.maxObservations

            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                let handler = VNImageRequestHandler(cvPixelBuffer: buffer, orientation: .up, options: [:])
                try? handler.perform([request])
                let observations = (request.results as? [VNRectangleObservation]) ?? []
                DispatchQueue.main.async {
                    self?.finishDetection(observations, imgW: imgW, imgH: imgH, transform: transform, size: size)
                }
            }
        }

        /// Posición en el mundo de un punto de la vista (raycast sobre superficie horizontal, o cualquiera si no hay).
        private func worldHit(_ point: CGPoint, in view: ARSCNView) -> ARRaycastResult? {
            if let q = view.raycastQuery(from: point, allowing: .estimatedPlane, alignment: .horizontal),
               let r = view.session.raycast(q).first { return r }
            return nil
        }

        private func finishDetection(_ observations: [VNRectangleObservation], imgW: CGFloat, imgH: CGFloat,
                                     transform: CGAffineTransform, size: CGSize) {
            busy = false
            guard !placed, let view = view else { return }

            // Vision (origen abajo-izquierda) → imagen (arriba-izquierda) → vista.
            func toView(_ p: CGPoint) -> CGPoint {
                let n = CGPoint(x: p.x, y: 1 - p.y).applying(transform)
                return CGPoint(x: n.x * size.width, y: n.y * size.height)
            }
            func pos(_ r: ARRaycastResult) -> simd_float3 {
                let c = r.worldTransform.columns.3
                return simd_float3(c.x, c.y, c.z)
            }

            var found: simd_float4x4?
            var lastDims = ""
            var okPolys: [[CGPoint]] = []
            var badPolys: [[CGPoint]] = []
            // Medidas nominales de una carta de póker (m) y margen de error configurable.
            let tol = cfg.tolerance
            let minShort: Float = 0.063 * (1 - tol), maxShort: Float = 0.063 * (1 + tol)
            let minLong: Float = 0.088 * (1 - tol), maxLong: Float = 0.088 * (1 + tol)
            let minRatio: Float = 0.716 * (1 - tol), maxRatio: Float = min(1.0, 0.716 * (1 + tol))
            for o in observations {
                let corners = [o.topLeft, o.topRight, o.bottomLeft, o.bottomRight].map(toView)
                let hits = corners.compactMap { worldHit($0, in: view) }
                if hits.count < 4 { badPolys.append(corners); continue }
                let p = hits.map(pos)
                let e1 = p[1] - p[0]   // arriba-izquierda → arriba-derecha
                let e2 = p[2] - p[0]   // arriba-izquierda → abajo-izquierda
                let l1 = simd_length(e1)
                let l2 = simd_length(e2)
                let shortLen = min(l1, l2)
                let longLen = max(l1, l2)
                lastDims = String(format: "%.1f x %.1f cm", shortLen * 100, longLen * 100)
                let ratio = shortLen / longLen
                if shortLen < minShort || shortLen > maxShort || longLen < minLong || longLen > maxLong
                    || ratio < minRatio || ratio > maxRatio {
                    badPolys.append(corners); continue
                }

                // Orientación: Y = normal de la superficie; X = lado corto del rectángulo; Z = lado largo.
                let up = simd_normalize(simd_float3(hits[0].worldTransform.columns.1.x,
                                                    hits[0].worldTransform.columns.1.y,
                                                    hits[0].worldTransform.columns.1.z))
                let shortEdge = l1 < l2 ? e1 : e2
                let flat = shortEdge - up * simd_dot(shortEdge, up)
                if simd_length(flat) < 0.001 { badPolys.append(corners); continue }
                okPolys.append(corners)
                let x = simd_normalize(flat)
                let z = simd_cross(x, up)
                let center = (p[0] + p[1] + p[2] + p[3]) / 4
                found = simd_float4x4(columns: (
                    SIMD4<Float>(x.x, x.y, x.z, 0),
                    SIMD4<Float>(up.x, up.y, up.z, 0),
                    SIMD4<Float>(z.x, z.y, z.z, 0),
                    SIMD4<Float>(center.x, center.y, center.z, 1)
                ))
                break
            }

            drawDebug(ok: okPolys, bad: badPolys)
            guard let t = found else {
                stableCount = 0
                lastCenter = nil
                model.status = lastDims.isEmpty ? "Buscando el mazo…" : "Descartado (" + lastDims + ")"
                return
            }

            // Hace falta verlo estable varias veces seguidas antes de anclar.
            let c = simd_float3(t.columns.3.x, t.columns.3.y, t.columns.3.z)
            if let prev = lastCenter, simd_distance(prev, c) < cfg.moveMeters { stableCount += 1 } else { stableCount = 1 }
            lastCenter = c
            model.status = "Mazo detectado (" + lastDims + ") " + String(stableCount) + "/" + String(cfg.tries)
            if stableCount >= cfg.tries {
                place(at: t, how: "auto")
                stableCount = 0
                lastCenter = nil
            }
        }

        // MARK: Seguimiento

        func session(_ session: ARSession, cameraDidChangeTrackingState camera: ARCamera) {
            let text: String
            switch camera.trackingState {
            case .normal: text = "normal"
            case .notAvailable: text = "no disponible"
            case .limited(let reason):
                switch reason {
                case .initializing: text = "limitado (iniciando)"
                case .excessiveMotion: text = "limitado (movimiento)"
                case .insufficientFeatures: text = "limitado (pocas referencias)"
                case .relocalizing: text = "limitado (reubicando)"
                @unknown default: text = "limitado"
                }
            }
            DispatchQueue.main.async { self.model.tracking = text }
        }

        // MARK: Contenido 3D anclado

        /// Anillos que giran alrededor de la baraja (nunca la atraviesan). Más anillos cuanto mayor es el nivel.
        private func makeRings(_ cfg: DetConfig) -> SCNNode {
            let root = SCNNode()
            func ring(radius: CGFloat, pipe: CGFloat, y: Float, tilt: Float, color: UIColor, seconds: Double, dir: CGFloat) {
                let torus = SCNTorus(ringRadius: radius, pipeRadius: pipe)
                let m = SCNMaterial()
                m.diffuse.contents = color
                m.emission.contents = color
                m.lightingModel = .constant
                torus.materials = [m]
                let node = SCNNode(geometry: torus)
                node.position = SCNVector3(0, y, 0)
                node.eulerAngles.x = tilt
                node.runAction(.repeatForever(.rotateBy(x: 0, y: dir * CGFloat.pi * 2, z: 0, duration: seconds)))
                root.addChildNode(node)
            }
            ring(radius: 0.075, pipe: 0.0015, y: 0.012, tilt: 0, color: cfg.primary, seconds: 4, dir: 1)
            ring(radius: 0.055, pipe: 0.001, y: 0.05, tilt: Float.pi / 8, color: cfg.secondary, seconds: 2.5, dir: -1)
            if cfg.level >= 2 {
                ring(radius: 0.105, pipe: 0.001, y: 0.008, tilt: 0, color: cfg.secondary.withAlphaComponent(0.6), seconds: 9, dir: -1)
                ring(radius: 0.065, pipe: 0.001, y: 0.085, tilt: -Float.pi / 10, color: cfg.accent, seconds: 3.2, dir: 1)
            }
            if cfg.level >= 3 {
                ring(radius: 0.125, pipe: 0.0008, y: 0.006, tilt: 0, color: cfg.primary.withAlphaComponent(0.5), seconds: 14, dir: 1)
                ring(radius: 0.08, pipe: 0.0008, y: 0.115, tilt: Float.pi / 12, color: cfg.secondary, seconds: 5, dir: -1)
            }
            return root
        }

        /// Partículas que orbitan alrededor de la baraja. Siempre quedan fuera de su huella o por encima de ella.
        private func makeParticles(_ cfg: DetConfig) -> SCNNode {
            let root = SCNNode()
            let count = cfg.level == 1 ? 30 : (cfg.level == 2 ? 70 : 120)
            let colors = [cfg.primary, cfg.secondary, cfg.accent]
            for i in 0..<count {
                let color = colors[i % 3]
                let pivot = SCNNode()
                pivot.position = SCNVector3(0, Float.random(in: 0.04...0.13), 0)
                pivot.eulerAngles = SCNVector3(Float.random(in: -0.25...0.25), Float.random(in: 0...(Float.pi * 2)),
                                               Float.random(in: -0.25...0.25))
                let sphere = SCNSphere(radius: 0.0022)
                let m = SCNMaterial()
                m.diffuse.contents = color
                m.emission.contents = color
                m.lightingModel = .constant
                sphere.materials = [m]
                let dot = SCNNode(geometry: sphere)
                dot.position = SCNVector3(Float.random(in: 0.065...0.11), 0, 0)
                pivot.addChildNode(dot)
                let dir: CGFloat = Bool.random() ? 1 : -1
                pivot.runAction(.repeatForever(.rotateBy(x: 0, y: dir * CGFloat.pi * 2, z: 0, duration: Double.random(in: 3...9))))
                root.addChildNode(pivot)
            }
            root.speed = 0.5
            return root
        }

        func renderer(_ renderer: SCNSceneRenderer, nodeFor anchor: ARAnchor) -> SCNNode? {
            guard anchor.name == "deck" else { return nil }
            let cfg = self.cfg
            let root = SCNNode()

            // Huella del mazo (63x88 mm) para comprobar visualmente la alineación.
            let plane = SCNPlane(width: 0.063, height: 0.088)
            let planeMaterial = SCNMaterial()
            planeMaterial.diffuse.contents = cfg.primary.withAlphaComponent(0.25)
            planeMaterial.lightingModel = .constant
            planeMaterial.isDoubleSided = true
            plane.materials = [planeMaterial]
            let planeNode = SCNNode(geometry: plane)
            planeNode.eulerAngles.x = -Float.pi / 2
            planeNode.position.y = 0.001
            root.addChildNode(planeNode)

            let rings = makeRings(cfg)
            let particles = makeParticles(cfg)
            root.addChildNode(rings)
            root.addChildNode(particles)
            DispatchQueue.main.async {
                self.deckRoot = root
                self.ringsRoot = rings
                self.particlesRoot = particles
            }
            return root
        }

        /// El cartel mira siempre a la pantalla y se ajusta a su ancho: lejos se ve pequeño; cerca no pasa del 70 % de la pantalla.
        func renderer(_ renderer: SCNSceneRenderer, updateAtTime time: TimeInterval) {
            guard let card = cardNode, let pov = renderer.pointOfView, let camera = pov.camera else { return }
            let p00 = Float(camera.projectionTransform.m11)
            if p00 <= 0 { return }
            let d = max(0.05, simd_distance(pov.simdWorldPosition, card.simdWorldPosition))
            let width = min(1.4 * d / p00, 0.35) // metros: 70 % del ancho de pantalla a esa distancia, con tope
            card.simdScale = simd_float3(repeating: width)
        }
    }
}

// MARK: Cartel de la predicción

enum ARCard {
    /// Imagen del cartel: rectángulo redondeado oscuro con borde de color y el texto centrado.
    static func image(text: String, border: UIColor) -> UIImage {
        let size = CGSize(width: 900, height: 320)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { _ in
            let rect = CGRect(origin: .zero, size: size).insetBy(dx: 6, dy: 6)
            let path = UIBezierPath(roundedRect: rect, cornerRadius: 40)
            UIColor(red: 0, green: 0.07, blue: 0.12, alpha: 0.85).setFill()
            path.fill()
            border.setStroke()
            path.lineWidth = 8
            path.stroke()
            let style = NSMutableParagraphStyle()
            style.alignment = .center
            style.lineBreakMode = .byWordWrapping
            let attrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.monospacedSystemFont(ofSize: 54, weight: .bold),
                .foregroundColor: UIColor.white,
                .paragraphStyle: style,
            ]
            let textRect = rect.insetBy(dx: 36, dy: 24)
            let bounds = (text as NSString).boundingRect(with: textRect.size, options: [.usesLineFragmentOrigin],
                                                         attributes: attrs, context: nil)
            let y = textRect.minY + max(0, (textRect.height - bounds.height) / 2)
            (text as NSString).draw(in: CGRect(x: textRect.minX, y: y, width: textRect.width, height: textRect.height),
                                    withAttributes: attrs)
        }
    }
}
