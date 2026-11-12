import SwiftUI
import ARKit
import SceneKit
import Vision
import simd

// MARK: - Prototipo RA-0 (iOS)
// Objetivo: comprobar que (1) se detecta un mazo como objeto (rectángulo ~63x88 mm) sin usar el dorso como patrón y
// (2) un ancla 3D en el mundo (ARKit) se mantiene estable al cambiar ángulo, luz o tapar el mazo con la mano.
// Entrada oculta: pulsación larga en el centro superior de la pantalla (ver ContentView.swift).

/// Parámetros de detección. Los escribe la configuración secreta (Kotlin) en UserDefaults con estas mismas claves.
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

    static func load() -> DetConfig {
        let d = UserDefaults.standard
        func int(_ key: String, _ def: Int) -> Int { d.object(forKey: key) == nil ? def : d.integer(forKey: key) }
        var c = DetConfig()
        c.tries = max(1, int("detTries", 3))
        c.tolerance = Float(int("detTolerancePct", 30)) / 100
        c.interval = TimeInterval(int("detIntervalMs", 300)) / 1000
        c.minConfidence = Float(int("detMinConfidencePct", 60)) / 100
        c.minSize = Float(int("detMinSizePct", 5)) / 100
        c.maxObservations = max(1, int("detMaxObservations", 3))
        c.moveMeters = Float(int("detMoveCm", 3)) / 100
        c.showDebug = d.object(forKey: "detShowDebug") == nil ? true : d.bool(forKey: "detShowDebug")
        c.manualTap = d.object(forKey: "detManualTap") == nil ? true : d.bool(forKey: "detManualTap")
        return c
    }
}

/// Estado que se muestra en el HUD.
final class ARModel: ObservableObject {
    @Published var status = "Buscando el mazo…"
    @Published var tracking = "—"
    @Published var resetToken = 0
    @Published var cfgToken = 0   // sube al cerrar la configuración, para recargar los parámetros
}

struct ARScreen: View {
    @StateObject private var model = ARModel()
    @State private var showConfig = false
    let onClose: () -> Void
    private let cyan = Color(red: 0.24, green: 0.86, blue: 1.0)

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
                    Text("INEXPLICABLE · RA-0")
                        .font(.system(size: 14, weight: .light, design: .monospaced))
                        .foregroundColor(cyan)
                    Text(model.status)
                        .font(.system(size: 16, design: .monospaced))
                        .foregroundColor(.white)
                    Text("Seguimiento: " + model.tracking)
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundColor(.gray)
                }
                .padding(.top, 60)
                Spacer()
                Text("Apunta al mazo sobre la mesa.")
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundColor(.gray)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
                HStack(spacing: 24) {
                    Button("REINICIAR") { model.resetToken += 1 }
                    Button("CERRAR") { onClose() }
                }
                .font(.system(size: 16, weight: .medium, design: .monospaced))
                .foregroundColor(cyan)
                .padding(.bottom, 40)
                .padding(.top, 12)
            }
        }
        .background(Color.black)
        // La configuración se abre encima sin cerrar la sesión de RA; al cerrarla se recargan los parámetros.
        .fullScreenCover(isPresented: $showConfig) {
            ConfigComposeView(onClose: { showConfig = false; model.cfgToken += 1 })
                .ignoresSafeArea(.all)
                .background(Color.black)
        }
    }
}

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
        if context.coordinator.lastCfgToken != model.cfgToken {
            context.coordinator.lastCfgToken = model.cfgToken
            context.coordinator.cfg = DetConfig.load()
        }
        if context.coordinator.lastResetToken != model.resetToken {
            context.coordinator.lastResetToken = model.resetToken
            context.coordinator.reset()
        }
    }

    static func dismantleUIView(_ uiView: ARSCNView, coordinator: Coordinator) {
        uiView.session.pause()
    }

    final class Coordinator: NSObject, ARSCNViewDelegate, ARSessionDelegate {
        let model: ARModel
        weak var view: ARSCNView?
        var lastResetToken = 0
        private var placed = false
        private var busy = false
        private var lastDetection: TimeInterval = 0
        private var deckAnchor: ARAnchor?
        private var stableCount = 0
        private var lastCenter: simd_float3?
        var lastCfgToken = 0
        var cfg = DetConfig.load()
        private let okLayer = CAShapeLayer()
        private let badLayer = CAShapeLayer()

        init(model: ARModel) { self.model = model }

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

        func startSession() {
            guard let view = view else { return }
            cfg = DetConfig.load()
            let config = ARWorldTrackingConfiguration()
            config.planeDetection = [.horizontal]
            view.session.run(config)
        }

        func reset() {
            guard let view = view else { return }
            placed = false
            deckAnchor = nil
            stableCount = 0
            lastCenter = nil
            cfg = DetConfig.load()
            clearDebug()
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
            model.status = "Anclado (" + how + "). Mueve el móvil: ¿se mantiene?"
        }

        @objc func handleTap(_ gr: UITapGestureRecognizer) {
            guard let view = view, cfg.manualTap else { return }
            let point = gr.location(in: view)
            if let query = view.raycastQuery(from: point, allowing: .estimatedPlane, alignment: .any),
               let result = view.session.raycast(query).first {
                place(at: result.worldTransform, how: "manual")
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
                // Una baraja mide ~6,3 x 8,8 cm: filtra pantallas, cables, etc.
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
                clearDebug()
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

        func renderer(_ renderer: SCNSceneRenderer, nodeFor anchor: ARAnchor) -> SCNNode? {
            guard anchor.name == "deck" else { return nil }
            let root = SCNNode()

            let cyan = UIColor(red: 0.24, green: 0.86, blue: 1.0, alpha: 1.0)
            let material = SCNMaterial()
            material.diffuse.contents = cyan
            material.emission.contents = cyan

            // Huella del mazo (63x88 mm) para comprobar visualmente la alineación.
            let plane = SCNPlane(width: 0.063, height: 0.088)
            let planeMaterial = SCNMaterial()
            planeMaterial.diffuse.contents = cyan.withAlphaComponent(0.25)
            planeMaterial.isDoubleSided = true
            plane.materials = [planeMaterial]
            let planeNode = SCNNode(geometry: plane)
            planeNode.eulerAngles.x = -Float.pi / 2
            planeNode.position.y = 0.001
            root.addChildNode(planeNode)

            // Anillos giratorios.
            let ring = SCNNode(geometry: SCNTorus(ringRadius: 0.07, pipeRadius: 0.0015))
            ring.geometry?.materials = [material]
            ring.position.y = 0.01
            ring.runAction(SCNAction.repeatForever(SCNAction.rotateBy(x: 0, y: CGFloat.pi * 2, z: 0, duration: 4)))
            root.addChildNode(ring)

            let ring2 = SCNNode(geometry: SCNTorus(ringRadius: 0.05, pipeRadius: 0.001))
            ring2.geometry?.materials = [material]
            ring2.position.y = 0.02
            ring2.eulerAngles.x = Float.pi / 6
            ring2.runAction(SCNAction.repeatForever(SCNAction.rotateBy(x: 0, y: -CGFloat.pi * 2, z: 0, duration: 2.5)))
            root.addChildNode(ring2)

            // Texto de revelación que mira siempre a la cámara.
            let text = SCNText(string: "24 cartas cara abajo", extrusionDepth: 0.5)
            text.font = UIFont.systemFont(ofSize: 10, weight: .bold)
            text.flatness = 0.1
            let textMaterial = SCNMaterial()
            textMaterial.diffuse.contents = UIColor.white
            textMaterial.emission.contents = UIColor.white
            text.materials = [textMaterial]
            let textNode = SCNNode(geometry: text)
            let (minB, maxB) = text.boundingBox
            textNode.pivot = SCNMatrix4MakeTranslation((maxB.x - minB.x) / 2 + minB.x, 0, 0)
            textNode.scale = SCNVector3(0.002, 0.002, 0.002)
            textNode.position = SCNVector3(0, 0.12, 0)
            textNode.constraints = [SCNBillboardConstraint()]
            root.addChildNode(textNode)

            return root
        }
    }
}
