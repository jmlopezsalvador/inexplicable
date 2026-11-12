import SwiftUI
import UIKit

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Conecta Kotlin con el sintetizador de sonido antes de que arranque la interfaz.
        SoundBridge.install()
        SpeechBridge.install()
    }

    var body: some Scene {
        WindowGroup { ContentView() }
            .onChange(of: scenePhase) { phase in
                // Al pasar a segundo plano se para el audio; al volver se reanuda.
                switch phase {
                case .background: SoundBridge.synth.pause()
                case .active: SoundBridge.synth.resume()
                default: break
                }
            }
    }
}
