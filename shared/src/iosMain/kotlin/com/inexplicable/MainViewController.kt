package com.inexplicable

import androidx.compose.ui.window.ComposeUIViewController
import com.inexplicable.ui.InexplicableRoot
import com.inexplicable.ui.SettingsStore
import com.inexplicable.ui.UserDefaultsStore
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    InexplicableRoot(SettingsStore(UserDefaultsStore()))
}
