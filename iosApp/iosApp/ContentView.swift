import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
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
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
            .background(Color.black)
            .preferredColorScheme(.dark)
            .statusBarHidden(true)
            .modifier(HideSystemOverlays())
    }
}
