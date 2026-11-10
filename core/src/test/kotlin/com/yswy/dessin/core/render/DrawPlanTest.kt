package com.yswy.dessin.core.render

import com.yswy.dessin.core.mesh.PartVisuals
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.Skeleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 期待値は docs/web-reference.html の refresh から */
class DrawPlanTest {
    private val initial = Skeleton.initial()
    private val all = PartSpecs.all.fold(initial) { s, p -> s.place(p.id) }

    private fun DrawPlan.of(id: String) = meshes.filter { it.partId == id }

    @Test
    fun 初期状態_肋骨は選択中の色_置ける部位はゴースト() {
        val plan = drawPlan(initial, "rib", DisplayOptions())
        assertEquals(listOf(MeshDraw("rib", 0, MeshStyle.HOT, edges = true, lines = true)), plan.of("rib"))
        // 置ける部位(首・骨盤・左右の肩)は関節の玉を除いてゴースト。線と輪郭は出さない
        assertEquals(listOf(MeshDraw("neck", 0, MeshStyle.GHOST, edges = false, lines = false)), plan.of("neck"))
        assertEquals(listOf(MeshDraw("flank", 0, MeshStyle.GHOST, edges = false, lines = false)), plan.of("flank"))
        assertTrue(plan.of("pelvis").isEmpty())
        assertEquals(1, plan.of("clavL").size)
        // 置けない部位は描かない
        assertTrue(plan.of("head").isEmpty())
        assertTrue(plan.of("upperArmL").isEmpty())
        // 肋骨には可動域が無い
        assertNull(plan.guidePart)
    }

    @Test
    fun 置いた部位は種類ごとの色_選択中は全部が選択中の色() {
        val plan = drawPlan(all, "upperArmL", DisplayOptions())
        assertEquals(MeshStyle.BASE, plan.of("neck")[0].style)
        assertEquals(MeshStyle.MARKER, plan.of("neck")[1].style)
        assertEquals(MeshStyle.BASE, plan.of("head")[0].style)
        assertEquals(MeshStyle.MASK, plan.of("head")[1].style)
        assertEquals(listOf(MeshStyle.HOT, MeshStyle.HOT), plan.of("upperArmL").map { it.style })
        assertEquals("upperArmL", plan.guidePart)
    }

    @Test
    fun 全部置くと全メッシュを描く() {
        val plan = drawPlan(all, "rib", DisplayOptions())
        assertEquals(PartSpecs.all.sumOf { PartVisuals.meshes(it.id).size }, plan.meshes.size)
        assertTrue(plan.meshes.none { it.style == MeshStyle.GHOST })
    }

    @Test
    fun 関節の玉を消すと置いた部位の玉を描かない() {
        val plan = drawPlan(all, "rib", DisplayOptions(markers = false))
        assertTrue(plan.meshes.none { it.style == MeshStyle.MARKER })
        assertEquals(1, plan.of("neck").size)
    }

    @Test
    fun 正中線を消すと線だけ消える_輪郭は残る() {
        val plan = drawPlan(all, "rib", DisplayOptions(lines = false))
        assertTrue(plan.meshes.none { it.lines })
        assertTrue(plan.of("rib")[0].edges)
    }

    @Test
    fun ガイドを消すとゴーストと可動域を描かない() {
        val plan = drawPlan(initial.place("neck"), "neck", DisplayOptions(guides = false))
        assertTrue(plan.meshes.none { it.style == MeshStyle.GHOST })
        assertNull(plan.guidePart)
        assertTrue(plan.of("head").isEmpty())
    }

    @Test
    fun 選択中でも置いていなければ可動域は出さない() {
        assertNull(drawPlan(initial, "neck", DisplayOptions()).guidePart)
    }
}
