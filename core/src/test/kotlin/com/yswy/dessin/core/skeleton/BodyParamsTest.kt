package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertVec
import org.junit.Assert.assertEquals
import org.junit.Test

/** 期待値は docs/web-reference.html の setShoulderWidth・updateHips・setPelvisWidth・syncUI から */
class BodyParamsTest {
    private val default = BodyParams()

    @Test
    fun 初期値() {
        assertEquals(BodyParams(headSize = 1.0, shoulderWidth = 1.0, pelvisWidth = 1.0, hipInset = 0.0407), default)
    }

    @Test
    fun スライダーの範囲はWeb版と同じ() {
        assertEquals(0.6..1.4, BodyParams.HEAD_SIZE_RANGE)
        assertEquals(0.4..1.5, BodyParams.SHOULDER_WIDTH_RANGE)
        assertEquals(0.6..1.5, BodyParams.PELVIS_WIDTH_RANGE)
        assertEquals(0.0..0.08, BodyParams.HIP_INSET_RANGE)
    }

    @Test
    fun 初期値なら取り付け位置は定義のまま() {
        PartSpecs.all.forEach { assertVec(it.attach, attachOf(it, default)) }
        assertEquals(0.05, hipLength(default), EPS)
    }

    @Test
    fun 肩幅は上腕の取り付け位置だけを広げる() {
        val p = default.copy(shoulderWidth = 1.5)
        assertVec(Vec3(0.18, 0.0, 0.0), attachOf(PartSpecs["upperArmL"], p))
        assertVec(Vec3(-0.18, 0.0, 0.0), attachOf(PartSpecs["upperArmR"], p))
        PartSpecs.all.filterNot { it.id.startsWith("upperArm") }.forEach { assertVec(it.attach, attachOf(it, p)) }
    }

    @Test
    fun 骨盤の横幅は付け根を長くし_ももを外へ動かす() {
        val p = default.copy(pelvisWidth = 1.5)
        // 外縁 0.1157×1.5 から内寄り 0.0407 を引き、付け根の取り付け位置 0.025 を引いた長さ
        val len = 0.1157 * 1.5 - 0.0407 - 0.025
        assertEquals(len, hipLength(p), EPS)
        assertVec(Vec3(len, 0.0, 0.0), attachOf(PartSpecs["thighL"], p))
        assertVec(Vec3(-len, 0.0, 0.0), attachOf(PartSpecs["thighR"], p))
    }

    @Test
    fun 内寄りを大きくすると付け根は短くなる() {
        val p = default.copy(hipInset = 0.06)
        assertEquals(0.1157 - 0.06 - 0.025, hipLength(p), EPS)
    }

    @Test
    fun 股関節は中心から003より内へは寄らない() {
        val p = default.copy(pelvisWidth = 0.6, hipInset = 0.08)
        assertEquals(0.03 - 0.025, hipLength(p), EPS)
        assertVec(Vec3(0.005, 0.0, 0.0), attachOf(PartSpecs["thighL"], p))
    }

    @Test
    fun 頭の大きさは頭の関節の拡大率() {
        val p = default.copy(headSize = 1.3)
        assertEquals(1.3, jointScale(PartSpecs["head"], p), EPS)
        PartSpecs.all.filterNot { it.id == "head" }.forEach { assertEquals(it.id, 1.0, jointScale(it, p), EPS) }
    }
}
