package com.yswy.dessin.core.editor

import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertSameRotation
import com.yswy.dessin.core.math.assertVec
import com.yswy.dessin.core.render.DisplayOptions
import com.yswy.dessin.core.render.drawPlan
import com.yswy.dessin.core.skeleton.Anchor
import com.yswy.dessin.core.skeleton.PartState
import com.yswy.dessin.core.skeleton.Skeleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan2

private const val D2R = PI / 180

/** E14-04: 骨盤が根のときは、骨盤の操作(回転オーブ・傾きのスライダー・ドラッグ)で人形全体を動かす・回す */
class PelvisRootTest {
    private val camera = OrbitCamera(target = Vec3(0.0, 1.0, 0.0), yaw = 0.0, pitch = 0.0, distance = 2.0)
    private val chestRoot: EditorState = listOf("flank", "pelvis", "hipL", "thighL", "neck", "head")
        .fold(EditorState(camera = camera)) { s, id -> s.select(id) }
        .let { it.copy(skeleton = it.skeleton.withState("flank", PartState(15 * D2R, 0.0, 0.0))) }
    private val pelvisRoot = chestRoot.copy(skeleton = chestRoot.skeleton.withAnchor(Anchor.PELVIS)).select("pelvis")

    private fun w(s: Skeleton, id: String) = s.worldTransforms().getValue(id)

    /** 全部位の状態(体の形)が変わらず、全体が rot で回ったか */
    private fun assertWholeRotated(before: Skeleton, after: Skeleton, rot: Quat) {
        assertEquals(before.states, after.states)
        val c = w(before, "pelvis").position
        assertVec(c, w(after, "pelvis").position)
        for (id in listOf("rib", "pelvis", "head", "thighL")) {
            assertSameRotation(rot * w(before, id).rotation, w(after, id).rotation)
            assertVec(c + rot.rotate(w(before, id).position - c), w(after, id).position)
        }
    }

    @Test
    fun 骨盤が根なら回転オーブで骨盤の関節を中心に全体が回る() {
        val rot = Quat.fromAxisAngle(Vec3(0.3, 1.0, 0.2).normalized(), 40 * D2R)
        val s = pelvisRoot.applyOrb(rot)
        assertWholeRotated(pelvisRoot.skeleton, s.skeleton, rot)
    }

    @Test
    fun 胸が根なら回転オーブで骨盤だけが回る_今までどおり() {
        val rot = Quat.fromAxisAngle(Vec3.X, 20 * D2R)
        val before = chestRoot.select("pelvis")
        val s = before.applyOrb(rot)
        assertEquals(before.skeleton.root, s.skeleton.root)
        assertTrue(before.skeleton.state("pelvis") != s.skeleton.state("pelvis"))
    }

    @Test
    fun 骨盤が根なら骨盤に胸と同じ傾きのスライダーを出し_ひねりの行は出さない() {
        val kinds = pelvisRoot.sliders.map { it.kind }
        assertTrue(kinds.containsAll(listOf(SliderKind.RIB_TILT, SliderKind.RIB_TWIST, SliderKind.RIB_SIDE_TILT)))
        assertFalse(SliderKind.ROLL in kinds)
        // 骨盤の幅と付け根の内寄りは今までどおり
        assertTrue(kinds.containsAll(listOf(SliderKind.PELVIS_WIDTH, SliderKind.HIP_INSET)))
        // 胸の大きさ・身長・乳房は胸だけ
        assertFalse(SliderKind.RIB_SIZE in kinds)
        // 胸が根なら今までどおり
        val chestKinds = chestRoot.select("pelvis").sliders.map { it.kind }
        assertTrue(SliderKind.ROLL in chestKinds)
        assertFalse(SliderKind.RIB_TILT in chestKinds)
    }

    @Test
    fun 骨盤が根なら傾きのスライダーは骨盤のワールドの向きで_動かすと骨盤を中心に全体が回る() {
        val e0 = w(pelvisRoot.skeleton, "pelvis").rotation.toEulerYXZ()
        fun value(kind: SliderKind) = pelvisRoot.sliders.first { it.kind == kind }.value
        assertEquals(Math.round(e0.x / D2R).toInt(), value(SliderKind.RIB_TILT))
        assertEquals(Math.round(e0.y / D2R).toInt(), value(SliderKind.RIB_TWIST))
        assertEquals(Math.round(e0.z / D2R).toInt(), value(SliderKind.RIB_SIDE_TILT))

        val s = pelvisRoot.applySlider(SliderKind.RIB_TILT, 30)
        val e1 = w(s.skeleton, "pelvis").rotation.toEulerYXZ()
        assertEquals(30 * D2R, e1.x, 1e-9)
        assertEquals(e0.y, e1.y, 1e-9)
        assertEquals(e0.z, e1.z, 1e-9)
        val rot = w(s.skeleton, "pelvis").rotation * w(pelvisRoot.skeleton, "pelvis").rotation.inverse()
        assertWholeRotated(pelvisRoot.skeleton, s.skeleton, rot)
        assertEquals(30, s.sliders.first { it.kind == SliderKind.RIB_TILT }.value)

        val twisted = pelvisRoot.applySlider(SliderKind.RIB_TWIST, 45).applySlider(SliderKind.RIB_SIDE_TILT, -20)
        val e2 = w(twisted.skeleton, "pelvis").rotation.toEulerYXZ()
        assertEquals(45 * D2R, e2.y, 1e-9)
        assertEquals(-20 * D2R, e2.z, 1e-9)
        assertVec(w(pelvisRoot.skeleton, "pelvis").position, w(twisted.skeleton, "pelvis").position)
    }

    @Test
    fun 骨盤が根なら骨盤のドラッグで全体が動く() {
        val w = 400.0
        val h = 600.0
        fun screenOf(p: Vec3): Pair<Double, Double> {
            val ndc = (camera.projectionMatrix(w / h) * camera.viewMatrix()).project(p)
            return Pair((ndc.x + 1) / 2 * w, (1 - ndc.y) / 2 * h)
        }
        val g = TouchGesture()
        // 骨盤の形の前面(真ん中の縦の継ぎ目を避けて少し横)
        val pelvis = w(pelvisRoot.skeleton, "pelvis")
        val press = screenOf(pelvis.toWorld(Vec3(0.05, -0.05, 0.1)))
        var s = g.down(pelvisRoot, 0, press.first, press.second, 0, w, h)
        s = g.move(s, 0, press.first + 10, press.second, w, h)
        s = g.move(s, 0, press.first + 30, press.second, w, h)
        val p0 = pelvis.position
        val p1 = p0 + camera.screenDelta(p0, 10.0, 0.0, h)
        val p2 = p1 + camera.screenDelta(p1, 20.0, 0.0, h)
        val delta = p2 - p0
        assertTrue("右へ動く: $delta", delta.x > 0.01)
        assertEquals(pelvisRoot.skeleton.states, s.skeleton.states)
        assertEquals(pelvisRoot.skeleton.root.rotation, s.skeleton.root.rotation)
        val moved = s.skeleton.root.position - pelvisRoot.skeleton.root.position
        assertEquals(delta.x, moved.x, EPS)
        assertEquals(delta.y, moved.y, EPS)
        assertEquals(delta.z, moved.z, EPS)
        assertEquals(camera, s.camera)
    }

    @Test
    fun 骨盤が根なら骨盤に緑の範囲を出さない() {
        assertNull(drawPlan(pelvisRoot.skeleton, "pelvis", DisplayOptions()).guidePart)
        assertEquals("pelvis", drawPlan(chestRoot.skeleton, "pelvis", DisplayOptions()).guidePart)
    }

    @Test
    fun 骨盤が根なら向きを戻すで骨盤をまっすぐに戻し_位置は保つ() {
        val tilted = pelvisRoot.applySlider(SliderKind.RIB_TILT, 30).applySlider(SliderKind.RIB_TWIST, 60)
        val s = tilted.resetSelected()
        assertSameRotation(Quat.IDENTITY, w(s.skeleton, "pelvis").rotation)
        assertVec(w(tilted.skeleton, "pelvis").position, w(s.skeleton, "pelvis").position)
        assertEquals(tilted.skeleton.states, s.skeleton.states)
    }

    @Test
    fun 骨盤が根なら説明は胸と同じく位置を決める() {
        assertEquals(EditorState().selectionInfo, pelvisRoot.selectionInfo)
    }

    /** 上から見たときの、ワールドの向きの前(+z)の回り具合(+y 軸まわり、ラジアン) */
    private fun yaw(q: Quat): Double {
        val f = q.rotate(Vec3.Z)
        return atan2(f.x, f.z)
    }

    @Test
    fun 骨盤が根なら脇腹のひねりのスライダーは動く側_上半身_が同じ向きに回る() {
        // 胸が根: スライダーを + にすると、動く側の骨盤がある向きに回る
        val chestFlank = chestRoot.select("flank")
        val c = chestFlank.applySlider(SliderKind.ROLL, 20)
        val pelvisTurn = yaw(w(c.skeleton, "pelvis").rotation) - yaw(w(chestFlank.skeleton, "pelvis").rotation)
        assertTrue(Math.abs(pelvisTurn) > 10 * D2R)
        // 骨盤が根: + にすると、動く側の胸が同じ向きに回る(脇腹が曲がっているので回る量は少し違う。向きを比べる)
        val pelvisFlank = pelvisRoot.select("flank")
        val p = pelvisFlank.applySlider(SliderKind.ROLL, 20)
        val chestTurn = yaw(w(p.skeleton, "rib").rotation) - yaw(w(pelvisFlank.skeleton, "rib").rotation)
        assertTrue(Math.abs(chestTurn) > 10 * D2R)
        assertEquals(Math.signum(pelvisTurn), Math.signum(chestTurn), 0.0)
        // スライダーの値は操作した値のまま見える
        assertEquals(20, p.sliders.first { it.kind == SliderKind.ROLL }.value)
        assertVec(w(pelvisFlank.skeleton, "pelvis").position, w(p.skeleton, "pelvis").position)
    }
}
