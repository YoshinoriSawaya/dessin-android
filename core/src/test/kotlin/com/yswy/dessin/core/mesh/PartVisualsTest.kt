package com.yswy.dessin.core.mesh

import com.yswy.dessin.core.math.Mat4
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertVec
import com.yswy.dessin.core.skeleton.BodyParams
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.Skeleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 部位の見た目の組み立て(線・輪郭・ワールド行列・全体を表示の箱・玉の大きさ) */
class PartVisualsTest {
    @Test
    fun 輪郭はWeb版で指定したメッシュだけにある() {
        val withEdges = PartSpecs.all.flatMap { s -> PartVisuals.meshes(s.id).mapIndexedNotNull { i, m -> if (m.edges != null) "${s.id}:$i" else null } }.toSet()
        // 関節の玉・頭の球・付け根には輪郭が無い
        assertTrue("head:1" in withEdges)
        assertTrue("head:0" !in withEdges)
        assertTrue("neck:1" !in withEdges)
        assertTrue("hipL:0" !in withEdges)
        assertTrue("rib:0" in withEdges)
    }

    @Test
    fun 線の本数はWeb版と同じ() {
        fun lines(id: String, i: Int) = PartVisuals.meshes(id)[i].lines.size
        assertEquals(4, lines("rib", 0)) // 前後の縦線2本と輪2本
        assertEquals(1, lines("neck", 0)) // 縦線だけ
        assertEquals(2, lines("upperArmL", 0)) // 縦線と輪
        assertEquals(2, lines("head", 0)) // 縦と横の円
        assertEquals(2, lines("head", 1)) // 面の縦横
        assertEquals(1, lines("handR", 0))
        assertEquals(0, lines("hipL", 0))
        assertEquals(0, lines("thighL", 1)) // 関節の玉
    }

    @Test
    fun メッシュのワールド行列は関節の変換とメッシュの置き方の積() {
        val s = Skeleton.initial().place("neck")
        val m = meshWorldMatrix(s, "neck", 0)
        // 首の円柱は関節の 0.035 上
        assertVec(Vec3(0.0, 1.10 + 0.15 + 0.035, 0.0), m.transformPoint(Vec3.ZERO))
        val j = s.worldTransforms().getValue("neck")
        val p = PartVisuals.placement("neck", 0, s.body)
        val expected = Mat4.compose(j.position, j.rotation, j.scale) * Mat4.compose(p.position, p.rotation, p.scale)
        assertVec(expected.transformPoint(Vec3(0.01, 0.02, 0.03)), m.transformPoint(Vec3(0.01, 0.02, 0.03)))
    }

    @Test
    fun 全体を表示の箱は置いた部位だけを囲む() {
        val ribOnly = fitBounds(Skeleton.initial())!!
        // 肋骨の錐台: 上の半径 0.115、高さ 0.30、奥行きは 0.64 倍(線の分だけ少し広い)
        assertTrue(ribOnly.first.y <= 1.10 - 0.15 && ribOnly.first.y > 1.10 - 0.16)
        assertTrue(ribOnly.second.x >= 0.115 && ribOnly.second.x < 0.125)
        val withLeg = fitBounds(Skeleton.initial().place("pelvis").place("hipL").place("thighL"))!!
        assertTrue(withLeg.first.y < ribOnly.first.y - 0.3)
    }

    @Test
    fun 玉の大きさは視点からの距離に比例する() {
        assertEquals(0.03 * 2.0 / 4.3 * 1.6, dotWorldRadius(2.0), 1e-12)
        assertEquals(2 * dotWorldRadius(1.5), dotWorldRadius(3.0), 1e-12)
    }

    @Test
    fun 体型で置き方が変わるのは肩と骨盤と付け根() {
        val b = BodyParams(headSize = 1.3, shoulderWidth = 1.4, pelvisWidth = 1.2, hipInset = 0.02)
        PartSpecs.all.forEach { s ->
            PartVisuals.meshes(s.id).indices.forEach { i ->
                val changed = PartVisuals.placement(s.id, i, b) != PartVisuals.placement(s.id, i, BodyParams())
                val expected = s.id.startsWith("clav") || s.id == "pelvis" || s.id.startsWith("hip")
                assertEquals("${s.id}:$i", expected && i == 0, changed)
            }
        }
        assertNull(PartVisuals.meshes("rib").getOrNull(1))
    }
}
