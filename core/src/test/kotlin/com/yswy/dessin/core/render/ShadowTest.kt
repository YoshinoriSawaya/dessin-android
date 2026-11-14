package com.yswy.dessin.core.render

import com.yswy.dessin.core.editor.Display
import com.yswy.dessin.core.editor.EditorState
import com.yswy.dessin.core.editor.EditorStateCodec
import com.yswy.dessin.core.editor.isOn
import com.yswy.dessin.core.editor.toggle
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.mesh.fitBounds
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.Skeleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** E11: 体に落ちる影。太陽から見た正射影(シャドウマップ用)と表示の切り替え */
class ShadowTest {
    private val all = PartSpecs.all.fold(Skeleton.initial()) { s, p -> s.place(p.id) }

    @Test
    fun 太陽の向きは陰影と同じ() {
        assertEquals(Vec3(2.2, 4.5, 3.2).normalized().x, SUN_DIRECTION.x, 1e-12)
        assertEquals(1.0, SUN_DIRECTION.length(), 1e-12)
    }

    @Test
    fun 置いた部位の箱がすべて影の範囲に入る() {
        val (lo, hi) = fitBounds(all)!!
        val m = sunLightMatrix(all)!!
        for (i in 0 until 8) {
            val c = Vec3(if (i and 1 == 0) lo.x else hi.x, if (i and 2 == 0) lo.y else hi.y, if (i and 4 == 0) lo.z else hi.z)
            val p = m.transformPoint(c)
            for (v in listOf(p.x, p.y, p.z)) assertTrue("$c → $p", v in -1.0..1.0)
        }
    }

    @Test
    fun 太陽に近い点ほど奥行きが小さい() {
        val (lo, hi) = fitBounds(all)!!
        val center = (lo + hi) * 0.5
        val m = sunLightMatrix(all)!!
        val near = m.transformPoint(center + SUN_DIRECTION * 0.2)
        val far = m.transformPoint(center - SUN_DIRECTION * 0.2)
        assertTrue(near.z < far.z)
        // 太陽の向きに沿って動いても、影の画像の上の位置は変わらない
        assertEquals(near.x, far.x, 1e-9)
        assertEquals(near.y, far.y, 1e-9)
    }

    @Test
    fun 何も置いていなければ影の範囲は無い() {
        assertNull(sunLightMatrix(Skeleton(Skeleton.initial().root, Skeleton.initial().states, emptySet(), Skeleton.initial().body)))
    }

    @Test
    fun 影は表示で切り替えられる_既定はオン_画面の状態に残る() {
        val e = EditorState()
        assertTrue(e.isOn(Display.SHADOWS))
        val off = e.toggle(Display.SHADOWS)
        assertFalse(off.options.shadows)
        assertEquals(off, EditorStateCodec.decode(EditorStateCodec.encode(off)))
    }

    @Test
    fun 影の項目が無い古い画面の状態も読める() {
        val text = EditorStateCodec.encode(EditorState()).replace(Regex("options (\\S+ \\S+ \\S+ \\S+) \\S+"), "options $1")
        assertTrue(text.contains(Regex("options \\S+ \\S+ \\S+ \\S+\n")))
        assertTrue(EditorStateCodec.decode(text)!!.options.shadows)
    }
}
