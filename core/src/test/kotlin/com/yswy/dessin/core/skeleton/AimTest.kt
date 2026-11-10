package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

private const val D2R = PI / 180

/** 期待値は docs/web-reference.html の aimPart から */
class AimTest {
    /** 部位の基準フレームで (phi, psi) の向きを、親の空間の方向にする */
    private fun dirOf(spec: PartSpec, phi: Double, psi: Double): Vec3 =
        baseQuat(spec.restDir, spec.ref).rotate(dirVec(phi, psi))

    private fun assertState(expected: PartState, actual: PartState) {
        assertEquals("phi", expected.phi, actual.phi, EPS)
        assertEquals("psi", expected.psi, actual.psi, EPS)
        assertEquals("roll", expected.roll, actual.roll, EPS)
    }

    @Test
    fun 可動域の中ならその向きになる_ロールは保つ() {
        listOf("neck", "upperArmL", "upperArmR", "clavR", "thighL", "footR").forEach { id ->
            val p = PartSpecs[id]
            val psi = 2.0
            val phi = phiMax(p, psi) * 0.6
            val result = aimPart(p, PartState(0.1, 0.2, 0.3), dirOf(p, phi, psi))
            assertState(PartState(phi, psi, 0.3), result)
        }
    }

    @Test
    fun 可動域の外なら同じψの境界に収める() {
        val neck = PartSpecs["neck"]
        // 首の後ろ(ψ=0)は50度まで
        assertState(PartState(50 * D2R, 0.0, 0.0), aimPart(neck, PartState(0.0, 0.0, 0.0), dirOf(neck, 80 * D2R, 0.0)))
        // 斜めも、その向きの上限に収める
        val psi = 2.4
        assertState(PartState(phiMax(neck, psi), psi, 0.0), aimPart(neck, PartState(0.0, 0.0, 0.0), dirOf(neck, 1.5, psi)))
    }

    @Test
    fun 右側は鏡像のコーンで収める() {
        val clavR = PartSpecs["clavR"]
        assertState(PartState(15 * D2R, PI / 2, 0.0), aimPart(clavR, PartState(0.0, 0.0, 0.0), dirOf(clavR, 80 * D2R, PI / 2)))
    }

    @Test
    fun 休止方向に向けるとψは前の値を保つ() {
        val p = PartSpecs["upperArmL"]
        assertState(PartState(0.0, 1.3, 0.0), aimPart(p, PartState(0.5, 1.3, 0.0), p.restDir))
    }

    @Test
    fun 方向は正規化してから使う() {
        val p = PartSpecs["thighL"]
        val d = dirOf(p, 0.5, 2.0)
        assertState(aimPart(p, PartState(0.0, 0.0, 0.0), d), aimPart(p, PartState(0.0, 0.0, 0.0), d * 3.0))
    }

    @Test
    fun 肘_前へ向けると90度曲がる() {
        val p = PartSpecs["forearmL"]
        assertState(PartState(PI / 2, PI, 0.2), aimPart(p, PartState(0.0, PI, 0.2), Vec3.Z))
    }

    @Test
    fun 肘_後ろや真上へ向けると範囲の端に収める() {
        val p = PartSpecs["forearmL"]
        assertState(PartState(-15 * D2R, PI, 0.0), aimPart(p, PartState(0.0, PI, 0.0), Vec3(0.0, 0.0, -1.0)))
        assertState(PartState(150 * D2R, PI, 0.0), aimPart(p, PartState(0.0, PI, 0.0), Vec3.Y))
    }

    @Test
    fun 膝_後ろへ向けると曲がり_前へは曲がらない() {
        val p = PartSpecs["shinR"]
        assertState(PartState(PI / 2, 0.0, 0.0), aimPart(p, PartState(0.0, 0.0, 0.0), Vec3(0.0, 0.0, -1.0)))
        assertState(PartState(0.0, 0.0, 0.0), aimPart(p, PartState(0.3, 0.0, 0.0), Vec3.Z))
    }

    @Test
    fun ヒンジは曲げる面の外の成分を無視する() {
        val p = PartSpecs["forearmL"]
        val sideways = Vec3(1.0, -1.0, 0.0) * (1 / sqrt(2.0))
        assertState(PartState(0.0, PI, 0.0), aimPart(p, PartState(0.7, PI, 0.0), sideways))
    }
}
