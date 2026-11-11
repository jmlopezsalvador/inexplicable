package com.inexplicable.ui

import androidx.compose.runtime.Composable

/**
 * Ejemplo de `expect`/`actual`: lo que depende del sistema (barras, sensores, vibración...) se declara aquí
 * y se implementa en androidMain / iosMain. Todo lo demás va en común.
 */
@Composable
expect fun SystemBarsEffect()
