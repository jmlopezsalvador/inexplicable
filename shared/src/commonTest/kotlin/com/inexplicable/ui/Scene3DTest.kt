package com.inexplicable.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Scene3DTest {
    private val cam = OrbitCamera(yaw = 30f, pitch = 40f, dist = 30f, widthPx = 1000f, heightPx = 2000f)

    @Test
    fun targetProjectsToScreenCenter() {
        val p = assertNotNull(cam.project(cam.target))
        assertTrue(abs(p.x - 500f) < 0.5f && abs(p.y - 1000f) < 0.5f, "centro: ${p.x}, ${p.y}")
        assertTrue(abs(p.depth - 30f) < 0.01f)
    }

    @Test
    fun pointBehindCameraIsNotProjected() {
        assertNull(cam.project(cam.position + (cam.position - cam.target)))
    }

    @Test
    fun higherPointAppearsHigherOnScreen() {
        val low = assertNotNull(cam.project(V3(0f, 0f, 0f)))
        val high = assertNotNull(cam.project(V3(0f, 10f, 0f)))
        assertTrue(high.y < low.y) // y de pantalla crece hacia abajo
    }
}
