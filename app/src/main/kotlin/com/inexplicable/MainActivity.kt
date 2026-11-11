package com.inexplicable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.inexplicable.ui.InexplicableRoot
import com.inexplicable.ui.SettingsStore
import com.inexplicable.ui.SharedPrefsStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val store = SettingsStore(SharedPrefsStore(applicationContext))
        setContent { InexplicableRoot(store) }
    }
}
