package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertSameRotation
import com.yswy.dessin.core.math.assertVec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val D2R = PI / 180

/** 期待値は docs/web-reference.html の baseQuat・dirVec・phiMax から */
class RangeOfMotionTest {
    @Test
    fun 方向_φが0なら休止方向() {
        listOf(0.0, 1.0, PI, -2.0).forEach { psi -> assertVec(Vec3(0.0, -1.0, 0.0), dirVec(0.0, psi)) }
    }

    @Test
    fun 方向_ψは0で負のz_π_2で正のx_πで正のz() {
        assertVec(Vec3(0.0, 0.0, -1.0), dirVec(PI / 2, 0.0))
        assertVec(Vec3(1.0, 0.0, 0.0), dirVec(PI / 2, PI / 2))
        assertVec(Vec3(0.0, 0.0, 1.0), dirVec(PI / 2, PI))
        assertVec(Vec3(-1.0, 0.0, 0.0), dirVec(PI / 2, -PI / 2))
        assertVec(Vec3(0.0, 1.0, 0.0), dirVec(PI, 0.3))
    }

    @Test
    fun 方向は単位ベクトル() {
        listOf(0.2 to 0.7, 1.3 to -2.1, 2.9 to 4.0).forEach { (phi, psi) ->
            assertEquals(1.0, dirVec(phi, psi).length(), EPS)
        }
    }

    @Test
    fun 基準フレーム_下向きでzが参照なら単位回転() {
        assertSameRotation(Quat.IDENTITY, baseQuat(Vec3(0.0, -1.0, 0.0), Vec3.Z))
    }

    @Test
    fun 基準フレーム_左肩はz軸まわりの90度() {
        assertSameRotation(Quat.fromAxisAngle(Vec3.Z, PI / 2), baseQuat(Vec3.X, Vec3.Z))
    }

    @Test
    fun 基準フレーム_足は負のyを前へ_zを上へ向ける() {
        val q = baseQuat(Vec3.Z, Vec3.Y)
        assertVec(Vec3.Z, q.rotate(Vec3(0.0, -1.0, 0.0)))
        assertVec(Vec3.Y, q.rotate(Vec3.Z))
    }

    @Test
    fun 基準フレーム_全部位で休止方向と参照軸の条件を満たす() {
        PartSpecs.all.filterNot { it.isRoot }.forEach { p ->
            val q = baseQuat(p.restDir, p.ref)
            val a0 = p.restDir.normalized()
            // フレームの −y が休止方向
            assertVec(a0, q.rotate(Vec3(0.0, -1.0, 0.0)))
            // フレームの z は休止方向に直交し、参照軸の側を向く
            val z = q.rotate(Vec3.Z)
            assertEquals(p.id, 0.0, z.dot(a0), EPS)
            assertTrue(p.id, z.dot(p.ref) > 0)
        }
    }

    @Test
    fun 基準フレーム_休止方向は正規化してから使う() {
        assertSameRotation(baseQuat(Vec3.X, Vec3.Z), baseQuat(Vec3(3.0, 0.0, 0.0), Vec3.Z))
    }

    @Test
    fun 可動域_各軸の方向ではコーンの値そのもの() {
        val cone = Cone(70 * D2R, 35 * D2R, 20 * D2R, 50 * D2R)
        assertEquals("ψ=0 は後ろ", cone.back, phiMax(cone, 0.0), EPS)
        assertEquals("ψ=π は前", cone.front, phiMax(cone, PI), EPS)
        assertEquals("ψ=π/2 は +x", cone.plusX, phiMax(cone, PI / 2), EPS)
        assertEquals("ψ=−π/2 は −x", cone.minusX, phiMax(cone, -PI / 2), EPS)
    }

    @Test
    fun 可動域_斜めは4象限の楕円() {
        val cone = Cone(70 * D2R, 35 * D2R, 20 * D2R, 50 * D2R)
        // ψ=π/4: sin・cos とも正 → +x と後ろの楕円
        val psi = PI / 4
        val m = phiMax(cone, psi)
        val s = m * sin(psi) / cone.plusX
        val c = m * cos(psi) / cone.back
        assertEquals("楕円の上", 1.0, s * s + c * c, EPS)
        // ψ=3π/4: sin 正・cos 負 → +x と前
        val m2 = phiMax(cone, 3 * PI / 4)
        assertEquals(1.0 / sqrt(0.5 / (cone.plusX * cone.plusX) + 0.5 / (cone.front * cone.front)), m2, EPS)
    }

    @Test
    fun 可動域_右側は鏡像のコーンで評価する() {
        assertEquals(15 * D2R, phiMax(PartSpecs["clavR"], PI / 2), EPS)
        assertEquals(35 * D2R, phiMax(PartSpecs["clavL"], PI / 2), EPS)
    }

    @Test
    fun 可動域_上限はπ() {
        assertEquals(PI, phiMax(Cone(4.0, 4.0, 4.0, 4.0), 0.7), EPS)
        // 上腕は前と +x が180度なので、その象限ではちょうど π
        assertEquals(PI, phiMax(PartSpecs["upperArmL"], 2.0), EPS)
    }

    @Test
    fun 可動域_0度の方向はほぼ動かない() {
        // Web版は0除算を避けて 0.001 ラジアンを下限にしている
        val cone = Cone(0.0, 0.0, 0.0, 0.0)
        assertEquals(0.001, phiMax(cone, 0.0), 1e-9)
        assertEquals(0.001, phiMax(cone, 1.0), 1e-9)
    }

    @Test
    fun 可動域_ヒンジはψに関係なく最大値() {
        val forearm = PartSpecs["forearmL"]
        listOf(0.0, 1.0, PI).forEach { psi -> assertEquals(150 * D2R, phiMax(forearm, psi), EPS) }
    }
}
