package com.yswy.dessin.core.math

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.tan

class Mat4Test {
    @Test
    fun 単位行列は点を変えない() {
        assertVec(Vec3(1.0, -2.0, 3.0), Mat4.IDENTITY.transformPoint(Vec3(1.0, -2.0, 3.0)))
    }

    @Test
    fun 位置と回転と拡大から作る() {
        val q = Quat.fromAxisAngle(Vec3(0.3, 1.0, -0.2), 1.1)
        val m = Mat4.compose(Vec3(1.0, 2.0, 3.0), q, 1.5)
        val p = Vec3(0.4, -0.7, 0.2)
        assertVec(Vec3(1.0, 2.0, 3.0) + q.rotate(p * 1.5), m.transformPoint(p))
    }

    @Test
    fun 積は右から順に適用する() {
        val a = Mat4.compose(Vec3(1.0, 0.0, 0.0), Quat.fromAxisAngle(Vec3.Z, PI / 2), 1.0)
        val b = Mat4.compose(Vec3(0.0, 2.0, 0.0), Quat.fromAxisAngle(Vec3.X, 0.4), 2.0)
        val p = Vec3(0.3, 0.1, -0.5)
        assertVec(a.transformPoint(b.transformPoint(p)), (a * b).transformPoint(p))
    }

    @Test
    fun 剛体変換の逆() {
        val m = Mat4.compose(Vec3(1.0, 2.0, 3.0), Quat.fromAxisAngle(Vec3(1.0, 1.0, 0.0), 0.8), 1.0)
        val p = Vec3(0.3, -0.4, 2.0)
        assertVec(p, m.inverseRigid().transformPoint(m.transformPoint(p)))
    }

    @Test
    fun 透視投影_近い面と遠い面と視野の端() {
        val fov = 35 * PI / 180
        val m = Mat4.perspective(fov, 0.5, 0.1, 60.0)
        assertEquals(-1.0, m.project(Vec3(0.0, 0.0, -0.1)).z, EPS)
        assertEquals(1.0, m.project(Vec3(0.0, 0.0, -60.0)).z, EPS)
        val t = tan(fov / 2)
        assertVec(Vec3(1.0, 0.0, -1.0), m.project(Vec3(0.1 * t * 0.5, 0.0, -0.1)))
        assertEquals(1.0, m.project(Vec3(0.0, 5.0 * t, -5.0)).y, EPS)
    }

    @Test
    fun GL用の配列は列優先() {
        val f = Mat4.compose(Vec3(1.0, 2.0, 3.0), Quat.IDENTITY, 1.0).toFloatArray()
        assertEquals(16, f.size)
        assertEquals(1f, f[12])
        assertEquals(2f, f[13])
        assertEquals(3f, f[14])
        assertEquals(1f, f[15])
    }
}
