package com.yswy.dessin.core.editor

import com.yswy.dessin.core.render.DisplayOptions
import com.yswy.dessin.core.render.drawPlan
import com.yswy.dessin.core.render.otherDollPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** E13-05: 人形のモード中は、操作中でない人形を淡く描く。「表示」の「ほかを淡く」で止められる */
class FadeOthersTest {
    private val two = EditorState().addDoll()

    @Test
    fun 既定はオンで_表示の切り替えで止められる() {
        assertTrue(EditorState().options.fadeOthers)
        assertTrue(EditorState().isOn(Display.FADE_OTHERS))
        val off = EditorState().toggle(Display.FADE_OTHERS)
        assertEquals(DisplayOptions(fadeOthers = false), off.options)
        assertFalse(off.isOn(Display.FADE_OTHERS))
    }

    @Test
    fun 淡くするのは人形のモード中で_切り替えがオンのときだけ() {
        assertFalse(two.fadeOthers)
        assertTrue(two.toggleDollMode().fadeOthers)
        assertFalse(two.toggleDollMode().toggle(Display.FADE_OTHERS).fadeOthers)
    }

    @Test
    fun ほかの人形の描き方は淡くするかを持つ() {
        val other = two.others.first()
        assertTrue(otherDollPlan(other, DisplayOptions(), faded = true).meshes.all { it.faded })
        assertTrue(otherDollPlan(other, DisplayOptions(), faded = false).meshes.none { it.faded })
        // 操作中の人形は淡くしない
        assertTrue(drawPlan(two.skeleton, two.selected, DisplayOptions()).meshes.none { it.faded })
    }

    @Test
    fun 画面の状態に残り_古い状態はオン() {
        val off = EditorState().toggle(Display.FADE_OTHERS)
        assertEquals(off.options, EditorStateCodec.decode(EditorStateCodec.encode(off))!!.options)
        val old = EditorStateCodec.encode(EditorState()).lines().joinToString("\n") { line ->
            if (line.startsWith("options ")) line.split(" ").take(6).joinToString(" ") else line
        }
        assertTrue(EditorStateCodec.decode(old)!!.options.fadeOthers)
    }
}
