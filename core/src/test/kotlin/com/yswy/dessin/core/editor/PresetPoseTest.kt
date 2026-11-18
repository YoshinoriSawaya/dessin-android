package com.yswy.dessin.core.editor

import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.mesh.fitBounds
import com.yswy.dessin.core.skeleton.BodyParams
import com.yswy.dessin.core.skeleton.HandShape
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.StickShape
import com.yswy.dessin.core.skeleton.isStick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.acos

private const val D2R = PI / 180

/** E19-01: プリセットのポーズ */
class PresetPoseTest {
    @Test
    fun ポーズは9つ() {
        assertEquals(
            listOf("立つ", "歩く", "走る", "座る", "しゃがむ", "体育座り", "あぐら", "手を挙げる", "腕を広げる"),
            PresetPose.entries.map { it.label },
        )
    }

    private fun angle(a: Vec3, b: Vec3) = acos((a.normalized().dot(b.normalized())).coerceIn(-1.0, 1.0))

    @Test
    fun 各部位は定義した向きにおおむね向く() {
        for (p in PresetPose.entries) {
            val sk = presetSkeleton(p)
            val w = sk.worldTransforms()
            for ((id, target) in presetTargets(p)) {
                val dir = w.getValue(id).rotation.rotate(PartSpecs[id].restDir)
                val err = angle(dir, target) / D2R
                assertTrue("${p.label} $id は狙いから $err° ずれる", err < 15.0)
            }
        }
    }

    @Test
    fun 棒以外の全部位を置く() {
        for (p in PresetPose.entries) {
            val sk = presetSkeleton(p)
            PartSpecs.all.forEach { assertEquals("${p.label} ${it.id}", !isStick(it.id), sk.isPlaced(it.id)) }
        }
    }

    @Test
    fun 狙いの意味() {
        fun dir(p: PresetPose, id: String) = presetSkeleton(p).worldTransforms().getValue(id).rotation.rotate(PartSpecs[id].restDir)
        // 歩く: 左ももは前、右ももは後ろ
        assertTrue(dir(PresetPose.WALK, "thighL").z > 0.2 && dir(PresetPose.WALK, "thighR").z < -0.2)
        // 座る: ももはほぼ水平
        assertTrue(kotlin.math.abs(dir(PresetPose.SIT, "thighL").y) < 0.2)
        // 手を挙げる: 右の前腕は上
        assertTrue(dir(PresetPose.RAISE_HAND, "forearmR").y > 0.9)
        // 腕を広げる: 左右の上腕は横
        assertTrue(dir(PresetPose.ARMS_OUT, "upperArmL").x > 0.9 && dir(PresetPose.ARMS_OUT, "upperArmR").x < -0.9)
    }

    /** 体型・位置・向き・大きさ・棒・手の形を変えた人形 */
    private val doll: EditorState = run {
        var s = EditorState()
        listOf("clavL", "upperArmL", "forearmL", "handL", "stickL").forEach { s = s.select(it) }
        val yaw = Quat.fromAxisAngle(Vec3.Y, 0.7)
        val tilt = Quat.fromAxisAngle(Vec3.X, 0.3)
        val sk = s.skeleton
            .withBody(BodyParams(height = 1.2, thighLength = 1.1))
            .withRoot(s.skeleton.root.copy(position = Vec3(0.8, 1.5, -0.4), rotation = yaw * tilt, scale = 1.3))
            .withStickShape("stickL", StickShape(1.2, 0.1))
        s.copy(skeleton = sk, selected = "stickL")
    }

    @Test
    fun 当てると体型_横の位置_床の上の向き_大きさ_棒_手の形を保つ() {
        for (p in PresetPose.entries) {
            val s = doll.applyPreset(p)
            val sk = s.skeleton
            assertEquals(doll.skeleton.body, sk.body)
            assertEquals(0.8, sk.root.position.x, EPS)
            assertEquals(-0.4, sk.root.position.z, EPS)
            assertEquals(1.3, sk.root.scale, EPS)
            // 床の上の向き(鉛直軸まわり)は保ち、前傾はポーズのもの
            assertEquals(0.7, sk.root.rotation.toEulerYXZ().y, 1e-9)
            assertEquals(presetTilt(p), sk.root.rotation.toEulerYXZ().x, 1e-9)
            assertTrue(sk.isPlaced("stickL"))
            assertEquals(StickShape(1.2, 0.1), sk.stickShape("stickL"))
            assertEquals(doll.skeleton.state("stickL"), sk.state("stickL"))
            assertEquals(HandShape.FIST, sk.handShape("handL"))
            assertEquals("stickL", s.selected)
        }
    }

    @Test
    fun 当てたあとはいちばん低い点が床に付く() {
        for (p in PresetPose.entries) {
            val sk = doll.applyPreset(p).skeleton
            assertEquals(p.label, 0.0, fitBounds(sk)!!.first.y, 1e-9)
        }
    }

    @Test
    fun ほかの人形は変えない() {
        val two = doll.addDoll().switchDoll(0)
        val s = two.applyPreset(PresetPose.RUN)
        assertEquals(two.others, s.others)
    }
}
