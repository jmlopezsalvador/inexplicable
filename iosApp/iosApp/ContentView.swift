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
    @State private var showAR = false

    var body: some View {
        ZStack(alignment: .top) {
            ComposeView()
            // Entrada oculta al prototipo de RA (RA-0): pulsación larga en el centro superior.
            Color.clear
                .frame(width: 120, height: 100)
                .contentShape(Rectangle())
                .onLongPressGesture(minimumDuration: 1.5) { showAR = true }
        }
        .ignoresSafeArea(.all)
        .background(Color.black)
        .preferredColorScheme(.dark)
        .statusBarHidden(true)
        .modifier(HideSystemOverlays())
        .fullScreenCover(isPresented: $showAR) {
            ARScreen(onClose: { showAR = false })
        }
    }
}
