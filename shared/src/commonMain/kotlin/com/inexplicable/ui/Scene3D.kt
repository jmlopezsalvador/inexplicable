package com.inexplicable.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Matemática 3D pura (unidades: cm; x derecha, y arriba, z hacia el espectador). La escena está en DeckScene.kt.

data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = V3(x * k, y * k, z * k)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun norm(): V3 { val l = sqrt(dot(this)); return if (l == 0f) this else this * (1f / l) }
}

/** Punto proyectado: posición en pantalla y profundidad (distancia a lo largo de la vista). */
data class P2(val x: Float, val y: Float, val depth: Float)

/** Cámara de la escena: proyecta puntos 3D (cm; x derecha, y arriba, z hacia el espectador) a píxeles de pantalla. */
interface SceneCamera {
    /** Posición de la cámara en coordenadas de la escena. */
    val position: V3
    /** Distancia focal en píxeles (sirve para escalar tamaños según la profundidad). */
    val focal: Float
    /** Null si el punto está detrás de la cámara. */
    fun project(p: V3): P2?
}

/** Cámara orbital alrededor de `target`. `yaw` y `pitch` en grados, `dist` en cm, `focal` en píxeles. */
class OrbitCamera(
    val yaw: Float, val pitch: Float, val dist: Float,
    val widthPx: Float, val heightPx: Float,
    val target: V3 = V3(0f, 1.5f, 0f),
    override val focal: Float = minOf(widthPx, heightPx) * 1.25f,
) : SceneCamera {
    private val yr = yaw * PI.toFloat() / 180f
    private val pr = pitch * PI.toFloat() / 180f
    override val position = target + V3(cos(pr) * sin(yr), sin(pr), cos(pr) * cos(yr)) * dist
    private val f = (target - position).norm()
    private val r = f.cross(V3(0f, 1f, 0f)).norm()
    private val u = r.cross(f)

    /** Null si el punto está detrás de la cámara. */
    override fun project(p: V3): P2? {
        val v = p - position
        val z = v.dot(f)
        if (z < 1f) return null
        return P2(widthPx / 2 + focal * v.dot(r) / z, heightPx / 2 - focal * v.dot(u) / z, z)
    }
}
