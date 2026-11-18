package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.editor.EditorState
import com.yswy.dessin.core.editor.Slider
import com.yswy.dessin.core.editor.SliderKind
import com.yswy.dessin.core.editor.applySlider
import com.yswy.dessin.core.editor.mirrorSelected
import com.yswy.dessin.core.editor.sliders
import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertVec
import com.yswy.dessin.core.mesh.PartVisuals
import com.yswy.dessin.core.mesh.meshWorldMatrix
import com.yswy.dessin.core.pose.PoseCodec
import com.yswy.dessin.core.pose.PoseDecodeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

private const val D2R = PI / 180

/** E18-01: 手に持たせる棒 */
class StickTest {
    private val arms = listOf("clavL", "upperArmL", "forearmL", "handL")
    private val withHand = arms.fold(EditorState()) { s, id -> s.select(id) }
    private val withStick = withHand.select("stickL")

    @Test
    fun 棒は手の子_前向き_45度とひねり90度() {
        assertEquals(25, PartSpecs.all.size)
        val l = PartSpecs["stickL"]
        assertEquals("handL", l.parent)
        assertEquals("左棒", l.label)
        assertVec(Vec3.Z, l.restDir)
        assertEquals(Cone(45 * D2R, 45 * D2R, 45 * D2R, 45 * D2R), l.cone)
        assertEquals(RollRange(-90 * D2R, 90 * D2R), l.roll)
        val r = PartSpecs["stickR"]
        assertEquals("handR", r.parent)
        assertEquals(-l.attach.x, r.attach.x, 0.0)
        assertEquals(RollRange(-90 * D2R, 90 * D2R), r.roll)
    }

    @Test
    fun 棒の形の既定と範囲() {
        assertEquals(StickShape(0.6, 0.5), StickShape.DEFAULT)
        val sk = withStick.skeleton
        assertEquals(StickShape.DEFAULT, sk.stickShape("stickL"))
        // 既定と同じなら持たない(保存の前後の比較を揃えるため)
        assertTrue(sk.sticks.isEmpty())
        val changed = sk.withStickShape("stickL", StickShape(5.0, -1.0))
        assertEquals(StickShape(StickShape.LENGTH_RANGE.endInclusive, 0.0), changed.stickShape("stickL"))
        // 外すと戻る
        assertEquals(StickShape.DEFAULT, changed.remove("handL").stickShape("stickL"))
    }

    @Test
    fun 置くと手がグーになる_パーに戻したらそのまま() {
        assertEquals(HandShape.OPEN, withHand.skeleton.handShape("handL"))
        assertEquals(HandShape.FIST, withStick.skeleton.handShape("handL"))
        val open = withStick.copy(skeleton = withStick.skeleton.withHandShape("handL", HandShape.OPEN))
        assertEquals(HandShape.OPEN, open.select("stickL").skeleton.handShape("handL"))
    }

    @Test
    fun 棒は握りの中心から前へ_握る位置で両端が決まる() {
        val sk = withStick.skeleton.withStickShape("stickL", StickShape(0.8, 0.25))
        val hand = sk.worldTransforms().getValue("handL")
        val stick = sk.worldTransforms().getValue("stickL")
        assertVec(hand.toWorld(PartSpecs["stickL"].attach), stick.position)
        val dir = stick.rotation.rotate(Vec3.Z)
        // 休止状態では手の前(+z)
        assertVec(hand.rotation.rotate(Vec3.Z), dir)
        // 板の本体(最初のメッシュ)は長さ1の箱を伸ばして置く。後ろの端は握りから 0.8 × 25%、前の端は 75%
        val m = meshWorldMatrix(sk, "stickL", 0)
        assertVec(stick.position - dir * (0.8 * 0.25), m.transformPoint(Vec3(0.0, 0.0, -0.5)))
        assertVec(stick.position + dir * (0.8 * 0.75), m.transformPoint(Vec3(0.0, 0.0, 0.5)))
    }

    @Test
    fun 板は幅が手の長さの向き_厚みはそれより薄い() {
        val box = PartVisuals.meshes("stickL")[0].mesh
        val (lo, hi) = box.bounds()!!
        val w = hi.y - lo.y
        val t = hi.x - lo.x
        assertEquals(0.03, w, 1e-6)
        assertTrue(t < w)
        assertEquals(1.0, hi.z - lo.z, 1e-6)
    }

    @Test
    fun スライダーはひねり_長さ_握る位置() {
        assertEquals(
            listOf(
                Slider(SliderKind.ROLL, "ひねり", -90, 90, 0, "°"),
                Slider(SliderKind.STICK_LENGTH, "長さ", 100, 2000, 600, "mm"),
                Slider(SliderKind.STICK_GRIP, "握る位置", 0, 100, 50, "%"),
            ),
            withStick.sliders,
        )
        val s = withStick.applySlider(SliderKind.STICK_LENGTH, 1200).applySlider(SliderKind.STICK_GRIP, 10)
        assertEquals(StickShape(1.2, 0.1), s.skeleton.stickShape("stickL"))
    }

    @Test
    fun 保存して読み込むと形も戻る() {
        val sk = withStick.skeleton.withStickShape("stickL", StickShape(1.1, 0.2))
            .withState("stickL", PartState(30 * D2R, 1.0, 40 * D2R))
        val text = PoseCodec.encode(sk)
        assertTrue(text.startsWith("dessin-pose 4"))
        val back = (PoseCodec.decode(text) as PoseDecodeResult.Ok).skeleton
        assertEquals(sk.stickShape("stickL"), back.stickShape("stickL"))
        assertEquals(sk.state("stickL"), back.state("stickL"))
        assertTrue(back.isPlaced("stickL"))
        // 版3(棒の無い形式)も読める
        val v3 = text.lines().filterNot { it.startsWith("stick ") || it.startsWith("part stick") }
            .joinToString("\n").replace("dessin-pose 4", "dessin-pose 3")
        val old = (PoseCodec.decode(v3) as PoseDecodeResult.Ok).skeleton
        assertFalse(old.isPlaced("stickL"))
    }

    @Test
    fun 左右のコピーで形も写る() {
        val s = withStick.copy(skeleton = withStick.skeleton.withStickShape("stickL", StickShape(1.5, 0.0)))
            .select("handL").mirrorSelected()
        assertTrue(s.skeleton.isPlaced("stickR"))
        assertEquals(StickShape(1.5, 0.0), s.skeleton.stickShape("stickR"))
        assertEquals(HandShape.FIST, s.skeleton.handShape("handR"))
    }

    @Test
    fun 部位チップに棒がある() {
        val ids = withHand.partChips.flatten().map { it.id }
        assertTrue("stickL" in ids && "stickR" in ids)
    }
}
