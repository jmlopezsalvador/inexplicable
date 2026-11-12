import SwiftUI
import Shared

/// Pantalla principal en Compose. `onStartAR` se llama cuando el usuario pulsa EMPEZAR con el modo RA elegido.
struct ComposeView: UIViewControllerRepresentable {
    let onStartAR: () -> Void
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(onStartAR: onStartAR)
    }
    func updateUIViewController(_ vc: UIViewController, context: Context) {}
}

/// Solo la configuración (Compose), para abrirla encima de la RA.
struct ConfigComposeView: UIViewControllerRepresentable {
    let onClose: () -> Void
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.ConfigViewController(onClose: onClose)
    }
    func updateUIViewController(_ vc: UIViewController, context: Context) {}
}

/// Oculta el indicador de inicio (la barra de abajo) para el modo inmersivo; iOS 16 o posterior.
struct HideSystemOverlays: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 16.0, *) {
            content.persistentSystemOverlays(.hidden)
        } else {
            content
        }
    }
}

struct ContentView: View {
    @State private var showAR = false

    var body: some View {
        ComposeView(onStartAR: { showAR = true })
            .ignoresSafeArea(.all)
            .background(Color.black)
            .preferredColorScheme(.dark)
            .statusBarHidden(true)
            .modifier(HideSystemOverlays())
            // La RA es un juego nuevo. Su reset (pulsación larga arriba a la izquierda) la cierra y vuelve al menú.
            .fullScreenCover(isPresented: $showAR) {
                ARScreen(onClose: { showAR = false })
            }
    }
}
