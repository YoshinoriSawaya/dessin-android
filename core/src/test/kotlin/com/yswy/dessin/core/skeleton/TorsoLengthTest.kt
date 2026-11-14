package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.editor.EditorState
import com.yswy.dessin.core.editor.Slider
import com.yswy.dessin.core.editor.SliderKind
import com.yswy.dessin.core.editor.applySlider
import com.yswy.dessin.core.editor.sliders
import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertVec
import com.yswy.dessin.core.mesh.PartVisuals
import com.yswy.dessin.core.pose.BodyCodec
import org.junit.Assert.assertEquals
import org.junit.Test

/** E14-03: 胸・脇腹・骨盤の長さ */
class TorsoLengthTest {
    @Test
    fun 既定は100パーセントで_範囲は70から130() {
        val b = BodyParams()
        assertEquals(1.0, b.chestLength, EPS)
        assertEquals(1.0, b.flankLength, EPS)
        assertEquals(1.0, b.pelvisLength, EPS)
        val c = BodyParams(chestLength = 2.0, flankLength = 0.1, pelvisLength = 1.31).clamped()
        assertEquals(1.3, c.chestLength, EPS)
        assertEquals(0.7, c.flankLength, EPS)
        assertEquals(1.3, c.pelvisLength, EPS)
    }

    @Test
    fun 胸を伸ばすと首_肩_脇腹の付け根が縦にだけ動く() {
        val b = BodyParams(chestLength = 1.2)
        assertEquals(1.2, lengthScale(PartSpecs["rib"], b), EPS)
        assertVec(Vec3(0.0, 0.15 * 1.2, -0.035), attachOf(PartSpecs["neck"], b))
        assertVec(Vec3(0.025, 0.12 * 1.2, 0.0), attachOf(PartSpecs["clavL"], b))
        assertVec(Vec3(-0.025, 0.12 * 1.2, 0.0), attachOf(PartSpecs["clavR"], b))
        assertVec(Vec3(0.0, -0.045 * 1.2, -0.035), attachOf(PartSpecs["flank"], b))
    }

    @Test
    fun 脇腹を伸ばすと骨盤の付け根が下がる() {
        val b = BodyParams(flankLength = 1.2)
        assertVec(Vec3(0.0, -0.105 * 1.2, 0.0), attachOf(PartSpecs["pelvis"], b))
    }

    @Test
    fun 骨盤を伸ばすと股関節の付け根が下がる_前後と横は保つ() {
        val b = BodyParams(pelvisLength = 1.2)
        assertVec(Vec3(0.025, -0.11 * 1.2, 0.035), attachOf(PartSpecs["hipL"], b))
    }

    @Test
    fun 手足の長さの取り付け位置は今までどおり() {
        val b = BodyParams(upperArmLength = 1.2, thighLength = 0.8)
        assertVec(Vec3(0.0, -0.25 * 1.2, 0.0), attachOf(PartSpecs["forearmL"], b))
        assertVec(Vec3(0.0, -0.40 * 0.8, 0.0), attachOf(PartSpecs["shinL"], b))
    }

    @Test
    fun 胸の形は縦に伸び_乳房は位置だけが動く() {
        val b = BodyParams(chestLength = 1.2, breastSize = 1.0)
        val chest = PartVisuals.placement("rib", 0, b)
        assertVec(Vec3(1.0, 1.2, 1.0), chest.scale)
        val base = PartVisuals.placement("rib", 1, BodyParams(breastSize = 1.0))
        val breast = PartVisuals.placement("rib", 1, b)
        assertVec(Vec3(base.position.x, base.position.y * 1.2, base.position.z), breast.position)
        assertVec(base.scale, breast.scale)
    }

    @Test
    fun 骨盤の形は横幅と長さの両方で拡大し_性器は位置だけが動く() {
        val b = BodyParams(pelvisWidth = 1.1, pelvisLength = 1.2)
        val base = PartVisuals.placement("pelvis", 0, BodyParams())
        val p = PartVisuals.placement("pelvis", 0, b)
        assertVec(Vec3(1.1, 1.2, 1.0), p.scale)
        assertVec(Vec3(base.position.x, base.position.y * 1.2, base.position.z), p.position)
        val male0 = PartVisuals.placement("pelvis", 2, BodyParams())
        val male = PartVisuals.placement("pelvis", 2, b)
        assertVec(Vec3(male0.position.x, male0.position.y * 1.2, male0.position.z), male.position)
        assertVec(Vec3(1.0, 1.0, 1.0), male.scale)
    }

    @Test
    fun 脇腹の形は縦に伸びる() {
        val p = PartVisuals.placement("flank", 0, BodyParams(flankLength = 0.8))
        assertVec(Vec3(1.0, 0.8, 1.0), p.scale)
    }

    @Test
    fun 体型の文字列に残り_古い文字列は100パーセント() {
        val b = BodyParams(chestLength = 1.1, flankLength = 0.9, pelvisLength = 1.25)
        assertEquals(b, BodyCodec.decodeLine(BodyCodec.encodeLine(b)))
        assertEquals(BodyParams(headSize = 1.2), BodyCodec.decodeLine("headSize=1.2"))
    }

    @Test
    fun スライダーは選んだ部位で出す() {
        val length = { v: Int -> Slider(SliderKind.TORSO_LENGTH, "長さ", 70, 130, v, "%") }
        val s0 = EditorState()
        assertEquals(length(100), s0.sliders.last())
        val chest = s0.applySlider(SliderKind.TORSO_LENGTH, 115)
        assertEquals(1.15, chest.skeleton.body.chestLength, EPS)
        val flank = chest.select("flank").applySlider(SliderKind.TORSO_LENGTH, 90)
        assertEquals(length(90), flank.sliders.last())
        assertEquals(0.9, flank.skeleton.body.flankLength, EPS)
        val pelvis = flank.select("pelvis").applySlider(SliderKind.TORSO_LENGTH, 120)
        assertEquals(length(120), pelvis.sliders.last())
        assertEquals(1.2, pelvis.skeleton.body.pelvisLength, EPS)
        // ほかの長さは変わらない
        assertEquals(1.15, pelvis.skeleton.body.chestLength, EPS)
        assertEquals(0.9, pelvis.skeleton.body.flankLength, EPS)
    }
}
