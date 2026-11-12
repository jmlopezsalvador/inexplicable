package com.inexplicable.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import kotlin.math.abs
import kotlin.math.sqrt

// Matemática del modo Foto: a partir de las 4 esquinas de la baraja en una foto se calcula desde dónde se hizo (pose de la cámara)
// y así se dibujan los mismos efectos 3D de la escena sobre la foto.

/**
 * Homografía 3x3 (fila a fila, 9 valores con h8 = 1) que lleva cada punto `src[i]` a `dst[i]`. Null si es degenerada.
 */
fun homography(src: List<Offset>, dst: List<Offset>): DoubleArray? {
    require(src.size == 4 && dst.size == 4)
    // 8 ecuaciones, 8 incógnitas (h0..h7).
    val a = Array(8) { DoubleArray(9) }
    for (i in 0 until 4) {
        val x = src[i].x.toDouble(); val y = src[i].y.toDouble()
        val u = dst[i].x.toDouble(); val v = dst[i].y.toDouble()
        a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
        a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
    }
    // Eliminación gaussiana con pivote parcial.
    for (col in 0 until 8) {
        var piv = col
        for (r in col + 1 until 8) if (abs(a[r][col]) > abs(a[piv][col])) piv = r
        if (abs(a[piv][col]) < 1e-12) return null
        val tmp = a[col]; a[col] = a[piv]; a[piv] = tmp
        for (r in 0 until 8) {
            if (r == col) continue
            val k = a[r][col] / a[col][col]
            for (c in col until 9) a[r][c] -= k * a[col][c]
        }
    }
    val h = DoubleArray(9)
    for (i in 0 until 8) h[i] = a[i][8] / a[i][i]
    h[8] = 1.0
    return h
}

/** Aplica la homografía a un punto. */
fun applyHomography(h: DoubleArray, p: Offset): Offset {
    val w = h[6] * p.x + h[7] * p.y + h[8]
    return Offset(((h[0] * p.x + h[1] * p.y + h[2]) / w).toFloat(), ((h[3] * p.x + h[4] * p.y + h[5]) / w).toFloat())
}

/** Matriz 4x4 de Compose (con perspectiva) equivalente a la homografía: sirve para dibujar una imagen deformada sobre un cuadrilátero. */
fun homographyToMatrix(h: DoubleArray): Matrix = Matrix(
    floatArrayOf(
        h[0].toFloat(), h[3].toFloat(), 0f, h[6].toFloat(),
        h[1].toFloat(), h[4].toFloat(), 0f, h[7].toFloat(),
        0f, 0f, 1f, 0f,
        h[2].toFloat(), h[5].toFloat(), 0f, h[8].toFloat(),
    ),
)

/**
 * Cámara deducida de una foto: `R` y `t` pasan del mundo (X, Y en el plano de la mesa, Z hacia arriba, en cm) a la cámara
 * (x derecha, y abajo, z hacia delante). Se expresa en coordenadas de la escena (x derecha, y arriba, z hacia el espectador):
 * mundo = (x, -z, y).
 */
class PhotoCamera(
    private val r: Array<DoubleArray>,
    private val t: DoubleArray,
    override val focal: Float,
    private val cx: Float,
    private val cy: Float,
) : SceneCamera {
    override val position: V3 = run {
        // Posición de la cámara en el mundo: -Rᵀ t.
        val wx = -(r[0][0] * t[0] + r[1][0] * t[1] + r[2][0] * t[2])
        val wy = -(r[0][1] * t[0] + r[1][1] * t[1] + r[2][1] * t[2])
        val wz = -(r[0][2] * t[0] + r[1][2] * t[1] + r[2][2] * t[2])
        V3(wx.toFloat(), wz.toFloat(), (-wy).toFloat())
    }

    override fun project(p: V3): P2? {
        val x = p.x.toDouble(); val y = -p.z.toDouble(); val z = p.y.toDouble()
        val xc = r[0][0] * x + r[0][1] * y + r[0][2] * z + t[0]
        val yc = r[1][0] * x + r[1][1] * y + r[1][2] * z + t[1]
        val zc = r[2][0] * x + r[2][1] * y + r[2][2] * z + t[2]
        if (zc < 1.0) return null
        return P2((cx + focal * xc / zc).toFloat(), (cy + focal * yc / zc).toFloat(), zc.toFloat())
    }

    companion object {
        /**
         * `corners` son las 4 esquinas de la baraja en pantalla, en este orden: arriba-izquierda, arriba-derecha, abajo-derecha, abajo-izquierda
         * (el lado de arriba es el corto). `w`×`d` son las medidas reales en cm. La imagen mostrada ocupa `viewW`×`viewH` píxeles
         * y se supone un objetivo de móvil normal (focal ≈ 0,75 × el lado largo). Null si la selección es degenerada.
         */
        fun fromCorners(corners: List<Offset>, w: Float, d: Float, viewW: Float, viewH: Float): PhotoCamera? {
            if (corners.size != 4) return null
            val plane = listOf(Offset(-w / 2, d / 2), Offset(w / 2, d / 2), Offset(w / 2, -d / 2), Offset(-w / 2, -d / 2))
            val h = homography(plane, corners) ?: return null
            val f = 0.75 * maxOf(viewW, viewH)
            val cx = viewW / 2.0; val cy = viewH / 2.0
            // B = K⁻¹ H; columnas b1, b2, b3.
            fun col(c: Int): DoubleArray {
                val v0 = h[c]; val v1 = h[3 + c]; val v2 = h[6 + c]
                return doubleArrayOf((v0 - cx * v2) / f, (v1 - cy * v2) / f, v2)
            }
            val b1 = col(0); val b2 = col(1); val b3 = col(2)
            fun len(v: DoubleArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            val l = (len(b1) + len(b2)) / 2
            if (l < 1e-9) return null
            val lambda = 1.0 / l
            var r1 = DoubleArray(3) { b1[it] * lambda }
            var r2 = DoubleArray(3) { b2[it] * lambda }
            var t = DoubleArray(3) { b3[it] * lambda }
            if (t[2] < 0) { r1 = DoubleArray(3) { -r1[it] }; r2 = DoubleArray(3) { -r2[it] }; t = DoubleArray(3) { -t[it] } }
            // Ortonormaliza r1, r2 y completa con r3.
            fun norm(v: DoubleArray): DoubleArray { val n = len(v); return DoubleArray(3) { v[it] / n } }
            fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
            val e1 = norm(r1)
            val d2 = DoubleArray(3) { r2[it] - dot(r2, e1) * e1[it] }
            if (len(d2) < 1e-9) return null
            val e2 = norm(d2)
            val e3 = doubleArrayOf(e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
            val rot = arrayOf(
                doubleArrayOf(e1[0], e2[0], e3[0]),
                doubleArrayOf(e1[1], e2[1], e3[1]),
                doubleArrayOf(e1[2], e2[2], e3[2]),
            )
            return PhotoCamera(rot, t, f.toFloat(), cx.toFloat(), cy.toFloat())
        }
    }
}

/**
 * Esquinas iniciales sugeridas (normalizadas 0..1 sobre la imagen de `imgW`×`imgH` píxeles) para una baraja vertical centrada, con la proporción
 * real de una carta (63×88): arriba-izq, arriba-der, abajo-der, abajo-izq.
 */
fun defaultDeckCorners(imgW: Int = 3, imgH: Int = 4): List<Offset> {
    val h = 0.45f                                   // alto de la selección (fracción del alto de la foto)
    val w = h * imgH * (63f / 88f) / imgW           // ancho en fracción del ancho, para conservar 63:88 en píxeles
    val x0 = 0.5f - w / 2; val x1 = 0.5f + w / 2
    val y0 = 0.5f - h / 2; val y1 = 0.5f + h / 2
    return listOf(Offset(x0, y0), Offset(x1, y0), Offset(x1, y1), Offset(x0, y1))
}
