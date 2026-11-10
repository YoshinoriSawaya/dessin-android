package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Ray
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertSameRotation
import com.yswy.dessin.core.math.assertVec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/** 期待値は docs/web-reference.html の available・select・removePart・resetPart・dragPart・aimPart・sphereRotate から */
class SkeletonTest {
    private val initial = Skeleton.initial()
    private val ribPos = Vec3(0.0, 1.10, 0.0)
    private val turned = initial.withRoot(initial.root.copy(rotation = Quat.fromAxisAngle(Vec3.Y, PI / 2)))

    private fun assertState(expected: PartState, actual: PartState) {
        assertEquals("phi", expected.phi, actual.phi, EPS)
        assertEquals("psi", expected.psi, actual.psi, EPS)
        assertEquals("roll", expected.roll, actual.roll, EPS)
    }

    private fun Skeleton.placeAll(vararg ids: String) = ids.fold(this) { s, id -> s.place(id) }

    // ---------- 配置 ----------

    @Test
    fun 初期状態は肋骨だけが置かれている() {
        assertEquals(setOf("rib"), initial.placed)
        assertVec(ribPos, initial.root.position)
        assertSameRotation(Quat.IDENTITY, initial.root.rotation)
        assertEquals(1.0, initial.root.scale, EPS)
        assertEquals(BodyParams(), initial.body)
        PartSpecs.all.filterNot { it.isRoot }.forEach { assertEquals(PartState.rest(it), initial.state(it.id)) }
    }

    @Test
    fun 親が置かれた部位だけが置ける() {
        assertEquals(
            setOf("rib", "neck", "flank", "clavL", "clavR"),
            PartSpecs.all.filter { initial.isAvailable(it.id) }.map { it.id }.toSet(),
        )
        assertTrue(initial.place("neck").isAvailable("head"))
    }

    @Test
    fun 置けない部位を置こうとしても変わらない() {
        assertSame(initial, initial.place("head"))
    }

    @Test
    fun 置くと休止状態から始まる() {
        val s = initial.withState("neck", PartState(0.4, 1.0, 0.1)).place("neck")
        assertTrue(s.isPlaced("neck"))
        assertEquals(PartState.rest(PartSpecs["neck"]), s.state("neck"))
    }

    @Test
    fun 置いてある部位を置き直しても状態は保つ() {
        val s = initial.place("neck").withState("neck", PartState(0.4, 1.0, 0.1))
        assertEquals(PartState(0.4, 1.0, 0.1), s.place("neck").state("neck"))
    }

    @Test
    fun 外すと子孫も外れて休止状態に戻る() {
        val s = initial.placeAll("clavL", "upperArmL", "forearmL")
            .withState("upperArmL", PartState(1.0, 2.0, 0.3))
            .withState("forearmL", PartState(0.5, PI, 0.0))
            .remove("clavL")
        assertEquals(setOf("rib"), s.placed)
        assertEquals(PartState.rest(PartSpecs["upperArmL"]), s.state("upperArmL"))
        assertEquals(PartState.rest(PartSpecs["forearmL"]), s.state("forearmL"))
    }

    @Test
    fun 頭を外すと頭の大きさが戻る_肩幅は戻らない() {
        val s = initial.placeAll("neck", "head", "clavL")
            .withBody(BodyParams(headSize = 1.3, shoulderWidth = 1.2))
        assertEquals(1.0, s.remove("neck").body.headSize, EPS)
        assertEquals(1.2, s.remove("clavL").body.shoulderWidth, EPS)
        assertEquals(1.3, s.remove("clavL").body.headSize, EPS)
    }

    @Test
    fun 肋骨は外せない() {
        assertSame(initial, initial.remove("rib"))
    }

    @Test
    fun 向きを戻す() {
        val s = initial.placeAll("clavL", "upperArmL", "forearmL")
            .withState("forearmL", PartState(1.0, PI, 0.5))
        assertEquals(PartState.rest(PartSpecs["forearmL"]), s.reset("forearmL").state("forearmL"))
        val moved = initial.withRoot(RootTransform(Vec3(1.0, 2.0, 3.0), Quat.fromAxisAngle(Vec3.X, 0.5), 1.4))
        assertEquals(RootTransform(), moved.reset("rib").root)
    }

    // ---------- ワールド変換 ----------

    @Test
    fun 休止状態の関節の位置() {
        val w = initial.worldTransforms()
        assertVec(ribPos, w.getValue("rib").position)
        assertVec(ribPos + Vec3(0.0, 0.15, -0.035), w.getValue("neck").position)
        assertVec(ribPos + Vec3(0.145, 0.12, 0.0), w.getValue("upperArmL").position)
        assertVec(ribPos + Vec3(-0.145, 0.12, 0.0), w.getValue("upperArmR").position)
        assertVec(ribPos + Vec3(0.145, 0.12 - 0.25 - 0.23, 0.0), w.getValue("handL").position)
    }

    @Test
    fun 肋骨の回転と拡大は子孫に効く() {
        val w = turned.worldTransforms()
        // y 軸まわり90度で x は −z へ
        assertVec(ribPos + Vec3(0.0, 0.12, -0.025), w.getValue("clavL").position)
        val big = initial.withRoot(initial.root.copy(scale = 2.0)).worldTransforms()
        assertVec(ribPos + Vec3(0.0, 0.30, -0.07), big.getValue("neck").position)
        assertEquals(2.0, big.getValue("head").scale, EPS)
    }

    @Test
    fun 親を回すと子が追従する() {
        val s = initial.placeAll("clavL", "upperArmL", "forearmL")
            .withState("upperArmL", PartState(PI / 2, PI, 0.0))
        val w = s.worldTransforms()
        // 上腕が前を向くので、前腕の関節は肩から前へ 0.25
        assertVec(w.getValue("upperArmL").position + Vec3(0.0, 0.0, 0.25), w.getValue("forearmL").position)
        // さらに肩を上へ振ると、前腕の関節も動く
        val w2 = s.withState("clavL", PartState(0.3, PI / 2, 0.0)).worldTransforms()
        assertTrue(w2.getValue("forearmL").position.distanceTo(w.getValue("forearmL").position) > 0.01)
        assertSameRotation(
            w2.getValue("clavL").rotation * localRotation(PartSpecs["upperArmL"], s.state("upperArmL")),
            w2.getValue("upperArmL").rotation,
        )
    }

    @Test
    fun 体型の調整が関節の位置と拡大率に効く() {
        val w = initial.withBody(BodyParams(headSize = 1.5, shoulderWidth = 1.5)).worldTransforms()
        assertVec(ribPos + Vec3(0.025 + 0.18, 0.12, 0.0), w.getValue("upperArmL").position)
        assertEquals(1.5, w.getValue("head").scale, EPS)
        assertEquals(1.0, w.getValue("neck").scale, EPS)
    }

    // ---------- 可動域の表示と玉 ----------

    @Test
    fun 可動域の中心と半径() {
        val big = initial.withRoot(initial.root.copy(scale = 2.0)).withBody(BodyParams(headSize = 1.5))
        assertVec(big.worldTransforms().getValue("neck").position, big.guideCenter("neck"))
        assertEquals(0.4, big.guideRadius("neck"), EPS)
        // 頭の可動域は親(首)の拡大率で決まり、頭自身の大きさは効かない
        assertEquals(0.22 * 2.0, big.guideRadius("head"), EPS)
    }

    @Test
    fun 玉のワールド位置() {
        val s = turned.placeAll("clavL", "upperArmL").withState("upperArmL", PartState(PI / 2, PI, 0.0))
        // 肋骨が y まわり90度なので、上腕の前(+z)はワールドの +x
        assertVec(s.guideCenter("upperArmL") + Vec3(0.25, 0.0, 0.0), s.dotWorld("upperArmL"))
    }

    // ---------- ワールドでの入力 ----------

    @Test
    fun ワールドの方向へ向ける() {
        val s = turned.placeAll("clavL", "upperArmL")
        assertState(PartState(PI / 2, PI, 0.0), s.aim("upperArmL", Vec3.X).state("upperArmL"))
    }

    @Test
    fun ワールドの点へ向ける() {
        val s = turned.placeAll("clavL", "upperArmL")
        val p = s.guideCenter("upperArmL") + Vec3(2.0, 0.0, 0.0)
        assertState(PartState(PI / 2, PI, 0.0), s.aimAtPoint("upperArmL", p).state("upperArmL"))
    }

    @Test
    fun ドラッグで向ける() {
        val s = initial.placeAll("clavL", "upperArmL")
        val c = s.guideCenter("upperArmL")
        // 正面から関節の中心を通るレイ。2つの交点は今の玉(真下)から等距離なので手前(前)を取る
        val ray = Ray(c + Vec3(0.0, 0.0, 5.0), Vec3(0.0, 0.0, -1.0))
        assertState(PartState(PI / 2, PI, 0.0), s.drag("upperArmL", ray).state("upperArmL"))
    }

    @Test
    fun 回転オーブ_部位() {
        // 肋骨が y まわり90度なので、ワールドの z 軸は肋骨の −x 軸。その軸で +90度は上腕を前へ振る
        val s = turned.placeAll("clavL", "upperArmL")
        assertState(PartState(PI / 2, PI, 0.0), s.orb("upperArmL", Quat.fromAxisAngle(Vec3.Z, PI / 2)).state("upperArmL"))
    }

    @Test
    fun 回転オーブ_肋骨() {
        val dw = Quat.fromAxisAngle(Vec3.X, 0.3)
        assertSameRotation(dw * turned.root.rotation, turned.orb("rib", dw).root.rotation)
    }

    @Test
    fun 置いていない部位も計算はできる() {
        assertFalse(initial.isPlaced("neck"))
        assertState(PartState(0.0, 0.0, 0.0), initial.aim("neck", Vec3.Y).state("neck"))
    }
}
