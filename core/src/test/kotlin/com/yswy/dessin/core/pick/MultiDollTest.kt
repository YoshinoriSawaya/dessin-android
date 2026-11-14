package com.yswy.dessin.core.pick

import com.yswy.dessin.core.editor.EditorState
import com.yswy.dessin.core.math.Ray
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.mesh.fitBounds
import com.yswy.dessin.core.render.DisplayOptions
import com.yswy.dessin.core.render.MeshStyle
import com.yswy.dessin.core.render.otherDollPlan
import com.yswy.dessin.core.render.sunLightMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** E13-03: 複数の人形の描画とピッキング */
class MultiDollTest {
    private val scene = EditorState().select("neck").select("head").addDoll().select("neck")

    /** 人形 i の胸の中心へ、前(+z)から向かうレイ */
    private fun rayAtChest(i: Int): Ray {
        val c = scene.dolls[i].root.position + Vec3(0.0, 0.05, 0.0)
        return Ray(c + Vec3(0.0, 0.0, 3.0), Vec3(0.0, 0.0, -1.0))
    }

    @Test
    fun 人形のタップで何番目の人形かが分かる_何も無ければ_null() {
        assertEquals(0, pickDoll(scene.dolls, rayAtChest(0)))
        assertEquals(1, pickDoll(scene.dolls, rayAtChest(1)))
        assertNull(pickDoll(scene.dolls, Ray(Vec3(0.0, 5.0, 3.0), Vec3(0.0, 0.0, -1.0))))
    }

    @Test
    fun 手前の人形を選ぶ() {
        // 人形1を人形0の真後ろ(奥)へ動かす
        val behind = scene.copy(skeleton = scene.skeleton.withRoot(scene.skeleton.root.copy(position = scene.dolls[0].root.position + Vec3(0.0, 0.0, -1.0))))
        assertEquals(0, pickDoll(behind.dolls, rayAtChest(0)))
    }

    @Test
    fun ほかの人形は置いた部位だけを描く_選択の色や可動域や影は無い() {
        val plan = otherDollPlan(scene.dolls[0], DisplayOptions())
        assertTrue(plan.meshes.isNotEmpty())
        assertTrue(plan.meshes.all { scene.dolls[0].isPlaced(it.partId) })
        assertTrue(plan.meshes.none { it.style == MeshStyle.HOT || it.style == MeshStyle.GHOST })
        assertNull(plan.guidePart)
        // 関節の玉を消す設定には従う
        assertTrue(otherDollPlan(scene.dolls[0], DisplayOptions(markers = false)).meshes.none { it.style == MeshStyle.MARKER })
    }

    @Test
    fun 影の範囲はすべての人形が入る() {
        val m = sunLightMatrix(scene.dolls)!!
        scene.dolls.forEach { d ->
            val (lo, hi) = fitBounds(d)!!
            for (p in listOf(lo, hi)) {
                val q = m.transformPoint(p)
                for (v in listOf(q.x, q.y, q.z)) assertTrue("$p → $q", v in -1.0..1.0)
            }
        }
    }
}
