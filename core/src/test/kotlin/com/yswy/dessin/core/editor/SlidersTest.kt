package com.yswy.dessin.core.editor

import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertSameRotation
import com.yswy.dessin.core.skeleton.PartState
import com.yswy.dessin.core.skeleton.RootTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

private const val D2R = PI / 180

/** 期待値は docs/web-reference.html の syncUI・onSlider から */
class SlidersTest {
    private val initial = EditorState()

    @Test
    fun 肋骨は大きさと3つの傾き() {
        assertEquals(
            listOf(
                Slider(SliderKind.RIB_SIZE, "大きさ", 30, 160, 100, "%"),
                Slider(SliderKind.RIB_TILT, "前後の傾き", -90, 90, 0, "°"),
                Slider(SliderKind.RIB_TWIST, "ひねり", -180, 180, 0, "°"),
                Slider(SliderKind.RIB_SIDE_TILT, "横の傾き", -90, 90, 0, "°"),
                // E07-02: 体型の身長
                Slider(SliderKind.HEIGHT, "身長", 70, 130, 100, "%"),
            ),
            initial.sliders,
        )
    }

    @Test
    fun 肋骨の値はオイラー角を度に丸めて出す() {
        val rot = Quat.fromEulerYXZ(Vec3(20.4 * D2R, -35.6 * D2R, 10.0 * D2R))
        val s = initial.copy(skeleton = initial.skeleton.withRoot(RootTransform(scale = 1.234, rotation = rot)))
        assertEquals(listOf(123, 20, -36, 10, 100), s.sliders.map { it.value }) // 末尾は身長(E07-02)
    }

    @Test
    fun 肋骨の傾きを1つ変えても他の軸は保つ() {
        val s0 = initial.copy(skeleton = initial.skeleton.withRoot(RootTransform(rotation = Quat.fromEulerYXZ(Vec3(0.2, 0.5, -0.1)))))
        val s = s0.applySlider(SliderKind.RIB_TWIST, 40)
        assertSameRotation(Quat.fromEulerYXZ(Vec3(0.2, 40 * D2R, -0.1)), s.skeleton.root.rotation)
        assertEquals(1.37, s0.applySlider(SliderKind.RIB_SIZE, 137).skeleton.root.scale, EPS)
    }

    @Test
    fun 部位はひねり_範囲が0なら使えない() {
        val neck = initial.select("neck")
        // E07-02 で首に長さと付け根の前後が加わったので、先頭のひねりだけを見る
        assertEquals(Slider(SliderKind.ROLL, "ひねり", -30, 30, 0, "°"), neck.sliders[0])
        assertTrue(neck.sliders[0].enabled)
        val clav = initial.select("clavL").sliders
        assertEquals(Slider(SliderKind.ROLL, "ひねり", 0, 0, 0, "°"), clav[0])
        assertFalse(clav[0].enabled)
    }

    @Test
    fun ひねりの値を変える() {
        val s = initial.select("neck").applySlider(SliderKind.ROLL, -20)
        assertEquals(-20 * D2R, s.skeleton.state("neck").roll, EPS)
        assertEquals(-20, s.sliders[0].value)
    }

    @Test
    fun 頭は大きさ_肩は肩幅_骨盤は横幅と付け根の内寄り() {
        val head = initial.select("neck").select("head")
        assertEquals(Slider(SliderKind.HEAD_SIZE, "大きさ", 60, 140, 100, "%"), head.sliders[1])
        val clav = initial.select("clavR")
        assertEquals(Slider(SliderKind.SHOULDER_WIDTH, "肩幅(左右)", 40, 150, 100, "%"), clav.sliders[1])
        val pelvis = initial.select("flank").select("pelvis")
        assertEquals(
            listOf(
                Slider(SliderKind.ROLL, "ひねり", -45, 45, 0, "°"),
                Slider(SliderKind.PELVIS_WIDTH, "骨盤の横幅", 60, 150, 100, "%"),
                Slider(SliderKind.HIP_INSET, "付け根の内寄り", 0, 80, 41, "mm"),
            ),
            pelvis.sliders,
        )
    }

    @Test
    fun 体型のスライダーを変える() {
        val s = initial.select("flank").select("pelvis")
            .applySlider(SliderKind.PELVIS_WIDTH, 120)
            .applySlider(SliderKind.HIP_INSET, 25)
            .select("clavL").applySlider(SliderKind.SHOULDER_WIDTH, 130)
            .select("neck").select("head").applySlider(SliderKind.HEAD_SIZE, 85)
        assertEquals(1.2, s.skeleton.body.pelvisWidth, EPS)
        assertEquals(0.025, s.skeleton.body.hipInset, EPS)
        assertEquals(1.3, s.skeleton.body.shoulderWidth, EPS)
        assertEquals(0.85, s.skeleton.body.headSize, EPS)
    }

    @Test
    fun 右側のひねりは鏡像の範囲() {
        // Web版のロールはすべて対称なので、範囲は左右同じ。値は状態の roll をそのまま度にする
        val r = initial.select("clavR").select("upperArmR")
        val s = r.copy(skeleton = r.skeleton.withState("upperArmR", PartState(0.0, 0.0, 0.5)))
        assertEquals(Slider(SliderKind.ROLL, "ひねり", -90, 90, 29, "°"), s.sliders[0])
    }

    @Test
    fun 選択中の部位に無いスライダーは何もしない() {
        val neck = initial.select("neck")
        assertEquals(neck, neck.applySlider(SliderKind.HEAD_SIZE, 120))
        assertEquals(initial, initial.applySlider(SliderKind.ROLL, 10))
    }

    // ---------- E07-02: 体型の項目 ----------

    @Test
    fun 首は長さと付け根の前後() {
        assertEquals(
            listOf(
                Slider(SliderKind.ROLL, "ひねり", -30, 30, 0, "°"),
                Slider(SliderKind.NECK_LENGTH, "長さ", 50, 200, 100, "%"),
                Slider(SliderKind.NECK_OFFSET, "付け根の前後", -70, 20, -35, "mm"),
            ),
            initial.select("neck").sliders,
        )
    }

    @Test
    fun 上腕_前腕_もも_すねは長さ_すねはひざの転がりも() {
        val arm = initial.select("clavL").select("upperArmL")
        assertEquals(Slider(SliderKind.LIMB_LENGTH, "長さ(左右)", 70, 130, 100, "%"), arm.sliders[1])
        assertEquals(Slider(SliderKind.LIMB_LENGTH, "長さ(左右)", 70, 130, 100, "%"), arm.select("forearmL").sliders[1])
        val leg = initial.select("flank").select("pelvis").select("hipR").select("thighR")
        assertEquals(Slider(SliderKind.LIMB_LENGTH, "長さ(左右)", 70, 130, 100, "%"), leg.sliders[1])
        assertEquals(
            listOf(
                Slider(SliderKind.ROLL, "ひねり", 0, 0, 0, "°"),
                Slider(SliderKind.LIMB_LENGTH, "長さ(左右)", 70, 130, 100, "%"),
                Slider(SliderKind.KNEE_ROLL, "ひざの転がり", 0, 60, 30, "mm"),
            ),
            leg.select("shinR").sliders,
        )
        // 手と足には長さが無い
        assertEquals(1, arm.select("forearmL").select("handL").sliders.size)
    }

    @Test
    fun 体型の項目を変える() {
        val s = initial.applySlider(SliderKind.HEIGHT, 110)
            .select("neck").applySlider(SliderKind.NECK_LENGTH, 150).applySlider(SliderKind.NECK_OFFSET, -50)
            .select("clavL").select("upperArmL").applySlider(SliderKind.LIMB_LENGTH, 120)
            .select("forearmL").applySlider(SliderKind.LIMB_LENGTH, 90)
            .select("flank").select("pelvis").select("hipL").select("thighL").applySlider(SliderKind.LIMB_LENGTH, 110)
            .select("shinL").applySlider(SliderKind.LIMB_LENGTH, 80).applySlider(SliderKind.KNEE_ROLL, 45)
        val b = s.skeleton.body
        assertEquals(1.1, b.height, EPS)
        assertEquals(1.5, b.neckLength, EPS)
        assertEquals(-0.05, b.neckOffset, EPS)
        assertEquals(1.2, b.upperArmLength, EPS)
        assertEquals(0.9, b.forearmLength, EPS)
        assertEquals(1.1, b.thighLength, EPS)
        assertEquals(0.8, b.shinLength, EPS)
        assertEquals(0.045, b.kneeRoll, EPS)
        // 胸の大きさ(ポーズ)は変わらない
        assertEquals(1.0, s.skeleton.root.scale, EPS)
    }
}
