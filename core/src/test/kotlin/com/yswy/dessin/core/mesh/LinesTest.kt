package com.yswy.dessin.core.mesh

import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertVec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 正中線などの管(Web版の densify・cl)と、輪郭線(Web版の EdgesGeometry)の性質 */
class LinesTest {
    @Test
    fun 細かく点を打つ_開いた線() {
        val pts = densify(listOf(Vec3.ZERO, Vec3(0.1, 0.0, 0.0)), closed = false, step = 0.015)
        // 0.1 / 0.015 を切り上げて7区間、終点も入れて8点
        assertEquals(8, pts.size)
        assertVec(Vec3.ZERO, pts.first())
        assertVec(Vec3(0.1, 0.0, 0.0), pts.last())
        assertVec(Vec3(0.1 / 7, 0.0, 0.0), pts[1])
    }

    @Test
    fun 細かく点を打つ_閉じた線は終点を足さない() {
        val square = listOf(Vec3.ZERO, Vec3(0.01, 0.0, 0.0), Vec3(0.01, 0.01, 0.0), Vec3(0.0, 0.01, 0.0))
        // 各辺が step より短いので1区間ずつ
        assertEquals(4, densify(square, closed = true, step = 0.015).size)
    }

    @Test
    fun 管は線のまわりに半径だけ離れる() {
        val pts = listOf(Vec3.ZERO, Vec3(0.0, 0.05, 0.0), Vec3(0.03, 0.08, 0.0))
        val m = tube(pts, closed = false, radius = 0.0028, sides = 4)
        val dense = densify(pts, closed = false, step = 0.015)
        assertEquals(dense.size * 4, m.vertexCount)
        // 各頂点は、対応する点からちょうど半径の距離
        for (i in 0 until m.vertexCount) {
            val v = Vec3(m.positions[i * 3].toDouble(), m.positions[i * 3 + 1].toDouble(), m.positions[i * 3 + 2].toDouble())
            assertEquals(0.0028, v.distanceTo(dense[i / 4]), 1e-6)
        }
        // 開いた線は端をふさがない: 区間ごとに4面×2枚
        assertEquals((dense.size - 1) * 4 * 2 * 3, m.indices.size)
    }

    @Test
    fun 閉じた管は最後と最初をつなぐ() {
        val circle = (0 until 48).map { val t = it / 48.0 * 2 * Math.PI; Vec3(0.1 * kotlin.math.cos(t), 0.0, 0.1 * kotlin.math.sin(t)) }
        val m = tube(circle, closed = true, radius = 0.0028, sides = 4)
        val n = densify(circle, closed = true, step = 0.015).size
        assertEquals(n * 4 * 2 * 3, m.indices.size)
    }

    @Test
    fun 輪郭線_箱は12本() {
        assertEquals(12, segments(edges(Primitives.box(0.1, 0.2, 0.3), 30.0)))
    }

    @Test
    fun 輪郭線_円柱は上下の縁だけ() {
        assertEquals(40, segments(edges(Primitives.cylinder(0.03, 0.03, 0.1, 20), 30.0)))
    }

    @Test
    fun 輪郭線_なめらかな球には出ない() {
        assertEquals(0, segments(edges(Primitives.sphere(0.1, 18, 14), 30.0)))
    }

    @Test
    fun 輪郭線_一部だけの球は縁に出る() {
        val m = Primitives.sphere(0.125, 32, 24, Math.PI / 2 - 0.95, 1.9, Math.PI / 2 - 0.7, 1.9)
        assertEquals(2 * 32 + 2 * 24, segments(edges(m, 30.0)))
    }

    @Test
    fun 輪郭線の端点は形の頂点の上() {
        val m = Primitives.box(0.1, 0.2, 0.3)
        val e = edges(m, 30.0)
        for (i in e.indices step 3) {
            assertEquals(0.05, abs(e[i].toDouble()), EPS)
            assertEquals(0.1, abs(e[i + 1].toDouble()), EPS)
            assertEquals(0.15, abs(e[i + 2].toDouble()), EPS)
        }
        assertTrue(e.isNotEmpty())
    }

    /** 線分の数(端点2つ × xyz) */
    private fun segments(e: FloatArray) = e.size / 6
}
