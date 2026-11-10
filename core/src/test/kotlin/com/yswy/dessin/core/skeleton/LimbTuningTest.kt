package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Mat4
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertVec
import com.yswy.dessin.core.mesh.PartVisuals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

private const val D2R = PI / 180

/** E06-01: もものひねりと手首 */
class ThighHandTest {
    @Test
    fun もものひねりは90度まで() {
        listOf("thighL", "thighR").forEach { assertEquals(RollRange(-90 * D2R, 90 * D2R), PartSpecs[it].roll) }
    }

    @Test
    fun 手のひねりは90度まで() {
        listOf("handL", "handR").forEach { assertEquals(RollRange(-90 * D2R, 90 * D2R), PartSpecs[it].roll) }
    }

    @Test
    fun 手首は手の平と甲の側に60度_親指と小指の側に25度() {
        listOf("handL", "handR").forEach { assertEquals(Cone(25 * D2R, 25 * D2R, 60 * D2R, 60 * D2R), PartSpecs[it].cone) }
    }

    @Test
    fun 手の平たい面は体側を向く() {
        // 手の箱(幅 0.062・厚さ 0.024)を縦軸まわりに 90 度回すので、x の広がりが厚さ、z の広がりが幅
        listOf("handL", "handR").forEach { id ->
            val p = PartVisuals.placement(id, 0, BodyParams())
            val mesh = PartVisuals.meshes(id)[0].mesh
            val m = Mat4.compose(p.position, p.rotation, p.scale)
            val pts = (0 until mesh.vertexCount).map { m.transformPoint(mesh.vertex(it)) }
            assertEquals(id, 0.024, pts.maxOf { it.x } - pts.minOf { it.x }, 1e-6)
            assertEquals(id, 0.062, pts.maxOf { it.z } - pts.minOf { it.z }, 1e-6)
        }
    }
}

/** E06-02: ひざは大腿骨の丸み(顆)に沿って転がるように曲がる */
class KneeRollTest {
    private val legs = Skeleton.initial().place("flank").place("pelvis").place("hipL").place("thighL").place("shinL")

    @Test
    fun すねの回転の中心は関節の003上() {
        assertVec(Vec3(0.0, 0.03, 0.0), PartSpecs["shinL"].pivot)
        assertVec(Vec3(0.0, 0.03, 0.0), PartSpecs["shinR"].pivot)
        // ほかの部位は関節で回る
        PartSpecs.all.filterNot { it.id.startsWith("shin") }.forEach { assertVec(Vec3.ZERO, it.pivot) }
    }

    @Test
    fun 伸ばしていれば関節の位置は変わらない() {
        val thigh = legs.worldTransforms().getValue("thighL")
        assertVec(thigh.toWorld(PartSpecs["shinL"].attach), legs.guideCenter("shinL"))
    }

    @Test
    fun 曲げるとすねの付け根が後ろ上へ移る() {
        val thigh = legs.worldTransforms().getValue("thighL")
        val bent = legs.withState("shinL", PartState(PI / 2, 0.0, 0.0))
        // 90 度曲げると、もとの関節から後ろ(−z)へ 0.03、上へ 0.03
        assertVec(thigh.toWorld(PartSpecs["shinL"].attach + Vec3(0.0, 0.03, -0.03)), bent.guideCenter("shinL"))
    }

    @Test
    fun 足もすねと一緒に動き_玉は新しい関節から向きの方向にある() {
        val bent = legs.place("footL").withState("shinL", PartState(PI / 2, 0.0, 0.0))
        val w = bent.worldTransforms()
        val shin = w.getValue("shinL")
        assertVec(shin.toWorld(PartSpecs["footL"].attach), w.getValue("footL").position)
        // すねは後ろ(−z)を向く。玉は関節から向きの方向へ可動域の半径
        val dir = (bent.dotWorld("shinL") - bent.guideCenter("shinL")).normalized()
        assertVec(Vec3(0.0, 0.0, -1.0), dir)
        assertEquals(0.38, bent.dotWorld("shinL").distanceTo(bent.guideCenter("shinL")), EPS)
    }
}
