package com.yswy.dessin.core.editor

import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Ray
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertSameRotation
import com.yswy.dessin.core.math.assertVec
import com.yswy.dessin.core.pick.pickAtPress
import com.yswy.dessin.core.render.DisplayOptions
import com.yswy.dessin.core.render.drawPlan
import com.yswy.dessin.core.skeleton.Anchor
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.PartState
import com.yswy.dessin.core.skeleton.Skeleton
import com.yswy.dessin.core.skeleton.dotPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val D2R = PI / 180

/**
 * E16-01: 骨盤が根のときは、脇腹を脇腹と骨盤の間の関節(骨盤の関節)で動かす。骨盤は止まり、脇腹と胸が一緒に動く。
 * みぞおち(胸と脇腹の間)は胸の操作で曲げる
 */
class WaistJointTest {
    private val camera = OrbitCamera(target = Vec3(0.0, 1.0, 0.0), yaw = 0.0, pitch = 0.0, distance = 2.0)
    private val chestRoot: EditorState = listOf("flank", "pelvis", "hipL", "thighL", "neck", "head")
        .fold(EditorState(camera = camera)) { s, id -> s.select(id) }
        .let { it.copy(skeleton = it.skeleton.withState("flank", PartState(15 * D2R, 0.0, 0.0))) }
    private val anchored = chestRoot.skeleton.withAnchor(Anchor.PELVIS)
    private val flank = chestRoot.copy(skeleton = anchored).select("flank")
    private val chest = chestRoot.copy(skeleton = anchored).select("rib")

    private fun w(s: Skeleton, id: String) = s.worldTransforms().getValue(id)

    private fun assertPelvisKept(before: Skeleton, after: Skeleton) {
        for (id in listOf("pelvis", "thighL")) {
            assertVec(w(before, id).position, w(after, id).position)
            assertSameRotation(w(before, id).rotation, w(after, id).rotation)
        }
    }

    /** 脇腹と胸が、骨盤の関節を中心に rot だけ回ったか。みぞおちの曲がり(脇腹の状態)は変わらない */
    private fun assertWaistRotated(before: Skeleton, after: Skeleton, rot: Quat) {
        assertPelvisKept(before, after)
        assertEquals(before.state("flank"), after.state("flank"))
        val c = w(before, "pelvis").position
        for (id in listOf("flank", "rib", "head")) {
            assertSameRotation(rot * w(before, id).rotation, w(after, id).rotation)
            assertVec(c + rot.rotate(w(before, id).position - c), w(after, id).position)
        }
    }

    // --- 脇腹: 骨盤の関節で動かす ---

    @Test
    fun 骨盤が根なら脇腹の回転オーブで骨盤は止まり_骨盤の関節を中心に脇腹と胸が回る() {
        val rot = Quat.fromAxisAngle(Vec3.X, 10 * D2R)
        val s = flank.applyOrb(rot)
        assertWaistRotated(flank.skeleton, s.skeleton, rot)
        assertNotEquals(flank.skeleton.state("pelvis"), s.skeleton.state("pelvis"))
    }

    @Test
    fun 骨盤が根なら脇腹の範囲は骨盤の関節に置き_玉は脇腹の上向き() {
        // ひねりが入ると玉は範囲の点からずれる(E10-01 から)。ここはひねりの入らない回し方で確かめる
        val s = flank.applyOrb(Quat.fromAxisAngle(Vec3.X, 12 * D2R)).skeleton
        assertEquals("pelvis", s.guideJoint("flank"))
        assertVec(w(s, "pelvis").position, s.guideCenter("flank"))
        val flankUp = w(s, "flank").rotation.rotate(Vec3.Y)
        assertVec(flankUp, (s.dotWorld("flank") - s.guideCenter("flank")).normalized())
        assertEquals(s.guideRadius("flank"), s.dotWorld("flank").distanceTo(s.guideCenter("flank")), EPS)
        // 範囲は骨盤の関節の可動域。玉はその上にある
        val onGuide = s.guideMatrix("flank").transformPoint(dotPosition(PartSpecs["pelvis"], s.state("pelvis")))
        assertVec(s.dotWorld("flank"), onGuide)
        assertEquals("flank", drawPlan(s, "flank", DisplayOptions()).guidePart)
    }

    @Test
    fun 骨盤が根なら脇腹の玉を前へドラッグすると骨盤の関節から上半身が前へ倒れる() {
        val s0 = flank.skeleton
        val c = s0.guideCenter("flank")
        val r = s0.guideRadius("flank")
        // 骨盤の上向きから前へ 20° の点
        val pelvisRot = w(s0, "pelvis").rotation
        val dir = pelvisRot.rotate(Vec3(0.0, cos(20 * D2R), sin(20 * D2R)))
        val ray = Ray(c + dir * r + pelvisRot.rotate(Vec3.X) * 3.0, pelvisRot.rotate(Vec3.X) * -1.0)
        val s = s0.drag("flank", ray)
        assertPelvisKept(s0, s)
        assertEquals(s0.state("flank"), s.state("flank"))
        assertVec(dir, (s.dotWorld("flank") - s.guideCenter("flank")).normalized())
        assertVec(dir, w(s, "flank").rotation.rotate(Vec3.Y))
        assertTrue("胸が前へ", w(s, "rib").position.z > w(s0, "rib").position.z + 0.01)
    }

    /** 骨盤から見た脇腹の上向きの、骨盤の上向きからの角度 */
    private fun leanFromPelvis(s: Skeleton): Double {
        val up = w(s, "pelvis").rotation.inverse().rotate(w(s, "flank").rotation.rotate(Vec3.Y))
        return acos(up.y.coerceIn(-1.0, 1.0))
    }

    @Test
    fun 骨盤が根なら脇腹の可動域は骨盤の関節のもの_前70度_後ろ35度() {
        val s0 = flank.skeleton
        val pelvisRot = w(s0, "pelvis").rotation
        fun aimed(z: Double) = s0.aim("flank", pelvisRot.rotate(Vec3(0.0, cos(85 * D2R), z * sin(85 * D2R))))
        val front = aimed(1.0)
        assertEquals(70.0, leanFromPelvis(front) / D2R, 0.5)
        assertTrue("前へ倒れる", w(front, "rib").position.z > w(s0, "rib").position.z)
        assertEquals(35.0, leanFromPelvis(aimed(-1.0)) / D2R, 0.5)
        assertPelvisKept(s0, front)
    }

    /** 上から見たときの、ワールドの向きの前(+z)の回り具合(+y 軸まわり、ラジアン) */
    private fun yaw(q: Quat): Double {
        val f = q.rotate(Vec3.Z)
        return atan2(f.x, f.z)
    }

    @Test
    fun 骨盤が根なら脇腹のひねりは骨盤の関節で_上半身が胸が根のときの骨盤と同じ向きに回る() {
        val slider = flank.sliders.first { it.kind == SliderKind.ROLL }
        assertEquals(-45, slider.min)
        assertEquals(45, slider.max)
        // 胸が根: 骨盤を選んでひねりを + にすると骨盤がある向きに回る
        val chestPelvis = chestRoot.select("pelvis")
        val c = chestPelvis.applySlider(SliderKind.ROLL, 20)
        val pelvisTurn = yaw(w(c.skeleton, "pelvis").rotation) - yaw(w(chestPelvis.skeleton, "pelvis").rotation)
        assertTrue(Math.abs(pelvisTurn) > 10 * D2R)
        // 骨盤が根: 脇腹のひねりを + にすると、脇腹(上半身)が同じ向きに回り、骨盤は止まる
        val p = flank.applySlider(SliderKind.ROLL, 20)
        val flankTurn = yaw(w(p.skeleton, "flank").rotation) - yaw(w(flank.skeleton, "flank").rotation)
        assertTrue(Math.abs(flankTurn) > 10 * D2R)
        assertEquals(Math.signum(pelvisTurn), Math.signum(flankTurn), 0.0)
        assertEquals(20, p.sliders.first { it.kind == SliderKind.ROLL }.value)
        assertPelvisKept(flank.skeleton, p.skeleton)
        assertEquals(flank.skeleton.state("flank"), p.skeleton.state("flank"))
        // 脇腹の長さは今までどおり
        assertTrue(SliderKind.TORSO_LENGTH in flank.sliders.map { it.kind })
    }

    @Test
    fun 骨盤が根なら脇腹の向きを戻すで骨盤の関節を自然な姿勢に戻す() {
        val bent = flank.applyOrb(Quat.fromAxisAngle(Vec3.X, 20 * D2R))
        val s = bent.resetSelected()
        assertEquals(NaturalPose.state(PartSpecs["pelvis"]), s.skeleton.state("pelvis"))
        assertEquals(bent.skeleton.state("flank"), s.skeleton.state("flank"))
        assertPelvisKept(bent.skeleton, s.skeleton)
    }

    @Test
    fun 骨盤が根なら脇腹の範囲を押しても後ろの骨盤を選ばない() {
        val s = flank.skeleton
        // 範囲の中の点(玉と骨盤の関節の間)を手前から押す。後ろに骨盤や脇腹がある
        val p = s.guideCenter("flank") + w(s, "flank").rotation.rotate(Vec3(0.0, 0.5, 0.3)) * s.guideRadius("flank")
        val ray = Ray(p + Vec3(0.0, 0.0, 3.0), Vec3(0.0, 0.0, -1.0))
        val press = pickAtPress(s, "flank", DisplayOptions(), ray)
        assertTrue("範囲に当たる", press.romPoint != null)
        assertTrue("骨盤を選ばない: ${press.part}", press.part != "pelvis")
    }

    // --- 胸: みぞおちで曲げる ---

    @Test
    fun 骨盤が根なら胸の回転オーブでみぞおちから胸が回り_脇腹と骨盤は止まる() {
        val rot = Quat.fromAxisAngle(Vec3.X, 10 * D2R)
        val s = chest.applyOrb(rot).skeleton
        assertPelvisKept(chest.skeleton, s)
        assertVec(w(chest.skeleton, "flank").position, w(s, "flank").position)
        assertSameRotation(w(chest.skeleton, "flank").rotation, w(s, "flank").rotation)
        assertSameRotation(rot * w(chest.skeleton, "rib").rotation, w(s, "rib").rotation)
        assertEquals(chest.skeleton.state("pelvis"), s.state("pelvis"))
    }

    @Test
    fun 骨盤が根なら胸の範囲はみぞおちに置き_玉は胸の上向き() {
        val s = chest.skeleton
        assertEquals("flank", s.guideJoint("rib"))
        assertVec(w(s, "flank").position, s.guideCenter("rib"))
        assertVec(w(s, "rib").rotation.rotate(Vec3.Y), (s.dotWorld("rib") - s.guideCenter("rib")).normalized())
        val onGuide = s.guideMatrix("rib").transformPoint(dotPosition(PartSpecs["flank"], s.state("flank")))
        assertVec(s.dotWorld("rib"), onGuide)
        assertEquals("rib", drawPlan(s, "rib", DisplayOptions()).guidePart)
        // 胸が根なら胸には範囲を出さない(今までどおり)
        assertEquals(null, drawPlan(chestRoot.skeleton, "rib", DisplayOptions()).guidePart)
    }

    @Test
    fun 骨盤が根なら胸の玉を前へドラッグすると上半身がみぞおちから前へ曲がる() {
        val s0 = chest.skeleton
        val c = s0.guideCenter("rib")
        val r = s0.guideRadius("rib")
        val flankRot = w(s0, "flank").rotation
        val dir = flankRot.rotate(Vec3(0.0, cos(15 * D2R), sin(15 * D2R)))
        val ray = Ray(c + dir * r + flankRot.rotate(Vec3.X) * 3.0, flankRot.rotate(Vec3.X) * -1.0)
        val s = s0.drag("rib", ray)
        assertPelvisKept(s0, s)
        assertVec(dir, w(s, "rib").rotation.rotate(Vec3.Y))
        assertVec(w(s0, "flank").position, w(s, "flank").position)
    }

    @Test
    fun 骨盤が根なら胸を押してドラッグすると全体は動かず胸が曲がる() {
        val wd = 400.0
        val h = 600.0
        fun screenOf(p: Vec3): Pair<Double, Double> {
            val ndc = (camera.projectionMatrix(wd / h) * camera.viewMatrix()).project(p)
            return Pair((ndc.x + 1) / 2 * wd, (1 - ndc.y) / 2 * h)
        }
        val g = TouchGesture()
        val press = screenOf(chest.skeleton.dotWorld("rib"))
        var s = g.down(chest, 0, press.first, press.second, 0, wd, h)
        s = g.move(s, 0, press.first, press.second + 10, wd, h)
        s = g.move(s, 0, press.first, press.second + 30, wd, h)
        assertPelvisKept(chest.skeleton, s.skeleton)
        assertNotEquals(chest.skeleton.state("flank"), s.skeleton.state("flank"))
        assertEquals(camera, s.camera)
    }

    @Test
    fun 骨盤が根なら胸のスライダーは傾きの3本を出さず_ひねりはみぞおちで符号を逆にする() {
        val kinds = chest.sliders.map { it.kind }
        assertFalse(SliderKind.RIB_TILT in kinds)
        assertFalse(SliderKind.RIB_TWIST in kinds)
        assertFalse(SliderKind.RIB_SIDE_TILT in kinds)
        assertTrue(kinds.containsAll(listOf(SliderKind.ROLL, SliderKind.RIB_SIZE, SliderKind.HEIGHT, SliderKind.BREAST_SIZE, SliderKind.TORSO_LENGTH)))
        // 胸が根なら今までどおり
        val chestKinds = chestRoot.select("rib").sliders.map { it.kind }
        assertTrue(SliderKind.RIB_TILT in chestKinds)
        assertFalse(SliderKind.ROLL in chestKinds)

        // 胸が根: 脇腹のひねりを + にすると、動く側の骨盤がある向きに回る
        val chestFlank = chestRoot.select("flank")
        val c = chestFlank.applySlider(SliderKind.ROLL, 20)
        val pelvisTurn = yaw(w(c.skeleton, "pelvis").rotation) - yaw(w(chestFlank.skeleton, "pelvis").rotation)
        // 骨盤が根: 胸のひねりを + にすると、動く側の胸が同じ向きに回る
        val p = chest.applySlider(SliderKind.ROLL, 20)
        val chestTurn = yaw(w(p.skeleton, "rib").rotation) - yaw(w(chest.skeleton, "rib").rotation)
        assertTrue(Math.abs(chestTurn) > 10 * D2R)
        assertEquals(Math.signum(pelvisTurn), Math.signum(chestTurn), 0.0)
        assertEquals(20, p.sliders.first { it.kind == SliderKind.ROLL }.value)
        assertEquals(-20 * D2R, p.skeleton.state("flank").roll, 1e-9)
        assertPelvisKept(chest.skeleton, p.skeleton)
        // 胸の大きさは今までどおり胸の置き方の大きさ
        assertEquals(1.2, chest.applySlider(SliderKind.RIB_SIZE, 120).skeleton.root.scale, 1e-9)
    }

    @Test
    fun 骨盤が根なら胸の向きを戻すでみぞおちを自然な姿勢に戻し_骨盤は動かない() {
        val bent = chest.applyOrb(Quat.fromAxisAngle(Vec3.X, 20 * D2R))
        val s = bent.resetSelected()
        assertEquals(NaturalPose.state(PartSpecs["flank"]), s.skeleton.state("flank"))
        assertPelvisKept(bent.skeleton, s.skeleton)
    }

    @Test
    fun 骨盤が根なら胸の説明は範囲で向きを決める() {
        assertEquals("緑の範囲の中をドラッグ、または2回タップして向きを決めます。", chest.selectionInfo)
        assertEquals("ドラッグで画面上の位置を決めます。", chestRoot.select("rib").selectionInfo)
    }

    @Test
    fun 骨盤を置いていなければ胸と脇腹は今までどおり() {
        val noPelvis = EditorState().select("flank").let { it.copy(skeleton = it.skeleton.withAnchor(Anchor.PELVIS)) }
        assertEquals("flank", noPelvis.skeleton.guideJoint("flank"))
        assertEquals("rib", noPelvis.skeleton.guideJoint("rib"))
        assertEquals(null, drawPlan(noPelvis.skeleton, "rib", DisplayOptions()).guidePart)
    }
}
