package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertSameRotation
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

private const val D2R = PI / 180

/** 期待値は docs/web-reference.html の sphereRotate から */
class OrbTest {
    private fun assertState(expected: PartState, actual: PartState) {
        assertEquals("phi", expected.phi, actual.phi, EPS)
        assertEquals("psi", expected.psi, actual.psi, EPS)
        assertEquals("roll", expected.roll, actual.roll, EPS)
    }

    /** 部位がいま向いている方向(親の空間) */
    private fun axisOf(spec: PartSpec, s: PartState): Vec3 = localRotation(spec, s).rotate(spec.restDir.normalized())

    @Test
    fun 回転しなければ状態は変わらない() {
        listOf(
            "thighL" to PartState(0.5, 1.0, 0.2),
            "upperArmR" to PartState(1.2, -2.0, -0.5),
            "neck" to PartState(0.3, 2.5, 0.1),
        ).forEach { (id, s) -> assertState(s, orbRotate(PartSpecs[id], s, Quat.IDENTITY)) }
    }

    @Test
    fun 部位の軸まわりの回転はロールに足される() {
        val p = PartSpecs["thighL"]
        val s = PartState(0.5, 1.0, 0.1)
        val twist = Quat.fromAxisAngle(axisOf(p, s), 0.2)
        assertState(PartState(0.5, 1.0, 0.3), orbRotate(p, s, twist))
    }

    @Test
    fun 振る回転は向きを変える() {
        // 左上腕を x 軸まわりに −90度: 下向きから前向き(+z)へ
        val p = PartSpecs["upperArmL"]
        assertState(PartState(PI / 2, PI, 0.0), orbRotate(p, PartState.rest(p), Quat.fromAxisAngle(Vec3.X, -PI / 2)))
    }

    @Test
    fun 振りは可動域に収める() {
        // 首を後ろ(−z)へ80度倒そうとしても50度まで
        val p = PartSpecs["neck"]
        assertState(PartState(50 * D2R, 0.0, 0.0), orbRotate(p, PartState.rest(p), Quat.fromAxisAngle(Vec3.X, -80 * D2R)))
    }

    @Test
    fun ロールは範囲に収める() {
        val neck = PartSpecs["neck"]
        assertState(PartState(0.0, 0.0, 30 * D2R), orbRotate(neck, PartState.rest(neck), Quat.fromAxisAngle(neck.restDir, 1.0)))
        val clav = PartSpecs["clavL"]
        assertState(PartState(0.0, 0.0, 0.0), orbRotate(clav, PartState.rest(clav), Quat.fromAxisAngle(clav.restDir, 0.5)))
    }

    @Test
    fun ロールは負のπからπに折り返してから収める() {
        // 0.4 + 3.0 = 3.4 は −2.88 と同じ向き。折り返すので下限 −90度に収まる
        val p = PartSpecs["upperArmL"]
        val s = PartState(0.0, 0.0, 0.4)
        assertState(PartState(0.0, 0.0, -PI / 2), orbRotate(p, s, Quat.fromAxisAngle(axisOf(p, s), 3.0)))
    }

    @Test
    fun 右側でもロールを範囲に収める() {
        // 前腕は左右とも ±90度(E06-01)
        val p = PartSpecs["forearmR"]
        val s = PartState.rest(p)
        assertState(PartState(0.0, PI, 90 * D2R), orbRotate(p, s, Quat.fromAxisAngle(axisOf(p, s), 2.0)))
    }

    @Test
    fun ヒンジは曲げる面の回転だけ受け付ける() {
        val p = PartSpecs["forearmL"]
        val rest = PartState.rest(p)
        assertState(PartState(PI / 2, PI, 0.0), orbRotate(p, rest, Quat.fromAxisAngle(Vec3.X, -PI / 2)))
        // 横へ倒す回転は曲げにならない
        assertState(PartState(0.0, PI, 0.0), orbRotate(p, rest, Quat.fromAxisAngle(Vec3.Z, 0.6)))
    }

    @Test
    fun 根はワールドの回転をそのまま後から掛ける() {
        val current = Quat.fromAxisAngle(Vec3.Y, 0.7)
        val dw = Quat.fromAxisAngle(Vec3.X, 0.3)
        assertSameRotation(dw * current, orbRotateRoot(current, dw))
    }
}
