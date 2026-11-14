package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Mat4
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertVec
import com.yswy.dessin.core.mesh.MeshKind
import com.yswy.dessin.core.mesh.PartVisuals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val D2R = PI / 180

/** E08-03: 足のかかとと、曲げられるつま先 */
class FootTest {
    private val legs = listOf("flank", "pelvis", "hipL", "thighL", "shinL", "footL", "toeL", "hipR", "thighR", "shinR", "footR", "toeR")
        .fold(Skeleton.initial()) { s, id -> s.place(id) }

    /** 部位の空間での、本体(関節の玉でない)メッシュの頂点 */
    private fun bodyPoints(id: String): List<Vec3> = PartVisuals.meshes(id).withIndex()
        .filter { it.value.kind == MeshKind.BODY }
        .flatMap { (i, pm) ->
            val p = PartVisuals.placement(id, i, BodyParams())
            val m = Mat4.compose(p.position, p.rotation, p.scale)
            (0 until pm.mesh.vertexCount).map { m.transformPoint(pm.mesh.vertex(it)) }
        }

    @Test
    fun つま先は足の子_左右がある() {
        assertEquals("footL", PartSpecs["toeL"].parent)
        assertEquals("footR", PartSpecs["toeR"].parent)
        assertEquals("左つま先", PartSpecs["toeL"].label)
        assertEquals("右つま先", PartSpecs["toeR"].label)
    }

    @Test
    fun つま先の関節は足の前の付け根() {
        assertVec(Vec3(0.0, -0.045, 0.105), PartSpecs["toeL"].attach)
        assertVec(Vec3(0.0, -0.045, 0.105), PartSpecs["toeR"].attach)
    }

    @Test
    fun つま先は上へ60度_下へ30度曲がる() {
        for (id in listOf("toeL", "toeR")) {
            val hinge = PartSpecs[id].hinge!!
            val up = legs.withState(id, PartState(hinge.max, hinge.psi, 0.0))
            val down = legs.withState(id, PartState(hinge.min, hinge.psi, 0.0))
            fun dir(s: Skeleton) = (s.dotWorld(id) - s.guideCenter(id)).normalized()
            assertVec(Vec3(0.0, sin(60 * D2R), cos(60 * D2R)), dir(up))
            assertVec(Vec3(0.0, -sin(30 * D2R), cos(30 * D2R)), dir(down))
        }
    }

    @Test
    fun 足とつま先を合わせた長さは今と同じ() {
        val foot = bodyPoints("footL")
        val toe = bodyPoints("toeL").map { it + PartSpecs["toeL"].attach }
        assertEquals(-0.05, foot.minOf { it.z }, 1e-6)
        assertTrue("足の本体はつま先の関節まで", foot.maxOf { it.z } <= 0.105 + 1e-6)
        assertEquals(0.15, toe.maxOf { it.z }, 1e-6)
        // 足の裏とつま先の裏は同じ高さ
        assertEquals(foot.minOf { it.y }, toe.minOf { it.y }, 1e-6)
    }

    @Test
    fun かかとは丸い() {
        // 箱なら後ろの面は高さ 0.045 いっぱいに広がる。丸めたかかとは後ろの端で細くなる
        val back = bodyPoints("footL").filter { it.z < -0.045 }
        assertTrue(back.isNotEmpty())
        assertTrue(back.maxOf { it.y } - back.minOf { it.y } < 0.03)
    }

    @Test
    fun 足を曲げるとつま先もついてくる() {
        val s = legs.withState("footL", PartState(20 * D2R, 0.0, 0.0))
        val w = s.worldTransforms()
        assertVec(w.getValue("footL").toWorld(PartSpecs["toeL"].attach), w.getValue("toeL").position)
        assertEquals(0.0, w.getValue("toeL").position.distanceTo(s.guideCenter("toeL")), EPS)
    }
}
