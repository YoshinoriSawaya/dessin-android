package com.yswy.dessin.core.editor

import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.skeleton.PartState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 期待値は docs/web-reference.html の pointerdown・pointermove・endPtr から。座標は dp(Web版の CSS px に当たる) */
class TouchGestureTest {
    private val w = 400.0
    private val h = 600.0
    private val camera = OrbitCamera(target = Vec3(0.0, 1.1, 0.0), yaw = 0.0, pitch = 0.0, distance = 2.0)
    private val start = EditorState(camera = camera)

    /** ワールドの点が映る画面の位置 */
    private fun screenOf(state: EditorState, p: Vec3): Pair<Double, Double> {
        val cam = state.camera!!
        val ndc = (cam.projectionMatrix(w / h) * cam.viewMatrix()).project(p)
        return Pair((ndc.x + 1) / 2 * w, (1 - ndc.y) / 2 * h)
    }

    private class Finger(val g: TouchGesture, val w: Double, val h: Double) {
        fun down(s: EditorState, id: Int, p: Pair<Double, Double>, t: Long = 0) = g.down(s, id, p.first, p.second, t, w, h)
        fun move(s: EditorState, id: Int, p: Pair<Double, Double>) = g.move(s, id, p.first, p.second, w, h)
        fun up(s: EditorState, id: Int, p: Pair<Double, Double>, t: Long = 0) = g.up(s, id, p.first, p.second, t, w, h)
        fun tap(s: EditorState, p: Pair<Double, Double>, t: Long = 0) = up(down(s, 0, p, t), 0, p, t + 50)
    }

    private fun finger() = Finger(TouchGesture(), w, h)
    private val empty = Pair(20.0, 20.0)

    // ---------- タップ ----------

    @Test
    fun タップで部位を選ぶ_ゴーストなら置く() {
        val f = finger()
        val s = f.tap(start, screenOf(start, start.skeleton.guideCenter("neck") + Vec3(0.0, 0.035, 0.0)))
        assertEquals("neck", s.selected)
        assertTrue(s.skeleton.isPlaced("neck"))
    }

    @Test
    fun 何も無いところのタップでは何も変わらない() {
        val f = finger()
        assertEquals(start, f.tap(start, empty))
    }

    // ---------- 1本指のドラッグ ----------

    @Test
    fun 何も無いところからのドラッグで視点が回る() {
        val f = finger()
        var s = f.down(start, 0, empty)
        s = f.move(s, 0, Pair(30.0, 25.0))
        s = f.move(s, 0, Pair(50.0, 35.0))
        // 最初の移動は6dp を超えた時点のもの(30-20, 25-20)から数える。Web版と同じく、超えた回の移動量から効く
        assertEquals(camera.yaw - (10.0 + 20.0) * 0.008, s.camera!!.yaw, EPS)
        assertEquals(camera.pitch + (5.0 + 10.0) * 0.006, s.camera.pitch, EPS)
        s = f.up(s, 0, Pair(50.0, 35.0))
        assertEquals(start.selected, s.selected)
    }

    @Test
    fun 動きが6dpに満たなければ回らない_タップになる() {
        val f = finger()
        val neck = screenOf(start, start.skeleton.guideCenter("neck") + Vec3(0.0, 0.035, 0.0))
        var s = f.down(start, 0, neck)
        s = f.move(s, 0, Pair(neck.first + 3, neck.second + 3))
        assertEquals(camera, s.camera)
        s = f.up(s, 0, Pair(neck.first + 3, neck.second + 3))
        assertEquals("neck", s.selected)
    }

    @Test
    fun 選択中の部位からのドラッグで向きが変わる_視点は回らない() {
        val f = finger()
        val s0 = start.select("clavL").select("upperArmL")
        val dot = screenOf(s0, s0.skeleton.dotWorld("upperArmL"))
        var s = f.down(s0, 0, dot)
        // 玉を画面の右上へ引く
        s = f.move(s, 0, Pair(dot.first + 40, dot.second - 60))
        assertEquals(camera, s.camera)
        assertNotEquals(PartState.rest(com.yswy.dessin.core.skeleton.PartSpecs["upperArmL"]), s.skeleton.state("upperArmL"))
        // 指の先(レイ)に向けた結果と同じ
        val ray = camera.ray(dot.first + 40, dot.second - 60, w, h)
        assertEquals(s0.skeleton.drag("upperArmL", ray).state("upperArmL"), s.skeleton.state("upperArmL"))
        s = f.up(s, 0, Pair(dot.first + 40, dot.second - 60))
        assertEquals("upperArmL", s.selected)
    }

    @Test
    fun 選択中の肋骨からのドラッグで肋骨が動く() {
        val f = finger()
        val rib = screenOf(start, start.skeleton.guideCenter("rib"))
        var s = f.down(start, 0, rib)
        s = f.move(s, 0, Pair(rib.first + 10, rib.second))
        s = f.move(s, 0, Pair(rib.first + 30, rib.second))
        val moved = s.skeleton.root.position
        val expected = start.skeleton.root.position.let { p0 ->
            val p1 = p0 + camera.screenDelta(p0, 10.0, 0.0, h)
            p1 + camera.screenDelta(p1, 20.0, 0.0, h)
        }
        assertEquals(expected.x, moved.x, EPS)
        assertEquals(expected.y, moved.y, EPS)
        assertEquals(expected.z, moved.z, EPS)
        assertEquals(camera, s.camera)
    }

    // ---------- 2本指 ----------

    @Test
    fun ピンチで指を開くと近づく() {
        val f = finger()
        var s = f.down(start, 0, Pair(100.0, 300.0))
        s = f.down(s, 1, Pair(200.0, 300.0))
        s = f.move(s, 1, Pair(300.0, 300.0))
        assertEquals(camera.distance * 100.0 / 200.0, s.camera!!.distance, EPS)
        assertEquals(camera.yaw, s.camera.yaw, EPS)
    }

    @Test
    fun ピンチのあと1本指だけ残しても回らない_タップにもならない() {
        val f = finger()
        val neck = screenOf(start, start.skeleton.guideCenter("neck") + Vec3(0.0, 0.035, 0.0))
        var s = f.down(start, 0, neck)
        s = f.down(s, 1, Pair(200.0, 300.0))
        s = f.up(s, 1, Pair(200.0, 300.0))
        val before = s.camera
        s = f.move(s, 0, Pair(neck.first + 50, neck.second))
        assertEquals(before, s.camera)
        s = f.up(s, 0, Pair(neck.first + 50, neck.second))
        assertEquals("rib", s.selected)
    }

    // ---------- ダブルタップ ----------

    private val neckSelected = start.select("neck")
    /** 首の可動域(真上、半径 0.2)の中の点。頭のゴースト(球と顔の面)より外側 */
    private val romSpot get() = screenOf(neckSelected, neckSelected.skeleton.guideCenter("neck") + Vec3(0.11, 0.165, 0.0))

    @Test
    fun 緑の範囲のダブルタップでその点へ向ける() {
        val f = finger()
        var s = f.tap(neckSelected, romSpot, t = 0)
        assertEquals(neckSelected, s)
        s = f.tap(s, romSpot, t = 300)
        assertNotEquals(neckSelected.skeleton.state("neck"), s.skeleton.state("neck"))
        assertEquals("neck", s.selected)
    }

    @Test
    fun 間が420ms以上あくとダブルタップにならない() {
        val f = finger()
        var s = f.tap(neckSelected, romSpot, t = 0)
        s = f.tap(s, romSpot, t = 500)
        assertEquals(neckSelected.skeleton.state("neck"), s.skeleton.state("neck"))
    }

    @Test
    fun 位置が32dp以上ずれるとダブルタップにならない() {
        val f = finger()
        var s = f.tap(neckSelected, romSpot, t = 0)
        s = f.tap(s, Pair(romSpot.first + 40, romSpot.second), t = 200)
        assertEquals(neckSelected.skeleton.state("neck"), s.skeleton.state("neck"))
    }

    @Test
    fun 取り消されたら何もしない() {
        val f = finger()
        val neck = screenOf(start, start.skeleton.guideCenter("neck") + Vec3(0.0, 0.035, 0.0))
        val s = f.g.cancel(f.down(start, 0, neck), 0)
        assertSame(start.skeleton, s.skeleton)
        assertEquals("rib", s.selected)
    }
}
