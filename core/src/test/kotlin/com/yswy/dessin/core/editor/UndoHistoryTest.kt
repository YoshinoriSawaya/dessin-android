package com.yswy.dessin.core.editor

import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.skeleton.BodyParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** E15-01: Undo と Redo。人形の変更(場面の全員と操作中の人形)を戻す。視点・表示・選択・モードは戻さない */
class UndoHistoryTest {
    private val s0 = EditorState()
    private val empty = UndoHistory()

    /** 1回の操作を記録する(ボタンなど) */
    private fun step(h: UndoHistory, before: EditorState, change: (EditorState) -> EditorState): Pair<UndoHistory, EditorState> {
        val after = change(before)
        return h.change(before, after) to after
    }

    @Test
    fun 初めは戻せない_やり直せない() {
        assertFalse(empty.canUndo)
        assertFalse(empty.canRedo)
        assertSame(s0, empty.undo(s0).second)
    }

    @Test
    fun 人形の変更を戻して_やり直す() {
        val (h1, s1) = step(empty, s0) { it.select("neck") }
        val (h2, s2) = step(h1, s1) { it.applySlider(SliderKind.ROLL, 20) }
        assertTrue(h2.canUndo)
        val (h3, back) = h2.undo(s2)
        assertEquals(s1.skeleton, back.skeleton)
        assertTrue(h3.canRedo)
        val (h4, again) = h3.redo(back)
        assertEquals(s2.skeleton, again.skeleton)
        assertFalse(h4.canRedo)
        val (_, first) = h4.undo(again).let { (h, s) -> h.undo(s) }
        assertEquals(s0.skeleton, first.skeleton)
    }

    @Test
    fun 視点_表示_選択だけの変更は記録しない() {
        val placed = s0.select("neck")
        val (h1, s1) = step(empty, placed) { it.withCamera(OrbitCamera()).toggle(Display.GRID).toggleDollMode() }
        assertFalse(h1.canUndo)
        // 置いてある部位を選び直すのも記録しない
        val (h2, _) = step(h1, s1) { it.select("rib") }
        assertFalse(h2.canUndo)
    }

    @Test
    fun 戻しても視点と表示と選択は今のまま() {
        val (h1, s1) = step(empty, s0) { it.select("neck") }
        val moved = s1.withCamera(OrbitCamera(Vec3(1.0, 1.0, 1.0), 0.3, 0.2, 4.0)).toggle(Display.GRID).select("rib")
        val (_, back) = h1.undo(moved)
        assertEquals(moved.camera, back.camera)
        assertEquals(moved.options, back.options)
        assertEquals("rib", back.selected)
    }

    @Test
    fun 選んでいた部位が戻した人形に無ければ胸を選ぶ() {
        val (h1, s1) = step(empty, s0) { it.select("neck") }
        val (_, back) = h1.undo(s1)
        assertEquals("rib", back.selected)
    }

    @Test
    fun 人形の追加と体型も戻せる() {
        val (h1, s1) = step(empty, s0) { it.addDoll() }
        val (h2, s2) = step(h1, s1) { it.loadBody(BodyParams(headSize = 1.2)) }
        val (h3, b1) = h2.undo(s2)
        assertEquals(s1.dolls, b1.dolls)
        val (_, b0) = h3.undo(b1)
        assertEquals(1, b0.dolls.size)
        assertEquals(0, b0.activeDoll)
    }

    @Test
    fun ドラッグの途中は記録せず_1回の操作を1段にまとめる() {
        val placed = s0.select("neck")
        var h = empty.begin(placed)
        var s = placed
        for (deg in listOf(5, 10, 15)) {
            val next = s.applySlider(SliderKind.ROLL, deg)
            h = h.change(s, next)
            s = next
        }
        h = h.commit(s)
        val (h2, back) = h.undo(s)
        assertEquals(placed.skeleton, back.skeleton)
        assertFalse(h2.canUndo)
    }

    @Test
    fun 何も変えなかった操作は記録しない() {
        val h = empty.begin(s0).commit(s0)
        assertFalse(h.canUndo)
    }

    @Test
    fun 新しく変更するとやり直しは消える() {
        val (h1, s1) = step(empty, s0) { it.select("neck") }
        val (h2, back) = h1.undo(s1)
        val (h3, _) = step(h2, back) { it.select("flank") }
        assertFalse(h3.canRedo)
    }

    @Test
    fun 履歴は100段まで() {
        var h = empty
        var s = s0.select("neck")
        repeat(130) { i ->
            val next = s.applySlider(SliderKind.ROLL, i % 60 - 30)
            h = h.change(s, next)
            s = next
        }
        var n = 0
        while (h.canUndo) { val (h2, s2) = h.undo(s); h = h2; s = s2; n++ }
        assertEquals(UndoHistory.LIMIT, n)
    }

    @Test
    fun 操作の途中で戻すと_その操作を確定してから戻す() {
        val placed = s0.select("neck")
        val h = empty.begin(placed)
        val dragged = placed.applySlider(SliderKind.ROLL, 20)
        val (h2, back) = h.change(placed, dragged).undo(dragged)
        assertEquals(placed.skeleton, back.skeleton)
        assertTrue(h2.canRedo)
    }
}
