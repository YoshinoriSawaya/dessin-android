package com.yswy.dessin.core.mesh

import com.yswy.dessin.core.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

/** 形の詳細な値は MeshGoldenTest で Web版と照合する。ここでは性質を確かめる */
class PrimitivesTest {
    private fun vertex(m: Mesh, i: Int) = Vec3(m.positions[i * 3].toDouble(), m.positions[i * 3 + 1].toDouble(), m.positions[i * 3 + 2].toDouble())
    private fun normal(m: Mesh, i: Int) = Vec3(m.normals[i * 3].toDouble(), m.normals[i * 3 + 1].toDouble(), m.normals[i * 3 + 2].toDouble())

    private fun assertWellFormed(m: Mesh) {
        assertEquals(m.positions.size, m.normals.size)
        assertEquals(0, m.indices.size % 3)
        assertTrue(m.indices.all { it in 0 until m.vertexCount })
        for (i in 0 until m.vertexCount) assertEquals(1.0, normal(m, i).length(), 1e-5)
    }

    @Test
    fun 箱() {
        val m = Primitives.box(0.2, 0.4, 0.6)
        assertWellFormed(m)
        assertEquals(24, m.vertexCount)
        assertEquals(36, m.indices.size)
        for (i in 0 until m.vertexCount) {
            val v = vertex(m, i)
            assertEquals(0.1, kotlin.math.abs(v.x), 1e-6)
            assertEquals(0.2, kotlin.math.abs(v.y), 1e-6)
            assertEquals(0.3, kotlin.math.abs(v.z), 1e-6)
        }
    }

    @Test
    fun 円柱() {
        val m = Primitives.cylinder(0.1, 0.1, 0.5, 20)
        assertWellFormed(m)
        // 胴 21×2、ふた (20 + 21)×2
        assertEquals(42 + 82, m.vertexCount)
        assertEquals((20 * 2 + 20 * 2) * 3, m.indices.size)
        for (i in 0 until 42) {
            val v = vertex(m, i)
            assertEquals(0.1, sqrt(v.x * v.x + v.z * v.z), 1e-6)
        }
    }

    @Test
    fun 球() {
        val m = Primitives.sphere(0.3, 18, 14)
        assertWellFormed(m)
        assertEquals(19 * 15, m.vertexCount)
        for (i in 0 until m.vertexCount) assertEquals(0.3, vertex(m, i).length(), 1e-6)
    }

    @Test
    fun 拡大すると法線は逆の比で傾く() {
        val m = Primitives.sphere(1.0, 8, 6).scaled(1.0, 2.0, 1.0)
        assertWellFormed(m)
        // 赤道の上の点では、法線は y にあまり傾かない
        for (i in 0 until m.vertexCount) {
            val v = vertex(m, i)
            val n = normal(m, i)
            // 楕円体 x² + (y/2)² + z² = 1 の法線は (x, y/4, z) の向き
            val expected = Vec3(v.x, v.y / 4, v.z).normalized()
            if (expected.length() > 0) assertEquals(1.0, n.dot(expected), 1e-5)
        }
    }

    @Test
    fun 一部だけの球() {
        val m = Primitives.sphere(0.125, 32, 24, PI / 2 - 0.95, 1.9, PI / 2 - 0.7, 1.9)
        assertWellFormed(m)
        assertEquals(33 * 25, m.vertexCount)
        // 極を含まないので全部の四角形が2枚の三角形
        assertEquals(32 * 24 * 2 * 3, m.indices.size)
    }
}
