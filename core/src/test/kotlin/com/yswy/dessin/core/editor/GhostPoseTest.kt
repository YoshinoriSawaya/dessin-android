package com.yswy.dessin.core.editor

import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.Skeleton
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 不具合の再現(2026-11-14、利用者): 付け根を操作しているとき、半透明の太もも(置く前の影)の角度が、
 * 置いたときの角度(自然な姿勢。E09)とずれている。置いていない部位も自然な姿勢で持つ
 */
class GhostPoseTest {
    private fun assertNaturalUnplaced(s: EditorState) {
        PartSpecs.all.filterNot { it.isRoot || s.skeleton.isPlaced(it.id) }.forEach { spec ->
            assertEquals(spec.id, NaturalPose.state(spec), s.skeleton.state(spec.id))
        }
    }

    @Test
    fun 付け根を選んだとき_太ももの影は置いたときと同じ角度() {
        val hip = EditorState().select("flank").select("pelvis").select("hipL")
        val ghost = hip.skeleton.worldTransforms().getValue("thighL")
        val placed = hip.select("thighL").skeleton.worldTransforms().getValue("thighL")
        assertEquals(0.0, ghost.position.distanceTo(placed.position), 1e-12)
        com.yswy.dessin.core.math.assertSameRotation(placed.rotation, ghost.rotation)
    }

    @Test
    fun 初めの状態で置いていない部位は自然な姿勢() {
        assertNaturalUnplaced(EditorState())
    }

    @Test
    fun 外した部位も自然な姿勢に戻る() {
        val s = EditorState().select("flank").select("pelvis").select("hipL").select("thighL").select("pelvis").removeSelected()
        assertNaturalUnplaced(s)
    }

    @Test
    fun ポーズを読み込んだあとも置いていない部位は自然な姿勢() {
        assertNaturalUnplaced(EditorState().loadPose(Skeleton.initial().place("flank"), 1.0))
    }

    @Test
    fun 画面の状態から戻したあとも置いていない部位は自然な姿勢() {
        val s = EditorState().select("neck")
        assertNaturalUnplaced(EditorStateCodec.decode(EditorStateCodec.encode(s))!!)
    }
}
