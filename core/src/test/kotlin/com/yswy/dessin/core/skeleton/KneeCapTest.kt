package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertVec
import com.yswy.dessin.core.mesh.MeshKind
import com.yswy.dessin.core.mesh.PartVisuals
import com.yswy.dessin.core.mesh.meshWorldMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

private const val D2R = PI / 180

/** E17-03: 膝蓋骨は大腿骨の丸みの前面を滑り、膝蓋腱(長さ一定)で脛骨粗面とつながる */
class KneeCapTest {
    private val legs = Skeleton.initial().place("flank").place("pelvis").place("hipL").place("thighL").place("shinL")
        .place("hipR").place("thighR").place("shinR")

    private fun bent(deg: Double, roll: Double = 0.0, body: BodyParams = BodyParams()) =
        legs.withBody(body).withState("shinL", PartState(deg * D2R, 0.0, roll))

    @Test
    fun 曲げても腱の長さは変わらない() {
        for (body in listOf(BodyParams(), BodyParams(kneeRoll = 0.0), BodyParams(kneeRoll = 0.06, shinLength = 0.8))) {
            val rest = kneeCap(bent(0.0, body = body), "shinL")
            for (deg in listOf(15.0, 45.0, 90.0, 120.0, 150.0)) {
                val c = kneeCap(bent(deg, body = body), "shinL")
                assertEquals("$body $deg", rest.tendonLength, c.lowerPole.distanceTo(c.tuberosity), 1e-6)
            }
        }
    }

    @Test
    fun 曲げるほど膝蓋骨は丸みに沿って下へ回る() {
        val angles = listOf(0.0, 30.0, 60.0, 90.0, 120.0, 150.0).map { kneeCap(bent(it), "shinL").angle }
        angles.zipWithNext().forEach { (a, b) -> assertTrue("$a < $b", a < b) }
        // 90° でも前(+z)にあり、伸ばしたときより低い
        val straight = kneeCap(bent(0.0), "shinL")
        val c = kneeCap(bent(90.0), "shinL")
        assertTrue(c.patellaCenter.z > 0.0)
        assertTrue(c.patellaCenter.y < straight.patellaCenter.y)
    }

    @Test
    fun 膝蓋骨は大腿骨の丸みの中心から一定の距離() {
        val center = PartSpecs["shinL"].attach + Vec3(0.0, 0.03, 0.0)
        listOf(0.0, 70.0, 150.0).forEach { assertEquals(PATELLA_RADIUS, kneeCap(bent(it), "shinL").patellaCenter.distanceTo(center), 1e-9) }
    }

    @Test
    fun ひねっても膝蓋骨は正中の面にある() {
        val c = kneeCap(bent(90.0, roll = -40 * D2R), "shinL")
        assertEquals(0.0, c.patellaCenter.x, 1e-12)
    }

    @Test
    fun 見た目はすねに含まれ_ももの空間の位置に描かれる() {
        val sk = bent(90.0)
        val i = PartVisuals.meshes("shinL").indexOfFirst { it.knee == KneePiece.PATELLA }
        assertTrue(i >= 0)
        assertEquals(MeshKind.BODY, PartVisuals.meshes("shinL")[i].kind)
        val thigh = sk.worldTransforms().getValue("thighL")
        assertVec(thigh.toWorld(kneeCap(sk, "shinL").patellaCenter), meshWorldMatrix(sk, "shinL", i).transformPoint(Vec3.ZERO))
        // 腱は下端が脛骨粗面、上端が膝蓋骨の下端(長さ1の円柱を伸ばして置く)
        val t = PartVisuals.meshes("shinL").indexOfFirst { it.knee == KneePiece.TENDON }
        val m = meshWorldMatrix(sk, "shinL", t)
        val c = kneeCap(sk, "shinL")
        assertVec(thigh.toWorld(c.tuberosity), m.transformPoint(Vec3(0.0, -0.5, 0.0)))
        assertVec(thigh.toWorld(c.lowerPole), m.transformPoint(Vec3(0.0, 0.5, 0.0)))
    }

    @Test
    fun 右は鏡像() {
        val sk = bent(80.0).withState("shinR", PartState(80 * D2R, 0.0, 0.0))
        val l = kneeCap(sk, "shinL")
        val r = kneeCap(sk, "shinR")
        assertVec(l.patellaCenter, Vec3(-r.patellaCenter.x, r.patellaCenter.y, r.patellaCenter.z))
        assertEquals(l.angle, r.angle, 1e-12)
    }
}
