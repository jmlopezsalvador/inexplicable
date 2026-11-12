import UIKit
import AVFoundation
import Shared

// Cámara con marco guía para la foto del dorso de la baraja y para el modo Foto. Kotlin la pide con `IosCameraHooks.request` y esta
// pantalla contesta con `IosCameraHooks.deliver` (JPEG en base64, o vacío si se cancela o no hay cámara/permiso).

final class CameraViewController: UIViewController, AVCapturePhotoCaptureDelegate {
    private let session = AVCaptureSession()
    private let output = AVCapturePhotoOutput()
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private let aspect: CGFloat      // ancho / alto del marco
    private let crop: Bool           // true: la foto se recorta al marco
    private let hint: String
    private let cancelText: String
    private let dimLayer = CAShapeLayer()
    private let frameLayer = CAShapeLayer()
    private let hintLabel = UILabel()
    private let shutter = UIButton(type: .custom)
    private let cancelButton = UIButton(type: .system)
    private var frameRect: CGRect = .zero
    private var finished = false
    private var configured = false
    var onFinish: ((String) -> Void)?

    init(aspect: CGFloat, crop: Bool, hint: String, cancel: String) {
        self.aspect = aspect
        self.crop = crop
        self.hint = hint
        self.cancelText = cancel
        super.init(nibName: nil, bundle: nil)
        modalPresentationStyle = .fullScreen
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) no se usa") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black

        dimLayer.fillColor = UIColor.black.withAlphaComponent(0.55).cgColor
        dimLayer.fillRule = .evenOdd
        frameLayer.fillColor = UIColor.clear.cgColor
        frameLayer.strokeColor = UIColor(red: 0.24, green: 0.86, blue: 1.0, alpha: 1).cgColor
        frameLayer.lineWidth = 3

        hintLabel.text = hint
        hintLabel.textColor = .white
        hintLabel.font = .systemFont(ofSize: 15)
        hintLabel.numberOfLines = 0
        hintLabel.textAlignment = .center

        shutter.backgroundColor = .white
        shutter.layer.cornerRadius = 36
        shutter.layer.borderWidth = 4
        shutter.layer.borderColor = UIColor.white.withAlphaComponent(0.6).cgColor
        shutter.addTarget(self, action: #selector(takePhoto), for: .touchUpInside)

        cancelButton.setTitle(cancelText, for: .normal)
        cancelButton.setTitleColor(.white, for: .normal)
        cancelButton.addTarget(self, action: #selector(cancel), for: .touchUpInside)

        view.layer.addSublayer(dimLayer)
        view.layer.addSublayer(frameLayer)
        view.addSubview(hintLabel)
        view.addSubview(shutter)
        view.addSubview(cancelButton)
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
            DispatchQueue.main.async {
                guard let self = self else { return }
                if granted { self.configureSession() } else { self.finish("") }
            }
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let b = view.bounds
        previewLayer?.frame = b
        let w = min(b.width * 0.8, b.height * 0.62 * aspect)
        let h = w / aspect
        frameRect = CGRect(x: (b.width - w) / 2, y: b.height * 0.44 - h / 2, width: w, height: h)
        let path = UIBezierPath(rect: b)
        path.append(UIBezierPath(roundedRect: frameRect, cornerRadius: 12))
        dimLayer.frame = b
        dimLayer.path = path.cgPath
        frameLayer.frame = b
        frameLayer.path = UIBezierPath(roundedRect: frameRect, cornerRadius: 12).cgPath
        let top = view.safeAreaInsets.top + 24
        hintLabel.frame = CGRect(x: 24, y: top, width: b.width - 48, height: 60)
        let bottom = view.safeAreaInsets.bottom + 40
        shutter.frame = CGRect(x: (b.width - 72) / 2, y: b.height - bottom - 72, width: 72, height: 72)
        cancelButton.frame = CGRect(x: 20, y: b.height - bottom - 56, width: 110, height: 40)
    }

    private func configureSession() {
        guard !configured else { return }
        configured = true
        session.sessionPreset = .photo
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input), session.canAddOutput(output) else {
            finish("")
            return
        }
        session.addInput(input)
        session.addOutput(output)
        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        layer.frame = view.bounds
        view.layer.insertSublayer(layer, at: 0)
        previewLayer = layer
        DispatchQueue.global(qos: .userInitiated).async { [session] in session.startRunning() }
    }

    @objc private func takePhoto() {
        shutter.isEnabled = false
        output.capturePhoto(with: AVCapturePhotoSettings(), delegate: self)
    }

    @objc private func cancel() { finish("") }

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        guard error == nil, let data = photo.fileDataRepresentation(), let image = UIImage(data: data) else {
            DispatchQueue.main.async { self.finish("") }
            return
        }
        // Este método llega en otro hilo: las medidas de la vista se leen en el principal y el trabajo pesado se hace fuera de él.
        DispatchQueue.main.async {
            let layerSize = self.view.bounds.size
            let frame = self.frameRect
            DispatchQueue.global(qos: .userInitiated).async {
                let result = self.process(image, layerSize: layerSize, frame: frame)
                DispatchQueue.main.async { self.finish(result) }
            }
        }
    }

    /// Endereza la foto (orientación), la recorta al marco si se pide y la reduce; devuelve JPEG en base64.
    private func process(_ source: UIImage, layerSize: CGSize, frame: CGRect) -> String {
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        var image = source
        if image.imageOrientation != .up {
            image = UIGraphicsImageRenderer(size: image.size, format: format).image { _ in image.draw(at: .zero) }
        }
        if crop, let cg = image.cgImage {
            // El marco está en la vista; la vista muestra la foto con «aspect fill»: se pasa a píxeles de la foto.
            let iw = CGFloat(cg.width), ih = CGFloat(cg.height)
            let scale = max(layerSize.width / iw, layerSize.height / ih)
            let offX = (iw * scale - layerSize.width) / 2
            let offY = (ih * scale - layerSize.height) / 2
            var r = CGRect(x: (frame.minX + offX) / scale, y: (frame.minY + offY) / scale,
                           width: frame.width / scale, height: frame.height / scale)
            r = r.intersection(CGRect(x: 0, y: 0, width: iw, height: ih))
            if r.width > 8, r.height > 8, let part = cg.cropping(to: r) {
                image = UIImage(cgImage: part, scale: 1, orientation: .up)
            }
        }
        let longest = max(image.size.width, image.size.height)
        let limit: CGFloat = crop ? 720 : 1280
        if longest > limit {
            let k = limit / longest
            let size = CGSize(width: image.size.width * k, height: image.size.height * k)
            image = UIGraphicsImageRenderer(size: size, format: format).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        }
        return image.jpegData(compressionQuality: 0.85)?.base64EncodedString() ?? ""
    }

    private func finish(_ base64: String) {
        if finished { return }
        finished = true
        let session = self.session
        DispatchQueue.global(qos: .userInitiated).async { if session.isRunning { session.stopRunning() } }
        let callback = onFinish
        dismiss(animated: true) { callback?(base64) }
    }
}

/// Une la petición de Kotlin con la pantalla de cámara.
enum CameraBridge {
    static func install() {
        IosCameraHooks.shared.handler = { cmd in
            DispatchQueue.main.async { handle(cmd) }
        }
    }

    private static func handle(_ cmd: String) {
        let p = cmd.components(separatedBy: "\u{1F}")
        guard p.count >= 5, p[0] == "capture" else { return }
        let aspect = CGFloat(Double(p[1]) ?? 0.716)
        let vc = CameraViewController(aspect: aspect, crop: p[2] == "true", hint: p[3], cancel: p[4])
        vc.onFinish = { base64 in IosCameraHooks.shared.deliver(base64: base64) }
        guard let top = topViewController() else {
            IosCameraHooks.shared.deliver(base64: "")
            return
        }
        top.present(vc, animated: true)
    }

    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = scenes.first(where: { $0.activationState == .foregroundActive }) ?? scenes.first
        var top = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController ?? scene?.windows.first?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
