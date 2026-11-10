package com.yswy.dessin.core.math

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

const val EPS = 1e-6

fun assertVec(expected: Vec3, actual: Vec3) {
    assertEquals("x", expected.x, actual.x, EPS)
    assertEquals("y", expected.y, actual.y, EPS)
    assertEquals("z", expected.z, actual.z, EPS)
}

class Vec3Test {
    @Test
    fun 足し算_引き算_スカラー倍() {
        val a = Vec3(1.0, 2.0, 3.0)
        val b = Vec3(-4.0, 0.5, 2.0)
        assertVec(Vec3(-3.0, 2.5, 5.0), a + b)
        assertVec(Vec3(5.0, 1.5, 1.0), a - b)
        assertVec(Vec3(2.0, 4.0, 6.0), a * 2.0)
        assertVec(Vec3(-1.0, -2.0, -3.0), -a)
    }

    @Test
    fun 内積と外積() {
        assertEquals(0.0, Vec3.X.dot(Vec3.Y), EPS)
        assertEquals(32.0, Vec3(1.0, 2.0, 3.0).dot(Vec3(4.0, 5.0, 6.0)), EPS)
        assertVec(Vec3.Z, Vec3.X.cross(Vec3.Y))
        assertVec(Vec3.X, Vec3.Y.cross(Vec3.Z))
        assertVec(-Vec3.Z, Vec3.Y.cross(Vec3.X))
    }

    @Test
    fun 長さと正規化() {
        val v = Vec3(3.0, 0.0, 4.0)
        assertEquals(5.0, v.length(), EPS)
        assertEquals(25.0, v.lengthSq(), EPS)
        assertVec(Vec3(0.6, 0.0, 0.8), v.normalized())
        assertEquals(sqrt(2.0), Vec3.X.distanceTo(Vec3.Y), EPS)
    }

    @Test
    fun 長さ0の正規化は0を返す() {
        // three.js の normalize と同じく、0で割らない
        assertVec(Vec3.ZERO, Vec3.ZERO.normalized())
    }
}
