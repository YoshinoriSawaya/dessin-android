package com.yswy.dessin.core.editor

import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.skeleton.PartState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** E13-01: 場面(複数の人形と操作中の人形) */
class DollSceneTest {
    private val one = EditorState().select("neck")
    private val two = one.addDoll()

    @Test
    fun 初めは1体で_操作中の人形が場面のすべて() {
        assertEquals(listOf(one.skeleton), one.dolls)
        assertEquals(0, one.activeDoll)
    }

    @Test
    fun 人形を足すと新しい人形が操作中になり_横にずらして置く() {
        assertEquals(2, two.dolls.size)
        assertEquals(1, two.activeDoll)
        assertEquals("rib", two.selected)
        assertEquals(one.skeleton, two.dolls[0])
        val x0 = two.dolls[0].root.position.x
        val x1 = two.dolls[1].root.position.x
        assertTrue("横にずれる: $x0 → $x1", x1 - x0 >= 0.5)
        // 新しい人形は胸だけを置いた初めの状態(体型は既定)
        assertEquals(NaturalPose.initial.placed, two.skeleton.placed)
        assertEquals(NaturalPose.initial.body, two.skeleton.body)
    }

    @Test
    fun 操作は操作中の人形だけに効く_体型も人形ごと() {
        val s = two.select("neck").let { it.copy(skeleton = it.skeleton.withState("neck", PartState(0.3, 1.0, 0.0))) }
            .applySlider(SliderKind.HEIGHT, 120).let { it.select("rib").applySlider(SliderKind.HEIGHT, 120) }
        assertEquals(one.skeleton, s.dolls[0])
        assertEquals(1.2, s.dolls[1].body.height, 1e-9)
        assertEquals(1.0, s.dolls[0].body.height, 1e-9)
    }

    @Test
    fun 切り替えると操作中の人形が変わり_それぞれの状態を保つ() {
        val moved = two.select("neck").let { it.copy(skeleton = it.skeleton.withState("neck", PartState(0.3, 1.0, 0.0))) }
        val back = moved.switchDoll(0)
        assertEquals(0, back.activeDoll)
        assertEquals(one.skeleton, back.skeleton)
        // 選んでいた部位(首)は人形0にも置いてあるのでそのまま
        assertEquals("neck", back.selected)
        assertEquals(moved.skeleton, back.dolls[1])
        assertEquals(moved.dolls, back.dolls)
        // 戻る
        assertEquals(moved.skeleton, back.switchDoll(1).skeleton)
    }

    @Test
    fun 切り替え先に選んでいた部位が無ければ胸を選ぶ() {
        val s = two.switchDoll(0).switchDoll(1)
        assertEquals("rib", s.selected)
        val onNeck = two.switchDoll(0)
        assertEquals("rib", onNeck.switchDoll(1).selected)
    }

    @Test
    fun 範囲外や今の人形への切り替えでは何も変わらない() {
        assertSame(two, two.switchDoll(5))
        assertSame(two, two.switchDoll(-1))
        assertSame(two, two.switchDoll(1))
    }

    @Test
    fun 人形は10体まで() {
        var s = one
        repeat(20) { s = s.addDoll() }
        assertEquals(MAX_DOLLS, s.dolls.size)
        assertEquals(10, MAX_DOLLS)
    }

    @Test
    fun 消すのは操作中の人形_最後の1体は消せない() {
        val three = two.addDoll().switchDoll(1)
        val removed = three.removeDoll()
        assertEquals(2, removed.dolls.size)
        assertEquals(listOf(three.dolls[0], three.dolls[2]), removed.dolls)
        // 前の人形が操作中になる
        assertEquals(0, removed.activeDoll)
        assertSame(one, one.removeDoll())
    }

    @Test
    fun 全体を表示はすべての人形が入る() {
        val s = two.withCamera(OrbitCamera()).fitView(1.0)
        val x0 = s.dolls[0].root.position.x
        val x1 = s.dolls[1].root.position.x
        assertTrue(s.camera!!.target.x > x0 + 0.1 && s.camera!!.target.x < x1 - 0.1)
        assertNotEquals(one.withCamera(OrbitCamera()).fitView(1.0).camera, s.camera)
    }

    @Test
    fun 画面の状態に全員が残る() {
        val s = two.select("neck").applySlider(SliderKind.HEIGHT, 110).switchDoll(0)
        assertEquals(s, EditorStateCodec.decode(EditorStateCodec.encode(s)))
    }

    @Test
    fun 足した人形の位置は空いているところ() {
        val three = two.addDoll()
        val xs = three.dolls.map { it.root.position.x }
        assertEquals(xs.sorted(), xs)
        assertEquals(Vec3(xs[2], NaturalPose.initial.root.position.y, NaturalPose.initial.root.position.z), three.dolls[2].root.position)
    }

    @Test
    fun 人形を足すときに体型を渡すと_その体型で置く() {
        // E07-04: 既定の体型は次に足す人形から効く。今いる人形の体型は変えない
        val body = com.yswy.dessin.core.skeleton.BodyParams(headSize = 1.2, thighLength = 1.1)
        val s = one.addDoll(body)
        assertEquals(body, s.dolls[1].body)
        assertEquals(one.skeleton, s.dolls[0])
        assertEquals(two.dolls[1].withBody(body), s.dolls[1])
    }
}
