package com.inexplicable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Raíz Compose común (Android e iOS). Pantalla de ejemplo: un contador que se guarda en local. */
@Composable
fun InexplicableRoot(store: SettingsStore) {
    var settings by remember { mutableStateOf(store.load()) }
    SystemBarsEffect()
    MaterialTheme {
        Column(
            Modifier.fillMaxSize().background(Color.Black),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Inexplicable", color = Color.White)
            Text("Pulsaciones: ${settings.counter}", color = Color.White)
            Button(onClick = { settings = settings.copy(counter = settings.counter + 1).also(store::save) }) {
                Text("Sumar")
            }
        }
    }
}
