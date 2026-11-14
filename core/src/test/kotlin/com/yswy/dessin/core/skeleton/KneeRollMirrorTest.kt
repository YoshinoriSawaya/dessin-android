package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * 不具合の再現(2026-11-14、利用者「膝の転がりを調整すると、全身の関節が動いている」):
 * 右側の部位の回転の中心のずれが鏡像で −0.0 になり、「ずれ無し」と判定されず、右側の部位がすべてひざとして扱われていた
 */
class KneeRollMirrorTest {
    private val all = PartSpecs.all.fold(Skeleton.initial()) { s, p -> s.place(p.id) }
        .withState("upperArmR", PartState(PI / 3, 0.5, 0.2))
        .withState("thighR", PartState(PI / 4, 0.3, 0.1))
        .withState("forearmR", PartState(PI / 2, PI, 0.0))
        .withState("shinL", PartState(PI / 2, 0.0, 0.0))
        .withState("shinR", PartState(PI / 2, 0.0, 0.0))

    @Test
    fun 回転の中心がずれるのはすねだけ() {
        val b = BodyParams(kneeRoll = 0.05)
        PartSpecs.all.filterNot { it.id.startsWith("shin") }.forEach { spec ->
            assertEquals(spec.id, 0.0, pivotOf(spec, b).lengthSq(), 0.0)
        }
        listOf("shinL", "shinR").forEach { assertEquals(it, 0.05, pivotOf(PartSpecs[it], b).y, 0.0) }
    }

    @Test
    fun ひざの転がりを変えて動くのはすねから先だけ() {
        val before = all.worldTransforms()
        val after = all.withBody(BodyParams(kneeRoll = 0.06)).worldTransforms()
        val shinSide = setOf("shinL", "footL", "toeL", "shinR", "footR", "toeR")
        PartSpecs.all.filterNot { it.id in shinSide }.forEach { p ->
            assertEquals(p.id, 0.0, before.getValue(p.id).position.distanceTo(after.getValue(p.id).position), 1e-12)
        }
        assertTrue(before.getValue("shinR").position.distanceTo(after.getValue("shinR").position) > 0.01)
    }

    @Test
    fun 鏡像の状態なら左右の位置も鏡像() {
        val mirrored = all
            .withState("upperArmL", PartState(PI / 3, -0.5, -0.2))
            .withState("thighL", PartState(PI / 4, -0.3, -0.1))
            .withState("forearmL", PartState(PI / 2, PI, 0.0))
        val w = mirrored.worldTransforms()
        for (base in listOf("upperArm", "forearm", "hand", "thigh", "shin", "foot")) {
            val l = w.getValue("${base}L").position
            val r = w.getValue("${base}R").position
            assertEquals(base, Vec3(-l.x, l.y, l.z).distanceTo(r), 0.0, 1e-9)
        }
    }
}
