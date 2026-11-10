package com.yswy.dessin.core.math

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

/** three.js の Euler(順序 YXZ)と同じ取り決め: 回転は Ry·Rx·Rz */
class EulerTest {
    @Test
    fun 単軸の回転() {
        assertSameRotation(Quat.fromAxisAngle(Vec3.X, 0.4), Quat.fromEulerYXZ(Vec3(0.4, 0.0, 0.0)))
        assertSameRotation(Quat.fromAxisAngle(Vec3.Y, -1.1), Quat.fromEulerYXZ(Vec3(0.0, -1.1, 0.0)))
        assertSameRotation(Quat.fromAxisAngle(Vec3.Z, 2.0), Quat.fromEulerYXZ(Vec3(0.0, 0.0, 2.0)))
    }

    @Test
    fun 順序はYのあとにXのあとにZ() {
        val e = Vec3(0.3, 0.8, -0.5)
        val expected = Quat.fromAxisAngle(Vec3.Y, e.y) * Quat.fromAxisAngle(Vec3.X, e.x) * Quat.fromAxisAngle(Vec3.Z, e.z)
        assertSameRotation(expected, Quat.fromEulerYXZ(e))
    }

    @Test
    fun 四元数からオイラー角に戻せる() {
        listOf(Vec3(0.3, 0.8, -0.5), Vec3(-1.2, 2.9, 1.0), Vec3(0.0, -3.0, 0.2)).forEach { e ->
            assertVec(e, Quat.fromEulerYXZ(e).toEulerYXZ())
        }
    }

    @Test
    fun Xが90度のときはZを0にしてYで表す() {
        val q = Quat.fromEulerYXZ(Vec3(PI / 2, 0.5, 0.3))
        val e = q.toEulerYXZ()
        assertEquals(PI / 2, e.x, EPS)
        assertEquals(0.0, e.z, EPS)
        assertSameRotation(q, Quat.fromEulerYXZ(e))
    }
}
