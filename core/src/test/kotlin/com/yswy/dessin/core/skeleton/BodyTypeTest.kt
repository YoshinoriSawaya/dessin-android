package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertVec
import com.yswy.dessin.core.mesh.PartVisuals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

/** E07-02: 体型の項目(首の長さと付け根、腕と脚の長さ、身長、ひざの転がりのずれ) */
class BodyTypeTest {
    private val all = listOf(
        "neck", "head", "flank", "pelvis",
        "clavL", "upperArmL", "forearmL", "handL", "hipL", "thighL", "shinL", "footL",
        "clavR", "upperArmR", "forearmR", "handR", "hipR", "thighR", "shinR", "footR",
    ).fold(Skeleton.initial()) { s, id -> s.place(id) }

    private fun world(body: BodyParams) = all.withBody(body).worldTransforms()

    @Test
    fun 既定の値はv1と同じ姿() {
        val b = BodyParams()
        assertEquals(1.0, b.neckLength, EPS)
        assertEquals(-0.035, b.neckOffset, EPS)
        assertEquals(1.0, b.upperArmLength, EPS)
        assertEquals(1.0, b.forearmLength, EPS)
        assertEquals(1.0, b.thighLength, EPS)
        assertEquals(1.0, b.shinLength, EPS)
        assertEquals(1.0, b.height, EPS)
        assertEquals(0.03, b.kneeRoll, EPS)
    }

    @Test
    fun 首の長さで頭の付け根が上がる() {
        val w = world(BodyParams(neckLength = 1.5))
        assertVec(w.getValue("neck").position + Vec3(0.0, 0.07 * 1.5, 0.0), w.getValue("head").position)
    }

    @Test
    fun 首の付け根は前後に動く() {
        val w = world(BodyParams(neckOffset = -0.05))
        assertVec(RootTransform.INITIAL_POSITION + Vec3(0.0, 0.15, -0.05), w.getValue("neck").position)
    }

    @Test
    fun 上腕と前腕の長さは左右共通で別々に効く() {
        val w = world(BodyParams(upperArmLength = 1.2, forearmLength = 0.8))
        for (s in listOf("L", "R")) {
            assertVec(w.getValue("upperArm$s").position + Vec3(0.0, -0.25 * 1.2, 0.0), w.getValue("forearm$s").position)
            assertVec(w.getValue("forearm$s").position + Vec3(0.0, -0.23 * 0.8, 0.0), w.getValue("hand$s").position)
        }
    }

    @Test
    fun ももとすねの長さで足の位置が変わる() {
        val w = world(BodyParams(thighLength = 1.1, shinLength = 0.9))
        for (s in listOf("L", "R")) {
            assertVec(w.getValue("thigh$s").position + Vec3(0.0, -0.40 * 1.1, 0.0), w.getValue("shin$s").position)
            assertVec(w.getValue("shin$s").position + Vec3(0.0, -0.38 * 0.9, 0.0), w.getValue("foot$s").position)
        }
    }

    @Test
    fun 可動域の表示と玉は部位の長さに合わせて伸びる() {
        val s = all.withBody(BodyParams(upperArmLength = 1.2, shinLength = 0.9))
        assertEquals(0.25 * 1.2, s.guideRadius("upperArmL"), EPS)
        assertEquals(1.2, s.guideScale("upperArmL"), EPS)
        assertEquals(0.25 * 1.2, s.dotWorld("upperArmL").distanceTo(s.guideCenter("upperArmL")), EPS)
        assertEquals(0.38 * 0.9, s.guideRadius("shinR"), EPS)
        // 長さを変えない部位は親の拡大率だけ
        assertEquals(1.0, s.guideScale("handL"), EPS)
    }

    @Test
    fun 身長は人形全体の拡大率で_胸の大きさと掛け合わさる() {
        val s = all.withBody(BodyParams(height = 1.2)).withRoot(RootTransform(scale = 1.5))
        val w = s.worldTransforms()
        assertEquals(1.8, w.getValue("rib").scale, EPS)
        assertVec(RootTransform.INITIAL_POSITION + Vec3(0.0, 0.15, -0.035) * 1.8, w.getValue("neck").position)
    }

    @Test
    fun ひざの転がりのずれを変えられる() {
        val thigh = all.worldTransforms().getValue("thighL")
        val attach = thigh.toWorld(PartSpecs["shinL"].attach)
        fun bentShin(kneeRoll: Double) =
            all.withBody(BodyParams(kneeRoll = kneeRoll)).withState("shinL", PartState(PI / 2, 0.0, 0.0)).guideCenter("shinL")
        // ずれ 0 なら関節で回る。0.05 なら 90 度で後ろ 0.05・上 0.05。
        // E17-02 で転がりの分(90° で下へ 0.05 × KNEE_ROLLING × π/2)が加わった
        val rolling = 0.05 * KNEE_ROLLING * PI / 2
        assertVec(attach, bentShin(0.0))
        assertVec(attach + Vec3(0.0, 0.05 - rolling, -0.05), bentShin(0.05))
    }

    @Test
    fun 長さを変えた部位の形は縦に伸びる() {
        val b = BodyParams(neckLength = 2.0, upperArmLength = 1.2, shinLength = 0.8)
        val neck = PartVisuals.placement("neck", 0, b)
        assertVec(Vec3(0.0, 0.035 * 2.0, 0.0), neck.position)
        assertVec(Vec3(1.0, 2.0, 1.0), neck.scale)
        val arm = PartVisuals.placement("upperArmR", 0, b)
        assertVec(Vec3(0.0, -0.125 * 1.2, 0.0), arm.position)
        assertVec(Vec3(1.0, 1.2, 1.0), arm.scale)
        val shin = PartVisuals.placement("shinL", 0, b)
        assertVec(Vec3(0.0, -0.19 * 0.8, 0.0), shin.position)
        assertVec(Vec3(1.0, 0.8, 1.0), shin.scale)
        // 関節の玉(index 1)は関節の位置のまま
        assertVec(Vec3.ZERO, PartVisuals.placement("upperArmL", 1, b).position)
        assertVec(Vec3(1.0, 1.0, 1.0), PartVisuals.placement("upperArmL", 1, b).scale)
    }
}
