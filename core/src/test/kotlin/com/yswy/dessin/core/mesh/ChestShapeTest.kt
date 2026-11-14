package com.yswy.dessin.core.mesh

import com.yswy.dessin.core.math.Mat4
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.skeleton.BodyParams
import com.yswy.dessin.core.skeleton.PartSpecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** E08-01: 胸は肋骨のように、上と下が狭まった形 */
class ChestShapeTest {
    /** 部位の関節の空間での、メッシュの頂点 */
    private fun points(id: String, index: Int = 0): List<Vec3> {
        val p = PartVisuals.placement(id, index, BodyParams())
        val m = Mat4.compose(p.position, p.rotation, p.scale)
        val mesh = PartVisuals.meshes(id)[index].mesh
        return (0 until mesh.vertexCount).map { m.transformPoint(mesh.vertex(it)) }
    }

    /** 高さ y 付近(±tol)の頂点の横幅の半分 */
    private fun halfWidthAt(pts: List<Vec3>, y: Double, tol: Double = 0.004) =
        pts.filter { abs(it.y - y) <= tol }.maxOf { abs(it.x) }

    private val chest = points("rib")
    private val top = chest.maxOf { it.y }
    private val bottom = chest.minOf { it.y }

    @Test
    fun 高さは今と同じ() {
        assertEquals(0.15, top, 1e-6)
        assertEquals(-0.045, bottom, 1e-6)
    }

    @Test
    fun 中ほどが最も広く_上と下は狭い() {
        val widest = chest.maxOf { abs(it.x) }
        assertEquals(0.12, widest, 1e-3)
        val yOfWidest = chest.maxBy { abs(it.x) }.y
        assertTrue("最も広いのは中ほど: $yOfWidest", yOfWidest in 0.0..0.06)
        assertTrue("上端は狭い", halfWidthAt(chest, top) < 0.09)
        assertTrue("下端も狭い", halfWidthAt(chest, bottom) < widest - 0.01)
    }

    @Test
    fun 前後の厚みは横幅の064倍() {
        val widest = chest.maxOf { abs(it.x) }
        assertEquals(widest * 0.64, chest.maxOf { abs(it.z) }, 1e-3)
    }

    @Test
    fun 脇腹の上端は胸の下端とつながる() {
        // 脇腹の関節は胸の空間で (0, −0.045, −0.035)。休止状態では回転しない
        val flankAttach = PartSpecs["flank"].attach
        val flank = points("flank").map { it + flankAttach }
        val flankTop = flank.maxOf { it.y }
        assertEquals(bottom, flankTop, 1e-6)
        assertEquals(halfWidthAt(chest, bottom), halfWidthAt(flank, flankTop), 1e-4)
    }

    @Test
    fun 上端の切り口は前と肩の側で低い() {
        // E14-02: 首側を斜めに切る。後ろの中央が最も高く(0.15)、前へ 25°・肩へ 15° 下がる
        assertEquals(0.15, PartVisuals.chestTopY(0.0, -0.0544), 1e-3)
        val backTop = chest.filter { it.z < 0 && abs(it.x) < 1e-6 }.maxOf { it.y }
        // 前・横の面の縁だけを見る(ふたの真ん中の頂点を除く)
        val frontTop = chest.filter { it.z > 0.05 && abs(it.x) < 1e-6 }.maxOf { it.y }
        val sideTop = chest.filter { abs(it.z) < 1e-6 && it.x > 0.05 }.maxOf { it.y }
        assertEquals(0.15, backTop, 1e-6)
        assertTrue("前は後ろより 0.04 以上低い: $frontTop", frontTop < backTop - 0.04)
        assertTrue("肩の側も低い: $sideTop", sideTop < backTop - 0.02)
        // どの頂点も切り口より上に出ない
        chest.forEach { assertTrue("切り口より上: $it", it.y <= PartVisuals.chestTopY(it.x, it.z) + 1e-5) }
    }

    @Test
    fun 首と肩の付け根は切り口の近くにある() {
        // 付け根は今の位置のまま。切り口からの高さのずれが 1cm 以内
        val neck = PartSpecs["neck"].attach
        val clav = PartSpecs["clavL"].attach
        assertEquals(neck.y, PartVisuals.chestTopY(neck.x, neck.z), 0.01)
        assertEquals(clav.y, PartVisuals.chestTopY(clav.x, clav.z), 0.01)
    }

    @Test
    fun 前の縦線は切り口で止まる() {
        val p = PartVisuals.placement("rib", 0, BodyParams())
        val m = Mat4.compose(p.position, p.rotation, p.scale)
        val front = PartVisuals.meshes("rib")[0].lines[0]
        val pts = (0 until front.vertexCount).map { m.transformPoint(front.vertex(it)) }
        // 前・横の面の縁だけを見る(ふたの真ん中の頂点を除く)
        val frontTop = chest.filter { it.z > 0.05 && abs(it.x) < 1e-6 }.maxOf { it.y }
        assertTrue(pts.maxOf { it.y } < frontTop + 0.01)
    }

    @Test
    fun 線は前後の縦線2本と輪1本のまま_縦線は形に沿う() {
        val lines = PartVisuals.meshes("rib")[0].lines
        assertEquals(3, lines.size)
        // 前の縦線は面より外にある(どの高さでも胸の前面から離れすぎない)
        val p = PartVisuals.placement("rib", 0, BodyParams())
        val m = Mat4.compose(p.position, p.rotation, p.scale)
        val front = lines[0]
        val zs = (0 until front.vertexCount).map { m.transformPoint(front.vertex(it)) }
        val maxFrontZ = chest.maxOf { it.z }
        assertTrue(zs.maxOf { it.z } < maxFrontZ + 0.01)
        assertTrue(zs.minOf { it.z } > 0.0)
    }
}
