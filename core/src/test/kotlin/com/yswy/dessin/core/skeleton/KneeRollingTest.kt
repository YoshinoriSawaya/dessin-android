package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertVec
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

private const val D2R = PI / 180

/**
 * E17-02: 大腿骨の丸みは脛骨の台の上を転がりながら滑る。曲げるほど接点が台の後ろへ移り、
 * すねの関節(台の中心)は接点から、すねの前向きに kneeRoll × KNEE_ROLLING × 曲げ角 だけ離れる
 */
class KneeRollingTest {
    private val legs = Skeleton.initial().place("flank").place("pelvis").place("hipL").place("thighL").place("shinL")
        .place("hipR").place("thighR").place("shinR")
    private val thighL = legs.worldTransforms().getValue("thighL")
    private val attachL = thighL.toWorld(PartSpecs["shinL"].attach)

    private fun bent(phi: Double, roll: Double = 0.0, kneeRoll: Double = 0.03) =
        legs.withBody(BodyParams(kneeRoll = kneeRoll)).withState("shinL", PartState(phi, 0.0, roll))

    @Test
    fun 伸ばしていれば関節の位置は変わらない() {
        assertVec(attachL, bent(0.0).guideCenter("shinL"))
    }

    @Test
    fun 曲げ90度で_滑りの分に加えて転がりの分だけすねの前へずれる() {
        // 滑り(E06-02): 後ろ 0.03・上 0.03。転がり: すねの前(90° で下)へ 0.03 × KNEE_ROLLING × π/2
        val rolling = 0.03 * KNEE_ROLLING * PI / 2
        assertVec(attachL + Vec3(0.0, 0.03 - rolling, -0.03), bent(PI / 2).guideCenter("shinL"))
    }

    @Test
    fun 接点は台の後ろへ移るが台からはみ出さない() {
        // 曲げ切った 150° でも、接点の台の中心からの距離はすねの半径(0.038)より小さい
        val shift = 0.03 * KNEE_ROLLING * 150 * D2R
        assert(shift in 0.02..0.038) { "$shift" }
    }

    @Test
    fun ずれ0なら転がりもしない() {
        assertVec(attachL, bent(PI / 2, kneeRoll = 0.0).guideCenter("shinL"))
    }

    @Test
    fun ひねっても関節の位置は変わらない() {
        assertVec(bent(PI / 2).guideCenter("shinL"), bent(PI / 2, roll = -40 * D2R).guideCenter("shinL"))
    }

    @Test
    fun 右も鏡像() {
        val sk = bent(PI / 2).withState("shinR", PartState(PI / 2, 0.0, 0.0))
        val l = sk.guideCenter("shinL") - legs.worldTransforms().getValue("rib").position
        val r = sk.guideCenter("shinR") - legs.worldTransforms().getValue("rib").position
        assertEquals(l.x, -r.x, EPS)
        assertEquals(l.y, r.y, EPS)
        assertEquals(l.z, r.z, EPS)
    }
}
