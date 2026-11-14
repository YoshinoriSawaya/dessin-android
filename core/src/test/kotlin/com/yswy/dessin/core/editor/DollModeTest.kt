package com.yswy.dessin.core.editor

import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** E13-02: 人形のモード。3D でタップした人形に切り替え、部位は動かさない */
class DollModeTest {
    private val w = 400.0
    private val h = 600.0
    private val two = EditorState().select("neck").addDoll()
        .let { it.withCamera(OrbitCamera(target = Vec3(0.3, 1.0, 0.0), yaw = 0.0, pitch = 0.0, distance = 3.0)) }

    private fun screenOf(state: EditorState, p: Vec3): Pair<Double, Double> {
        val cam = state.camera!!
        val ndc = (cam.projectionMatrix(w / h) * cam.viewMatrix()).project(p)
        return Pair((ndc.x + 1) / 2 * w, (1 - ndc.y) / 2 * h)
    }

    private fun tap(g: TouchGesture, s: EditorState, p: Pair<Double, Double>) =
        g.up(g.down(s, 0, p.first, p.second, 0, w, h), 0, p.first, p.second, 50, w, h)

    private fun chest(s: EditorState, i: Int) = screenOf(s, s.dolls[i].root.position + Vec3(0.0, 0.05, 0.0))

    @Test
    fun 既定はオフ_切り替えられる() {
        assertFalse(two.dollMode)
        assertTrue(two.toggleDollMode().dollMode)
        assertFalse(two.toggleDollMode().toggleDollMode().dollMode)
    }

    @Test
    fun ふだんはほかの人形をタップしても切り替わらない() {
        val s = tap(TouchGesture(), two, chest(two, 0))
        assertEquals(1, s.activeDoll)
    }

    @Test
    fun 人形のモード中はタップした人形に切り替わる() {
        val mode = two.toggleDollMode()
        val s = tap(TouchGesture(), mode, chest(mode, 0))
        assertEquals(0, s.activeDoll)
        assertTrue(s.dollMode)
        // 何も無いところのタップでは変わらない
        assertEquals(1, tap(TouchGesture(), mode, Pair(5.0, 5.0)).activeDoll)
    }

    @Test
    fun 人形のモード中のドラッグは視点を回すだけ() {
        val mode = two.toggleDollMode()
        val g = TouchGesture()
        val p = chest(mode, 1)
        var s = g.down(mode, 0, p.first, p.second, 0, w, h)
        s = g.move(s, 0, p.first + 40, p.second, w, h)
        assertEquals(mode.dolls, s.dolls)
        assertTrue(s.camera!!.yaw != mode.camera!!.yaw)
    }
}
