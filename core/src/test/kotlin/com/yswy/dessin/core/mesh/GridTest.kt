package com.yswy.dessin.core.mesh

import org.junit.Assert.assertEquals
import org.junit.Test

/** 期待値は three.js r128 の GridHelper(Web版は GridHelper(6, 12) を y=0.001 に置く) */
class GridTest {
    private val grid = gridLines(size = 6.0, divisions = 12, y = 0.001)

    @Test
    fun 線の数() {
        // 13本ずつ縦横。中心の2本は別の色
        assertEquals(2 * 2 * 3, grid.center.size)
        assertEquals(24 * 2 * 3, grid.others.size)
    }

    @Test
    fun 中心の線は原点を通る() {
        // x 方向の線(z=0)と z 方向の線(x=0)
        assertEquals(listOf(-3f, 0.001f, 0f, 3f, 0.001f, 0f, 0f, 0.001f, -3f, 0f, 0.001f, 3f), grid.center.toList())
    }

    @Test
    fun 線は05おきに端から端まで() {
        val zs = (0 until grid.others.size / 12).map { grid.others[it * 12 + 2] }.toSet()
        assertEquals((0..12).filter { it != 6 }.map { -3f + it * 0.5f }.toSet(), zs)
        for (i in grid.others.indices step 3) assertEquals(0.001f, grid.others[i + 1])
    }
}
