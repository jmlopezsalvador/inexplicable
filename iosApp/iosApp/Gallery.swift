import UIKit
import PhotosUI
import Shared

// Selector de galería para la imagen de fondo. Kotlin pide con `IosGalleryHooks.request` y esta pantalla contesta con
// `IosGalleryHooks.deliver` (JPEG en base64, reducido a 1280 px de lado mayor; vacío si se cancela). `PHPickerViewController` no necesita
// permiso de acceso a la galería.

private final class GalleryPickerDelegate: NSObject, PHPickerViewControllerDelegate {
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        guard let provider = results.first?.itemProvider, provider.canLoadObject(ofClass: UIImage.self) else {
            picker.dismiss(animated: true) { IosGalleryHooks.shared.deliver(base64: "") }
            return
        }
        provider.loadObject(ofClass: UIImage.self) { object, _ in
            let result = (object as? UIImage).map { GalleryBridge.encode($0) } ?? ""
            DispatchQueue.main.async {
                picker.dismiss(animated: true) { IosGalleryHooks.shared.deliver(base64: result) }
            }
        }
    }
}

enum GalleryBridge {
    private static let delegate = GalleryPickerDelegate()

    static func install() {
        IosGalleryHooks.shared.handler = { _ in
            DispatchQueue.main.async { present() }
        }
    }

    private static func present() {
        var config = PHPickerConfiguration()
        config.filter = .images
        config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = delegate
        guard let top = topViewController() else {
            IosGalleryHooks.shared.deliver(base64: "")
            return
        }
        top.present(picker, animated: true)
    }

    /// Endereza la imagen, la reduce a 1280 px de lado mayor y la devuelve como JPEG en base64.
    static func encode(_ source: UIImage) -> String {
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let longest = max(source.size.width, source.size.height)
        let k = longest > 1280 ? 1280 / longest : 1
        let size = CGSize(width: source.size.width * k, height: source.size.height * k)
        let image = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            source.draw(in: CGRect(origin: .zero, size: size)) // al dibujar se aplica la orientación
        }
        return image.jpegData(compressionQuality: 0.85)?.base64EncodedString() ?? ""
    }

    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = scenes.first(where: { $0.activationState == .foregroundActive }) ?? scenes.first
        var top = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController ?? scene?.windows.first?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
