package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.editor.EditorState
import com.yswy.dessin.core.editor.handShapeChoice
import com.yswy.dessin.core.editor.setHandShape
import com.yswy.dessin.core.math.Mat4
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.mesh.PartVisuals
import com.yswy.dessin.core.pose.PoseCodec
import com.yswy.dessin.core.pose.PoseDecodeResult
import com.yswy.dessin.core.render.DisplayOptions
import com.yswy.dessin.core.render.drawPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** E08-02: 手の形(グーとパー)と親指 */
class HandShapeTest {
    private val arms = listOf("clavL", "upperArmL", "forearmL", "handL", "clavR", "upperArmR", "forearmR", "handR")
        .fold(Skeleton.initial()) { s, id -> s.place(id) }

    /** 描く手のメッシュの、手の関節の空間での頂点 */
    private fun handPoints(s: Skeleton, id: String): List<Vec3> =
        drawPlan(s, "rib", DisplayOptions(markers = false)).meshes.filter { it.partId == id }.flatMap { d ->
            val p = PartVisuals.placement(id, d.meshIndex, s.body)
            val m = Mat4.compose(p.position, p.rotation, p.scale)
            val mesh = PartVisuals.meshes(id)[d.meshIndex].mesh
            (0 until mesh.vertexCount).map { m.transformPoint(mesh.vertex(it)) }
        }

    @Test
    fun 既定はパー_左右別々に変えられる() {
        assertEquals(HandShape.OPEN, arms.handShape("handL"))
        val s = arms.withHandShape("handL", HandShape.FIST)
        assertEquals(HandShape.FIST, s.handShape("handL"))
        assertEquals(HandShape.OPEN, s.handShape("handR"))
    }

    @Test
    fun 手を外すとパーに戻る() {
        val s = arms.withHandShape("handL", HandShape.FIST)
        assertEquals(HandShape.OPEN, s.remove("forearmL").handShape("handL"))
        assertEquals(HandShape.FIST, s.remove("forearmR").handShape("handL"))
    }

    @Test
    fun 形ごとに描くメッシュが変わる() {
        val open = drawPlan(arms, "rib", DisplayOptions()).meshes.filter { it.partId == "handL" }.map { it.meshIndex }.toSet()
        val fist = drawPlan(arms.withHandShape("handL", HandShape.FIST), "rib", DisplayOptions()).meshes.filter { it.partId == "handL" }.map { it.meshIndex }.toSet()
        assertTrue(open.isNotEmpty() && fist.isNotEmpty())
        // 関節の玉は両方にあり、ほかは重ならない
        val marker = PartVisuals.meshes("handL").indexOfFirst { it.kind == com.yswy.dessin.core.mesh.MeshKind.MARKER }
        assertEquals(setOf(marker), open intersect fist)
    }

    @Test
    fun パーは指が開いて長く_グーは短いブロック() {
        val open = handPoints(arms, "handL")
        val fist = handPoints(arms.withHandShape("handL", HandShape.FIST), "handL")
        val openLength = -open.minOf { it.y }
        val fistLength = -fist.minOf { it.y }
        assertTrue("パーは手のひらより長い: $openLength", openLength > 0.13)
        assertTrue("グーは短い: $fistLength", fistLength < 0.10)
        // パーは指が扇状に開くので、手のひら(幅 0.062)より幅が広い
        val openWidth = open.maxOf { it.z } - open.minOf { it.z }
        assertTrue("パーの幅: $openWidth", openWidth > 0.08)
    }

    @Test
    fun 親指は左右とも前側に出る() {
        for (shape in HandShape.entries) {
            val s = arms.withHandShape("handL", shape).withHandShape("handR", shape)
            for (id in listOf("handL", "handR")) {
                val pts = handPoints(s, id)
                val front = pts.maxOf { it.z }
                val back = -pts.minOf { it.z }
                assertTrue("$shape $id: 前 $front 後ろ $back", front > back + 0.01)
            }
        }
    }

    @Test
    fun 左右は鏡像() {
        val l = handPoints(arms.withHandShape("handL", HandShape.FIST), "handL")
        val r = handPoints(arms.withHandShape("handR", HandShape.FIST), "handR")
        assertEquals(l.maxOf { it.x }, -r.minOf { it.x }, 1e-6)
        assertEquals(l.minOf { abs(it.y) }, r.minOf { abs(it.y) }, 1e-6)
    }

    @Test
    fun ポーズに保存して戻る() {
        val s = arms.withHandShape("handR", HandShape.FIST)
        val back = (PoseCodec.decode(PoseCodec.encode(s)) as PoseDecodeResult.Ok).skeleton
        assertEquals(s, back)
        assertTrue(PoseCodec.encode(s).lines().contains("hand handR fist"))
        assertTrue(PoseCodec.decode(PoseCodec.encode(s).replace("hand handR fist", "hand handR rock")) is PoseDecodeResult.Error)
    }

    @Test
    fun 手を選んでいるときだけ形を選べる() {
        val e = EditorState(skeleton = arms)
        assertNull(e.handShapeChoice)
        val hand = e.select("handR")
        assertEquals(HandShape.OPEN, hand.handShapeChoice)
        val fist = hand.setHandShape(HandShape.FIST)
        assertEquals(HandShape.FIST, fist.handShapeChoice)
        assertEquals(HandShape.OPEN, fist.skeleton.handShape("handL"))
        // 手以外を選んでいれば何もしない
        assertEquals(e, e.setHandShape(HandShape.FIST))
    }
}
