package com.inexplicable.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PhotoTest {
    @Test fun homographyMapsCorners() {
        val src = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f))
        val dst = listOf(Offset(100f, 50f), Offset(300f, 80f), Offset(280f, 300f), Offset(90f, 260f))
        val h = assertNotNull(homography(src, dst))
        for (i in 0 until 4) {
            val p = applyHomography(h, src[i])
            assertTrue(abs(p.x - dst[i].x) < 0.01f && abs(p.y - dst[i].y) < 0.01f, "esquina $i: $p")
        }
    }

    @Test fun degenerateSelectionIsRejected() {
        val src = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f))
        val line = listOf(Offset(0f, 0f), Offset(1f, 1f), Offset(2f, 2f), Offset(3f, 3f)) // todos alineados
        assertTrue(homography(src, line) == null)
    }

    /** Fabrica una cámara conocida, proyecta la baraja, recupera la cámara solo con las esquinas y comprueba que otro punto 3D cae igual. */
    @Test fun poseRecoveryReprojectsOtherPoints() {
        val viewW = 1000f; val viewH = 1000f
        val f = 0.75f * 1000f; val cx = 500f; val cy = 500f
        // Cámara real: mira hacia la mesa desde arriba y algo inclinada. Mundo (X, Y, Z): X a la derecha, Y hacia delante, Z arriba.
        val tilt = 0.5 // rad: la cámara mira hacia delante (+Y) y abajo
        val c = cos(tilt); val s = sin(tilt)
        // Filas = ejes de la cámara en el mundo: x derecha (+X), y abajo en la imagen, z hacia delante (0, sin, -cos). Determinante +1.
        val r = arrayOf(
            doubleArrayOf(1.0, 0.0, 0.0),
            doubleArrayOf(0.0, -c, -s),
            doubleArrayOf(0.0, s, -c),
        )
        // La cámara está en C (detrás de la baraja y arriba); t = -R·C.
        val camPos = doubleArrayOf(-0.5, -10.0, 30.0)
        val t = DoubleArray(3) { i -> -(r[i][0] * camPos[0] + r[i][1] * camPos[1] + r[i][2] * camPos[2]) }
        fun proj(x: Double, y: Double, z: Double): Offset {
            val xc = r[0][0] * x + r[0][1] * y + r[0][2] * z + t[0]
            val yc = r[1][0] * x + r[1][1] * y + r[1][2] * z + t[1]
            val zc = r[2][0] * x + r[2][1] * y + r[2][2] * z + t[2]
            return Offset((cx + f * xc / zc).toFloat(), (cy + f * yc / zc).toFloat())
        }
        val w = 6.3; val d = 8.8
        val corners = listOf(proj(-w / 2, d / 2, 0.0), proj(w / 2, d / 2, 0.0), proj(w / 2, -d / 2, 0.0), proj(-w / 2, -d / 2, 0.0))
        val cam = assertNotNull(PhotoCamera.fromCorners(corners, w.toFloat(), d.toFloat(), viewW, viewH))
        // Un punto a 6 cm sobre la baraja. Escena (x, y arriba, z) ↔ mundo (X, Y, Z) = (x, -z, y).
        val truth = proj(1.0, -2.0, 6.0)
        val got = assertNotNull(cam.project(V3(1f, 6f, 2f)))
        assertTrue(abs(got.x - truth.x) < 1.5f && abs(got.y - truth.y) < 1.5f, "esperado $truth, obtenido (${got.x}, ${got.y})")
        // Las esquinas de la baraja caen donde se marcaron.
        val tl = assertNotNull(cam.project(V3(-3.15f, 0f, -4.4f)))
        assertTrue(abs(tl.x - corners[0].x) < 1.5f && abs(tl.y - corners[0].y) < 1.5f)
    }
}
