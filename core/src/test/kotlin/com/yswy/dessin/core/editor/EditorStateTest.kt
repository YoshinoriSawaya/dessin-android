package com.yswy.dessin.core.editor

import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.render.DisplayOptions
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.PartState
import com.yswy.dessin.core.skeleton.Skeleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 期待値は docs/web-reference.html の select・del・jReset・fold・configUI・buildChips・refresh から */
class EditorStateTest {
    private val initial = EditorState()

    // ---------- 操作 ----------

    @Test
    fun 初期状態は肋骨を選び_パネルは開いている() {
        assertEquals(Skeleton.initial(), initial.skeleton)
        assertEquals("rib", initial.selected)
        assertNull(initial.camera)
        assertEquals(DisplayOptions(), initial.options)
        assertTrue(initial.panelOpen)
    }

    @Test
    fun 選ぶと置かれる() {
        val s = initial.select("neck")
        assertEquals("neck", s.selected)
        assertTrue(s.skeleton.isPlaced("neck"))
    }

    @Test
    fun 置けない部位は選べない() {
        assertSame(initial, initial.select("head"))
    }

    @Test
    fun 置いてある部位を選び直しても姿勢は変わらない() {
        val s = initial.select("neck").let { it.copy(skeleton = it.skeleton.withState("neck", PartState(0.3, 1.0, 0.1))) }.select("rib").select("neck")
        assertEquals(PartState(0.3, 1.0, 0.1), s.skeleton.state("neck"))
    }

    @Test
    fun 外すと子孫も外れて親を選ぶ() {
        val s = initial.select("clavL").select("upperArmL").select("forearmL").select("upperArmL").removeSelected()
        assertEquals("clavL", s.selected)
        assertFalse(s.skeleton.isPlaced("upperArmL"))
        assertFalse(s.skeleton.isPlaced("forearmL"))
    }

    @Test
    fun 肋骨は外せない() {
        assertSame(initial, initial.removeSelected())
        assertFalse(initial.canRemove)
        assertTrue(initial.select("neck").canRemove)
    }

    @Test
    fun 向きを戻すのは選択中の部位だけ() {
        val s = initial.select("neck").select("flank").select("pelvis").let {
            it.copy(skeleton = it.skeleton.withState("neck", PartState(0.3, 1.0, 0.1)).withState("pelvis", PartState(0.2, 0.5, 0.0)))
        }.resetSelected()
        // E09 で、向きを戻すと自然な姿勢(まっすぐではない)に戻るようにした
        assertEquals(NaturalPose.state(PartSpecs["pelvis"]), s.skeleton.state("pelvis"))
        assertEquals(PartState(0.3, 1.0, 0.1), s.skeleton.state("neck"))
    }

    @Test
    fun パネルを折りたたむ() {
        assertFalse(initial.togglePanel().panelOpen)
        assertTrue(initial.togglePanel().togglePanel().panelOpen)
    }

    // ---------- 選択中の部位の表示 ----------

    @Test
    fun 説明文は部位の種類で変わる() {
        assertEquals("ドラッグで画面上の位置を決めます。", initial.selectionInfo)
        assertEquals("緑の扇の中をドラッグ、または2回タップして曲げます。", initial.select("clavL").select("upperArmL").select("forearmL").selectionInfo)
        assertEquals("緑の範囲の中をドラッグ、または2回タップして向きを決めます。", initial.select("neck").selectionInfo)
        assertEquals("首", initial.select("neck").selectionLabel)
    }

    // ---------- 部位チップ ----------

    @Test
    fun チップは配置の段ごとに区切る() {
        val groups = initial.partChips.map { g -> g.map { it.id } }
        assertEquals(
            listOf(
                listOf("rib"),
                listOf("clavL", "clavR", "neck", "flank"),
                listOf("upperArmL", "upperArmR", "head", "pelvis"),
                listOf("forearmL", "forearmR", "hipL", "hipR"),
                listOf("handL", "handR", "thighL", "thighR"),
                listOf("shinL", "shinR"),
                listOf("footL", "footR"),
                listOf("toeL", "toeR"), // E08-03
            ),
            groups,
        )
        assertEquals("左上腕", initial.partChips[2][0].label)
    }

    @Test
    fun チップの見た目_選択中_置いた_置ける_置けない() {
        val chips = initial.select("neck").partChips.flatten().associateBy { it.id }
        assertEquals(ChipLook.SELECTED, chips.getValue("neck").look)
        assertEquals(ChipLook.PLACED, chips.getValue("rib").look)
        assertEquals(ChipLook.AVAILABLE, chips.getValue("head").look)
        assertEquals(ChipLook.AVAILABLE, chips.getValue("clavL").look)
        assertEquals(ChipLook.DISABLED, chips.getValue("upperArmL").look)
        assertTrue(chips.getValue("head").enabled)
        assertFalse(chips.getValue("upperArmL").enabled)
    }

    // ---------- 視点 ----------

    @Test
    fun 視点を差し替える() {
        val cam = OrbitCamera(target = Vec3(0.0, 1.0, 0.0))
        assertEquals(cam, initial.withCamera(cam).camera)
    }
}
