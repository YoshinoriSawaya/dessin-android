package com.yswy.dessin.core.pick

import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Ray
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertVec
import com.yswy.dessin.core.render.DisplayOptions
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.RootTransform
import com.yswy.dessin.core.skeleton.Skeleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 期待値は three.js r128 の Ray.intersectTriangle と、Web版の pickPart・romPoint・pointerdown から */
class PickingTest {
    private val a = Vec3(-1.0, -1.0, 0.0)
    private val b = Vec3(1.0, -1.0, 0.0)
    private val c = Vec3(0.0, 1.0, 0.0)
    private val towardMinusZ = Ray(Vec3(0.0, 0.0, 5.0), Vec3(0.0, 0.0, -1.0))

    @Test
    fun 三角形_表から当たる() {
        // a→b→c は +z から見て反時計回り(表)
        assertVec(Vec3.ZERO, intersectTriangle(towardMinusZ, a, b, c, backfaceCulling = true)!!)
    }

    @Test
    fun 三角形_裏からは片面なら当たらない_両面なら当たる() {
        val fromBehind = Ray(Vec3(0.0, 0.0, -5.0), Vec3(0.0, 0.0, 1.0))
        assertNull(intersectTriangle(fromBehind, a, b, c, backfaceCulling = true))
        assertVec(Vec3.ZERO, intersectTriangle(fromBehind, a, b, c, backfaceCulling = false)!!)
    }

    @Test
    fun 三角形_外れる_後ろにある_平行() {
        assertNull(intersectTriangle(Ray(Vec3(3.0, 0.0, 5.0), Vec3(0.0, 0.0, -1.0)), a, b, c, true))
        assertNull(intersectTriangle(Ray(Vec3(0.0, 0.0, -5.0), Vec3(0.0, 0.0, -1.0)), a, b, c, false))
        assertNull(intersectTriangle(Ray(Vec3(0.0, 0.0, 5.0), Vec3(1.0, 0.0, 0.0)), a, b, c, false))
    }

    /** 関節の位置に向けたレイ(正面から) */
    private fun rayTo(p: Vec3) = Ray(p + Vec3(0.0, 0.0, 3.0), Vec3(0.0, 0.0, -1.0))

    @Test
    fun 部位_手前の部位を選ぶ() {
        val s = Skeleton.initial()
        // 肋骨の中心を正面から
        assertEquals("rib", pickPart(s, "rib", DisplayOptions(), rayTo(s.guideCenter("rib"))))
        // 何も無いところ
        assertNull(pickPart(s, "rib", DisplayOptions(), Ray(Vec3(5.0, 5.0, 5.0), Vec3(0.0, 0.0, -1.0))))
    }

    @Test
    fun 部位_ゴーストも選べる_ガイドを消すと選べない() {
        val s = Skeleton.initial()
        val neckMid = s.guideCenter("neck") + Vec3(0.0, 0.035, 0.0)
        assertEquals("neck", pickPart(s, "rib", DisplayOptions(), rayTo(neckMid)))
        assertNull(pickPart(s, "rib", DisplayOptions(guides = false), rayTo(neckMid)))
    }

    @Test
    fun 部位_関節の玉を消すと玉では選べない() {
        val s = PartSpecs.all.fold(Skeleton.initial()) { acc, p -> acc.place(p.id) }
        // 肘の関節の玉(半径 0.036)を外から。上腕(0.032)と前腕(0.028)の円柱より外側を通る
        val elbow = s.guideCenter("forearmL")
        val ray = Ray(elbow + Vec3(3.0, 0.0, 0.034), Vec3(-1.0, 0.0, 0.0))
        assertEquals("forearmL", pickPart(s, "rib", DisplayOptions(), ray))
        // 玉を消すと前腕には当たらず、奥の胴に当たる
        assertEquals("rib", pickPart(s, "rib", DisplayOptions(markers = false), ray))
    }

    @Test
    fun 玉は他の部位より優先する() {
        // 左上腕を選ぶ。玉は上腕の先(真下)にあり、手前に何も無くても後ろに前腕があっても玉が勝つ
        val s = Skeleton.initial().place("clavL").place("upperArmL").place("forearmL")
        val dot = s.dotWorld("upperArmL")
        assertEquals("upperArmL", pickPart(s, "upperArmL", DisplayOptions(), rayTo(dot)))
        assertNotNull(pickDot(s, "upperArmL", DisplayOptions(), rayTo(dot)))
        // 選んでいない部位の玉は無い
        assertNull(pickDot(s, "rib", DisplayOptions(), rayTo(dot)))
    }

    @Test
    fun 玉の大きさは視点からの距離で変わる() {
        val s = Skeleton.initial().place("clavL").place("upperArmL")
        val dot = s.dotWorld("upperArmL")
        // 遠くから見ると玉は大きくなるので、少し外したレイでも当たる
        val off = Vec3(0.012, 0.0, 0.0)
        assertNull(pickDot(s, "upperArmL", DisplayOptions(), Ray(dot + off + Vec3(0.0, 0.0, 1.0), Vec3(0.0, 0.0, -1.0))))
        assertNotNull(pickDot(s, "upperArmL", DisplayOptions(), Ray(dot + off + Vec3(0.0, 0.0, 5.0), Vec3(0.0, 0.0, -1.0))))
    }

    @Test
    fun 緑の範囲_選んだ部位の可動域に当たった点() {
        val s = Skeleton.initial().place("neck")
        val c = s.guideCenter("neck")
        // 首の可動域は関節から上へ半径 0.2 の面。真上から見下ろして中心近くに当てる
        val down = Ray(c + Vec3(0.0, 3.0, 0.0), Vec3(0.0, -1.0, 0.0))
        val p = romPoint(s, "neck", DisplayOptions(), down)!!
        assertEquals(0.0, p.x - c.x, 1e-3)
        assertEquals(0.2, p.y - c.y, 1e-3)
        // 選んでいなければ、ガイドを消していれば null
        assertNull(romPoint(s, "rib", DisplayOptions(), down))
        assertNull(romPoint(s, "neck", DisplayOptions(guides = false), down))
    }

    @Test
    fun 押した先_緑の範囲に当たったら親は選ばない() {
        // 照合データ(pick-golden.txt の「全部位」)の1件: 前腕を選んでいて、前腕の可動域(扇)を通って親の上腕に当たる
        var s = PartSpecs.all.fold(Skeleton.initial()) { acc, p -> acc.place(p.id) }
        s = s.aim("upperArmL", Vec3(0.6, -0.48, 0.64))
        s = s.aim("thighR", Vec3(0.0, -0.6, 0.8))
        s = s.withRoot(RootTransform(Vec3(0.1, 1.05, 0.0), Quat.fromEulerYXZ(Vec3(0.2, 0.5, -0.1)), 1.1))
        val ray = Ray(Vec3(0.0, 1.3112517389511904, 2.581302453220052), Vec3(0.17013231143811647, -0.11430285330088809, -0.9787695614045215))
        assertEquals("upperArmL", pickPart(s, "forearmL", DisplayOptions(), ray))
        val press = pickAtPress(s, "forearmL", DisplayOptions(), ray)
        assertNull(press.part)
        assertNotNull(press.romPoint)
        // 親でない部位(肋骨)は、規則に関係なくそのまま選べる
        val ribRay = Ray(s.guideCenter("rib") + Vec3(0.0, 0.0, 3.0), Vec3(0.0, 0.0, -1.0))
        assertEquals(pickPart(s, "forearmL", DisplayOptions(), ribRay), pickAtPress(s, "forearmL", DisplayOptions(), ribRay).part)
    }
}
