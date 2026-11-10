package com.yswy.dessin.core.camera

import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertVec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** 期待値は docs/web-reference.html の updateCamera・pointermove・pinch・toolChip・fitView・screenDelta から */
class OrbitCameraTest {
    private val cam = OrbitCamera()
    private val fov = 35 * PI / 180

    @Test
    fun 初期値() {
        assertVec(Vec3(0.0, 1.10, 0.0), cam.target)
        assertEquals(0.4, cam.yaw, EPS)
        assertEquals(0.14, cam.pitch, EPS)
        assertEquals(2.2, cam.distance, EPS)
    }

    @Test
    fun 位置は注視点のまわりの球の上() {
        val cp = cos(0.14)
        assertVec(Vec3(2.2 * sin(0.4) * cp, 1.10 + 2.2 * sin(0.14), 2.2 * cos(0.4) * cp), cam.position)
    }

    @Test
    fun 向きは注視点を見て_右は水平() {
        val q = cam.rotation
        assertVec((cam.target - cam.position).normalized(), q.rotate(Vec3(0.0, 0.0, -1.0)))
        assertEquals(0.0, q.rotate(Vec3.X).y, EPS)
        assertTrue(q.rotate(Vec3.Y).y > 0)
    }

    @Test
    fun 指の移動で回る_縦は範囲に収める() {
        val c = cam.orbit(10.0, 5.0)
        assertEquals(0.4 - 0.08, c.yaw, EPS)
        assertEquals(0.14 + 0.03, c.pitch, EPS)
        assertEquals(1.4, cam.orbit(0.0, 1000.0).pitch, EPS)
        assertEquals(-1.2, cam.orbit(0.0, -1000.0).pitch, EPS)
    }

    @Test
    fun ズームは範囲に収める() {
        assertEquals(1.1, cam.zoom(0.5).distance, EPS)
        assertEquals(0.8, cam.zoom(0.01).distance, EPS)
        assertEquals(9.0, cam.zoom(100.0).distance, EPS)
    }

    @Test
    fun ピンチで指が開くと近づく() {
        // Web版: dist = dist * 前の指の間隔 / 今の指の間隔
        assertEquals(1.1, cam.pinch(100.0, 200.0).distance, EPS)
    }

    @Test
    fun 視点のプリセット() {
        assertEquals(Pair(0.0, 0.12), ViewPreset.FRONT.let { cam.preset(it).yaw to cam.preset(it).pitch })
        assertEquals(Pair(PI / 2, 0.12), ViewPreset.SIDE.let { cam.preset(it).yaw to cam.preset(it).pitch })
        assertEquals(Pair(PI, 0.12), ViewPreset.BACK.let { cam.preset(it).yaw to cam.preset(it).pitch })
        assertEquals(Pair(0.0, 1.35), ViewPreset.TOP.let { cam.preset(it).yaw to cam.preset(it).pitch })
        // 注視点と距離は変えない
        assertEquals(cam.distance, cam.preset(ViewPreset.TOP).distance, EPS)
    }

    @Test
    fun 画面の中央からのレイは注視点へ向かう() {
        val ray = cam.ray(540.0, 960.0, 1080.0, 1920.0)
        assertVec(cam.position, ray.origin)
        assertVec((cam.target - cam.position).normalized(), ray.direction)
    }

    @Test
    fun 画面の上端の中央からのレイは視野の半分だけ上を向く() {
        val ray = cam.ray(540.0, 0.0, 1080.0, 1920.0)
        val forward = (cam.target - cam.position).normalized()
        assertEquals(fov / 2, acos(ray.direction.dot(forward)), EPS)
        assertTrue(ray.direction.y > forward.y)
    }

    @Test
    fun 画面上の移動量をワールドに直す() {
        val p = cam.target
        val height = 1000.0
        // 画面の高さ分の移動は、その距離での視野の高さ
        val d = cam.screenDelta(p, height, 0.0, height)
        assertEquals(2 * cam.distance * tan(fov / 2), d.length(), EPS)
        assertVec(cam.rotation.rotate(Vec3.X) * d.length(), d)
        // 下へ動かすと下へ
        assertTrue(cam.screenDelta(p, 0.0, 10.0, height).dot(cam.rotation.rotate(Vec3.Y)) < 0)
    }

    @Test
    fun 全体を表示() {
        val min = Vec3(-0.3, 0.2, -0.1)
        val max = Vec3(0.3, 1.6, 0.2)
        val aspect = 0.6
        val c = cam.fit(min, max, aspect)
        assertVec(Vec3(0.0, 0.9, 0.05), c.target)
        val t = tan(fov / 2)
        val need = maxOf(1.4 / (2 * t), 0.6 / (2 * t * aspect), 0.3 / (2 * t * aspect))
        assertEquals(need * 1.5 + 0.2, c.distance, EPS)
        // 向きは変えない
        assertEquals(cam.yaw, c.yaw, EPS)
    }

    @Test
    fun 全体を表示の距離は範囲に収める() {
        assertEquals(1.6, cam.fit(Vec3(0.0, 1.0, 0.0), Vec3(0.01, 1.01, 0.01), 1.0).distance, EPS)
        assertEquals(9.0, cam.fit(Vec3(-10.0, -10.0, -10.0), Vec3(10.0, 10.0, 10.0), 1.0).distance, EPS)
    }

    @Test
    fun ビュー行列は視点を原点に_注視点を正面に置く() {
        val v = cam.viewMatrix()
        assertVec(Vec3.ZERO, v.transformPoint(cam.position))
        assertVec(Vec3(0.0, 0.0, -cam.distance), v.transformPoint(cam.target))
    }
}
