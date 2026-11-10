package com.yswy.dessin.core.editor

import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertSameRotation
import com.yswy.dessin.core.math.assertVec
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.PartState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 期待値は docs/web-reference.html の orb の pointerdown・pointermove・drawOrb から。座標はオーブの中心を原点とする dp */
class OrbGestureTest {
    private val r = ORB_RADIUS
    private val cam = OrbitCamera(yaw = 0.7, pitch = 0.3).rotation

    @Test
    fun オーブの半径はWeb版と同じ() {
        // 88px の円から縁の 7px を除いた半径
        assertEquals(37.0, ORB_RADIUS, EPS)
        assertEquals(88.0, ORB_SIZE, EPS)
    }

    @Test
    fun 中央のドラッグは視点の縦横の軸まわりに傾ける() {
        val g = OrbGesture()
        g.down(5.0, -3.0)
        val q = g.move(15.0, 2.0, cam)!!
        val k = PI / (2 * r)
        val expected = Quat.fromAxisAngle(cam.rotate(Vec3.Y), 10.0 * k) * Quat.fromAxisAngle(cam.rotate(Vec3.X), 5.0 * k)
        assertSameRotation(expected, q)
        // 次の移動は直前の位置から数える
        val q2 = g.move(15.0, 12.0, cam)!!
        assertSameRotation(Quat.fromAxisAngle(cam.rotate(Vec3.X), 10.0 * k), q2)
    }

    @Test
    fun 外周のドラッグは視線まわりにひねる() {
        val g = OrbGesture()
        // 半径の 78% より外で押す(右)
        g.down(r * 0.9, 0.0)
        // 画面の上へ(反時計回りに)30度まわす。画面の y は下が正
        val a = 30 * PI / 180
        val q = g.move(r * 0.9 * cos(a), -r * 0.9 * sin(a), cam)!!
        assertSameRotation(Quat.fromAxisAngle(cam.rotate(Vec3.Z), a), q)
    }

    @Test
    fun 外周のひねりは負のπとπの境目をまたいでも小さい角度() {
        val g = OrbGesture()
        val near = { deg: Double -> Pair(-r * 0.9 * cos(deg * PI / 180), r * 0.9 * sin(deg * PI / 180)) }
        // 左(角度 ±180°)の少し上から少し下へ
        val (x0, y0) = near(-10.0)
        val (x1, y1) = near(10.0)
        g.down(x0, y0)
        val q = g.move(x1, y1, cam)!!
        assertSameRotation(Quat.fromAxisAngle(cam.rotate(Vec3.Z), 20 * PI / 180), q)
    }

    @Test
    fun 押していなければ回さない() {
        val g = OrbGesture()
        assertNull(g.move(10.0, 10.0, cam))
        g.down(0.0, 0.0)
        g.up()
        assertNull(g.move(10.0, 10.0, cam))
    }

    @Test
    fun 選択中の部位に回転を加える() {
        val s0 = EditorState().select("clavL").select("upperArmL")
        val q = Quat.fromAxisAngle(Vec3.X, -0.5)
        val s = s0.applyOrb(q)
        assertEquals(s0.skeleton.orb("upperArmL", q).state("upperArmL"), s.skeleton.state("upperArmL"))
        assertNotEquals(PartState.rest(PartSpecs["upperArmL"]), s.skeleton.state("upperArmL"))
        // 肋骨は回転がそのまま掛かる
        val rib = EditorState().applyOrb(q)
        assertSameRotation(q, rib.skeleton.root.rotation)
    }

    @Test
    fun 向きの表示は視点から見た関節の軸() {
        // 視点も関節も回っていなければ、軸はそのまま
        assertVec(Vec3.Z, orbProject(Quat.IDENTITY, Quat.IDENTITY, Vec3.Z))
        // 視点を y まわりに90度回すと、関節の +z(前)は視点の空間で −x(左)に見える
        val camRot = Quat.fromAxisAngle(Vec3.Y, PI / 2)
        assertVec(Vec3(-1.0, 0.0, 0.0), orbProject(camRot, Quat.IDENTITY, Vec3.Z))
        // 関節も同じだけ回っていれば、前は手前のまま
        assertVec(Vec3.Z, orbProject(camRot, camRot, Vec3.Z))
    }

    @Test
    fun 向きの表示の輪と印() {
        val pic = orbPicture(Quat.IDENTITY, Quat.IDENTITY)
        assertEquals(3, pic.rings.size)
        pic.rings.forEach { assertEquals(65, it.points.size) }
        // xz の輪(青)を真正面から見ると、横一直線(y=0)
        pic.rings.first { it.plane == OrbPlane.XZ }.points.forEach { assertEquals(0.0, it.y, EPS) }
        // 前の印は手前(z>0)の中心、上の印は上端
        assertVec(Vec3(0.0, 0.0, 1.0), pic.front)
        assertVec(Vec3(0.0, 1.0, 0.0), pic.up)
    }
}
