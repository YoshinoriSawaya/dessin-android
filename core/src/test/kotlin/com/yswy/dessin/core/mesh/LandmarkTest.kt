package com.yswy.dessin.core.mesh

import com.yswy.dessin.core.editor.EditorState
import com.yswy.dessin.core.editor.SliderKind
import com.yswy.dessin.core.editor.applySlider
import com.yswy.dessin.core.editor.setSex
import com.yswy.dessin.core.editor.sexChoice
import com.yswy.dessin.core.editor.sliders
import com.yswy.dessin.core.math.Mat4
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.pose.BodyCodec
import com.yswy.dessin.core.render.DisplayOptions
import com.yswy.dessin.core.render.drawPlan
import com.yswy.dessin.core.skeleton.BodyParams
import com.yswy.dessin.core.skeleton.Sex
import com.yswy.dessin.core.skeleton.Skeleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** E08-04: 乳房と性器を体の形として付ける。大きさと性別は体型 */
class LandmarkTest {
    private val torso = Skeleton.initial().place("flank").place("pelvis")

    /** 描くメッシュのうち、目印の種類が feature のものの、部位の関節の空間での頂点 */
    private fun featurePoints(s: Skeleton, id: String, feature: BodyFeature): List<Vec3> =
        drawPlan(s, "rib", DisplayOptions()).meshes
            .filter { it.partId == id && PartVisuals.meshes(id)[it.meshIndex].feature == feature }
            .flatMap { d ->
                val p = PartVisuals.placement(id, d.meshIndex, s.body)
                val m = Mat4.compose(p.position, p.rotation, p.scale)
                val mesh = PartVisuals.meshes(id)[d.meshIndex].mesh
                (0 until mesh.vertexCount).map { m.transformPoint(mesh.vertex(it)) }
            }

    @Test
    fun 既定は乳房0_男() {
        assertEquals(0.0, BodyParams().breastSize, 0.0)
        assertEquals(Sex.MALE, BodyParams().sex)
    }

    @Test
    fun 乳房は大きさが0なら描かない_大きくすると胸の前に左右対称に膨らむ() {
        assertTrue(featurePoints(torso, "rib", BodyFeature.BREAST).isEmpty())
        val s = torso.withBody(BodyParams(breastSize = 0.6))
        val pts = featurePoints(s, "rib", BodyFeature.BREAST)
        assertTrue(pts.isNotEmpty())
        // 左右に分かれ、胸の前面より前に出る
        assertTrue(pts.any { it.x > 0.03 } && pts.any { it.x < -0.03 })
        assertEquals(pts.maxOf { it.x }, -pts.minOf { it.x }, 1e-6)
        val chestFront = PartVisuals.meshes("rib")[0].mesh.let { m -> (0 until m.vertexCount).maxOf { m.vertex(it).z } }
        assertTrue(pts.maxOf { it.z } > chestFront + 0.01)
        // 高さは胸の輪(y = 0.06)のあたり
        val midY = (pts.maxOf { it.y } + pts.minOf { it.y }) / 2
        assertEquals(0.06, midY, 0.02)
    }

    @Test
    fun 乳房は大きいほど前に出る() {
        fun front(size: Double) = featurePoints(torso.withBody(BodyParams(breastSize = size)), "rib", BodyFeature.BREAST).maxOf { it.z }
        assertTrue(front(1.0) > front(0.5) + 0.005)
    }

    @Test
    fun 性器は性別の形だけを骨盤の前下に描く() {
        val male = featurePoints(torso, "pelvis", BodyFeature.MALE)
        assertTrue(male.isNotEmpty())
        assertTrue(featurePoints(torso, "pelvis", BodyFeature.FEMALE).isEmpty())
        val f = torso.withBody(BodyParams(sex = Sex.FEMALE))
        val female = featurePoints(f, "pelvis", BodyFeature.FEMALE)
        assertTrue(female.isNotEmpty())
        assertTrue(featurePoints(f, "pelvis", BodyFeature.MALE).isEmpty())
        for (pts in listOf(male, female)) {
            // 骨盤の空間(下が −y、前が +z)で、前の下のほうの中央
            assertTrue(pts.minOf { it.z } > 0.09)
            assertTrue(pts.maxOf { it.y } < -0.07)
            assertEquals(0.0, (pts.maxOf { it.x } + pts.minOf { it.x }) / 2, 1e-6)
        }
    }

    @Test
    fun 胸を選ぶと乳房のスライダー() {
        val s = EditorState(skeleton = torso)
        assertTrue(s.sliders.any { it.kind == SliderKind.BREAST_SIZE && it.label == "乳房" && it.min == 0 && it.max == 100 })
        assertEquals(0.45, s.applySlider(SliderKind.BREAST_SIZE, 45).skeleton.body.breastSize, 1e-9)
    }

    @Test
    fun 骨盤を選んでいるときだけ性別を選べる() {
        val e = EditorState(skeleton = torso)
        assertNull(e.sexChoice)
        val pelvis = e.select("pelvis")
        assertEquals(Sex.MALE, pelvis.sexChoice)
        assertEquals(Sex.FEMALE, pelvis.setSex(Sex.FEMALE).skeleton.body.sex)
        assertEquals(e, e.setSex(Sex.FEMALE))
    }

    @Test
    fun 体型の文字列に入る() {
        val b = BodyParams(breastSize = 0.7, sex = Sex.FEMALE)
        assertEquals(b, BodyCodec.decodeLine(BodyCodec.encodeLine(b)))
    }
}
