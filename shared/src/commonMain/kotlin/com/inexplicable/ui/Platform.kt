package com.inexplicable.ui

import androidx.compose.runtime.Composable

/**
 * Ejemplo de `expect`/`actual`: lo que depende del sistema (barras, sensores, vibración...) se declara aquí
 * y se implementa en androidMain / iosMain. Todo lo demás va en común.
 */
@Composable
expect fun SystemBarsEffect()

/** Avisa cuando la app pasa a segundo plano (`onPause`) o vuelve (`onResume`). iOS: sin comportamiento. */
@Composable
expect fun LifecycleEffect(onPause: () -> Unit, onResume: () -> Unit)

/** Código de idioma del dispositivo en minúsculas (`"es"`, `"en"`...). */
expect fun deviceLanguage(): String
