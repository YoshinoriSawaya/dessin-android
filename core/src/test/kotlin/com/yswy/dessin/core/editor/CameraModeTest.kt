package com.yswy.dessin.core.editor

import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertVec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** E12: カメラだけを操作するモード。部位は動かさず、視点を回す・拡大縮小・平行移動する */
class CameraModeTest {
    private val w = 400.0
    private val h = 600.0
    private val camera = OrbitCamera(target = Vec3(0.0, 1.1, 0.0), yaw = 0.0, pitch = 0.0, distance = 2.0)
    private val start = EditorState(camera = camera).select("neck")
    private val cameraMode = start.toggleCameraOnly()

    private fun screenOf(state: EditorState, p: Vec3): Pair<Double, Double> {
        val cam = state.camera!!
        val ndc = (cam.projectionMatrix(w / h) * cam.viewMatrix()).project(p)
        return Pair((ndc.x + 1) / 2 * w, (1 - ndc.y) / 2 * h)
    }

    /** 選択中の首の玉の上 */
    private fun onNeckDot(s: EditorState) = screenOf(s, s.skeleton.dotWorld("neck"))

    @Test
    fun 既定はオフ_切り替えられる_画面の状態に残る() {
        assertFalse(start.cameraOnly)
        assertTrue(cameraMode.cameraOnly)
        assertFalse(cameraMode.toggleCameraOnly().cameraOnly)
        assertEquals(cameraMode, EditorStateCodec.decode(EditorStateCodec.encode(cameraMode)))
        // 行が無い古い状態はオフ
        val old = EditorStateCodec.encode(cameraMode).lines().filterNot { it.startsWith("cameraonly") }.joinToString("\n")
        assertFalse(EditorStateCodec.decode(old)!!.cameraOnly)
    }

    @Test
    fun ふつうは玉をドラッグすると部位が動く() {
        val g = TouchGesture()
        val p = onNeckDot(start)
        var s = g.down(start, 0, p.first, p.second, 0, w, h)
        s = g.move(s, 0, p.first + 30, p.second, w, h)
        assertNotEquals(start.skeleton.state("neck"), s.skeleton.state("neck"))
    }

    @Test
    fun モード中は玉の上をドラッグしても部位は動かず視点が回る() {
        val g = TouchGesture()
        val p = onNeckDot(cameraMode)
        var s = g.down(cameraMode, 0, p.first, p.second, 0, w, h)
        s = g.move(s, 0, p.first + 30, p.second, w, h)
        s = g.up(s, 0, p.first + 30, p.second, 100, w, h)
        assertEquals(cameraMode.skeleton, s.skeleton)
        assertNotEquals(cameraMode.camera!!.yaw, s.camera!!.yaw)
    }

    @Test
    fun モード中はタップしても部位を選ばない() {
        val g = TouchGesture()
        val p = screenOf(cameraMode, cameraMode.skeleton.guideCenter("rib"))
        val s = g.up(g.down(cameraMode, 0, p.first, p.second, 0, w, h), 0, p.first, p.second, 50, w, h)
        assertEquals("neck", s.selected)
        assertEquals(cameraMode, s)
    }

    @Test
    fun モード中は2本指のドラッグで平行移動する_距離は変わらない() {
        val g = TouchGesture()
        var s = g.down(cameraMode, 0, 100.0, 300.0, 0, w, h)
        s = g.down(s, 1, 200.0, 300.0, 0, w, h)
        s = g.move(s, 0, 140.0, 300.0, w, h)
        s = g.move(s, 1, 240.0, 300.0, w, h)
        val before = cameraMode.camera!!
        val after = s.camera!!
        assertEquals(before.distance, after.distance, 1e-9)
        assertEquals(before.yaw, after.yaw, 1e-12)
        // 指を右へ動かすと、人形が右へ動くように注視点は左(−x)へ動く
        assertTrue("注視点: ${after.target}", after.target.x < before.target.x - 0.01)
        assertEquals(before.target.y, after.target.y, 1e-9)
        assertEquals(cameraMode.skeleton, s.skeleton)
    }

    @Test
    fun モード中もピンチで拡大縮小する() {
        val g = TouchGesture()
        var s = g.down(cameraMode, 0, 150.0, 300.0, 0, w, h)
        s = g.down(s, 1, 250.0, 300.0, 0, w, h)
        s = g.move(s, 0, 100.0, 300.0, w, h)
        s = g.move(s, 1, 300.0, 300.0, w, h)
        assertTrue(s.camera!!.distance < cameraMode.camera!!.distance)
    }

    @Test
    fun 平行移動は注視点の奥行きで画面の移動に合わせる() {
        val moved = camera.pan(40.0, -20.0, h)
        assertVec(camera.target - camera.screenDelta(camera.target, 40.0, -20.0, h), moved.target)
    }
}
