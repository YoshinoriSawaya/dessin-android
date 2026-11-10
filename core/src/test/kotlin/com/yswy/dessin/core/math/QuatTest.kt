package com.yswy.dessin.core.math

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/** q と −q は同じ回転なので、符号の違いは許す */
fun assertSameRotation(expected: Quat, actual: Quat) {
    val d = expected.x * actual.x + expected.y * actual.y + expected.z * actual.z + expected.w * actual.w
    assertEquals("同じ回転", 1.0, abs(d), EPS)
}

class QuatTest {
    private val samples = listOf(
        Vec3.X, Vec3.Y, Vec3.Z, Vec3(0.3, -0.8, 0.52), Vec3(-1.0, 2.0, 0.5),
    )

    @Test
    fun 単位回転は向きを変えない() {
        samples.forEach { assertVec(it, Quat.IDENTITY.rotate(it)) }
    }

    @Test
    fun 軸と角度からの回転() {
        val q = Quat.fromAxisAngle(Vec3.Z, PI / 2)
        assertVec(Vec3.Y, q.rotate(Vec3.X))
        assertVec(-Vec3.X, q.rotate(Vec3.Y))
        assertVec(Vec3.Z, q.rotate(Vec3.Z))
        // 正規化されていない軸でも同じ回転になる
        assertSameRotation(q, Quat.fromAxisAngle(Vec3(0.0, 0.0, 5.0), PI / 2))
    }

    @Test
    fun 積は右から順に適用する() {
        val a = Quat.fromAxisAngle(Vec3.Z, PI / 2)
        val b = Quat.fromAxisAngle(Vec3.X, PI / 3)
        samples.forEach { v -> assertVec(a.rotate(b.rotate(v)), (a * b).rotate(v)) }
    }

    @Test
    fun 逆元を掛けると元に戻る() {
        val q = Quat.fromAxisAngle(Vec3(1.0, 2.0, -0.5), 1.234)
        assertSameRotation(Quat.IDENTITY, q * q.inverse())
        assertSameRotation(Quat.IDENTITY, q.inverse() * q)
        samples.forEach { v -> assertVec(v, q.inverse().rotate(q.rotate(v))) }
    }

    @Test
    fun 単位ベクトル間の回転() {
        val pairs = listOf(
            Vec3.X to Vec3.Y,
            Vec3(0.0, -1.0, 0.0) to Vec3(0.3, -0.5, 0.8).normalized(),
            Vec3.Z to Vec3.Z,
        )
        pairs.forEach { (from, to) -> assertVec(to, Quat.fromUnitVectors(from, to).rotate(from)) }
    }

    @Test
    fun 正反対の単位ベクトル間の回転() {
        // three.js と同じく、直交する任意の軸で180度回す
        listOf(Vec3.X, Vec3.Y, Vec3.Z, Vec3(1.0, 1.0, 1.0).normalized()).forEach { from ->
            assertVec(-from, Quat.fromUnitVectors(from, -from).rotate(from))
        }
    }

    @Test
    fun 基底からの回転() {
        // 各軸の行き先を指定した回転。x→y、y→−x、z→z は z 軸まわりの90度
        val q = Quat.fromBasis(Vec3.Y, -Vec3.X, Vec3.Z)
        assertSameRotation(Quat.fromAxisAngle(Vec3.Z, PI / 2), q)

        val r = Quat.fromAxisAngle(Vec3(0.2, -1.0, 0.7), 2.5)
        val fromBasis = Quat.fromBasis(r.rotate(Vec3.X), r.rotate(Vec3.Y), r.rotate(Vec3.Z))
        assertSameRotation(r, fromBasis)
    }

    @Test
    fun 基底からの回転_trace負の各分岐() {
        // x・y・z 軸まわりの180度回転は、trace が負になり別々の分岐を通る
        listOf(Vec3.X, Vec3.Y, Vec3.Z, Vec3(1.0, 1.0, 0.0)).forEach { axis ->
            val r = Quat.fromAxisAngle(axis, PI)
            assertSameRotation(r, Quat.fromBasis(r.rotate(Vec3.X), r.rotate(Vec3.Y), r.rotate(Vec3.Z)))
        }
    }

    @Test
    fun 正規化() {
        val q = Quat(0.0, 0.0, 2.0, 2.0).normalized()
        assertEquals(1.0, sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w), EPS)
        assertSameRotation(Quat.fromAxisAngle(Vec3.Z, PI / 2), q)
    }
}
