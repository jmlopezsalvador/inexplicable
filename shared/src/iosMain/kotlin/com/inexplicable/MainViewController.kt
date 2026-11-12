package com.inexplicable

import androidx.compose.ui.window.ComposeUIViewController
import com.inexplicable.ui.ConfigHost
import com.inexplicable.ui.InexplicableRoot
import com.inexplicable.ui.SettingsStore
import com.inexplicable.ui.UserDefaultsStore
import platform.UIKit.UIViewController

/** Pantalla principal. `onStartAR` abre la RA nativa (Swift) cuando el modo elegido es RA. */
fun MainViewController(onStartAR: () -> Unit): UIViewController = ComposeUIViewController {
    InexplicableRoot(SettingsStore(UserDefaultsStore()), onStartAR)
}

/** Solo la configuración, para mostrarla encima de la RA. `onClose` se llama al guardar o cancelar. */
fun ConfigViewController(onClose: () -> Unit): UIViewController = ComposeUIViewController {
    ConfigHost(SettingsStore(UserDefaultsStore()), arAvailable = true, onClose = onClose)
}
