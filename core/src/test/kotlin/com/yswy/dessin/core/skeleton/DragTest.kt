package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.Ray
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertVec
import org.junit.Assert.assertNull
import org.junit.Test

/** 期待値は docs/web-reference.html の dragPart から */
class DragTest {
    private val down = Vec3(0.0, 0.0, -1.0)

    @Test
    fun 球に当たれば交点のうち今の玉に近いほうを向く() {
        val ray = Ray(Vec3(0.0, 0.0, 5.0), down)
        assertVec(Vec3.Z, dragDirection(ray, Vec3.ZERO, 1.0, Vec3(0.0, 0.0, 0.9))!!)
        assertVec(-Vec3.Z, dragDirection(ray, Vec3.ZERO, 1.0, Vec3(0.1, 0.0, -0.95))!!)
    }

    @Test
    fun 中心から外れたレイの交点() {
        val ray = Ray(Vec3(0.6, 0.0, 5.0), down)
        assertVec(Vec3(0.6, 0.0, 0.8), dragDirection(ray, Vec3.ZERO, 1.0, Vec3(0.0, 0.0, 1.0))!!)
        assertVec(Vec3(0.6, 0.0, -0.8), dragDirection(ray, Vec3.ZERO, 1.0, Vec3(0.0, 0.0, -1.0))!!)
    }

    @Test
    fun 球の中心と半径を反映する() {
        val center = Vec3(1.0, 2.0, 3.0)
        val ray = Ray(Vec3(1.0, 3.2, 10.0), down)
        // 半径2の球: y 方向に 1.2 ずれたレイは z 方向 ±1.6 で当たる
        assertVec(Vec3(0.0, 0.6, 0.8), dragDirection(ray, center, 2.0, center + Vec3(0.0, 0.0, 2.0))!!)
    }

    @Test
    fun 外れたらレイ上で中心に最も近い点を向く() {
        val ray = Ray(Vec3(2.0, 0.0, 5.0), down)
        assertVec(Vec3.X, dragDirection(ray, Vec3.ZERO, 1.0, Vec3(0.0, -1.0, 0.0))!!)
    }

    @Test
    fun 外れて中心がレイの後ろならレイの始点を向く() {
        val ray = Ray(Vec3(2.0, 0.0, -5.0), down)
        assertVec(Vec3(2.0, 0.0, -5.0).normalized(), dragDirection(ray, Vec3.ZERO, 1.0, Vec3(0.0, -1.0, 0.0))!!)
    }

    @Test
    fun 向きが決まらなければnull() {
        // 半径0の球を中心で通ると、交点が中心と重なる
        val ray = Ray(Vec3(0.0, 0.0, 5.0), down)
        assertNull(dragDirection(ray, Vec3.ZERO, 0.0, Vec3(0.0, -1.0, 0.0)))
    }
}
