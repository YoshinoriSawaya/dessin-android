package com.yswy.dessin.core.editor

import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.camera.ViewPreset
import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.mesh.fitBounds
import com.yswy.dessin.core.render.DisplayOptions
import com.yswy.dessin.core.skeleton.BodyParams
import com.yswy.dessin.core.skeleton.Skeleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 期待値は docs/web-reference.html の toolChip(全体を表示・視点・ガイド・正中線・関節の玉・格子)から */
class ViewControlsTest {
    private val cam = OrbitCamera(target = Vec3(0.3, 0.5, 0.1), yaw = 1.0, pitch = 0.4, distance = 5.0)
    private val start = EditorState(camera = cam).select("neck").select("flank").select("pelvis")

    @Test
    fun 表示の切替は1つずつ反転する() {
        assertEquals(DisplayOptions(guides = false), start.toggle(Display.GUIDES).options)
        assertEquals(DisplayOptions(lines = false), start.toggle(Display.LINES).options)
        assertEquals(DisplayOptions(markers = false), start.toggle(Display.MARKERS).options)
        assertEquals(DisplayOptions(grid = false), start.toggle(Display.GRID).options)
        assertEquals(DisplayOptions(), start.toggle(Display.GRID).toggle(Display.GRID).options)
    }

    @Test
    fun 切替の状態() {
        assertTrue(start.isOn(Display.GUIDES))
        assertFalse(start.toggle(Display.MARKERS).isOn(Display.MARKERS))
    }

    @Test
    fun 視点のプリセットは向きだけ変える() {
        val s = start.preset(ViewPreset.SIDE)
        assertEquals(cam.preset(ViewPreset.SIDE), s.camera)
        assertEquals(cam.target, s.camera!!.target)
    }

    @Test
    fun 全体を表示は置いた部位を囲む箱に合わせる_向きは変えない() {
        val s = start.fitView(aspect = 0.6)
        val (lo, hi) = fitBounds(start.skeleton)!!
        assertEquals(cam.fit(lo, hi, 0.6), s.camera)
        assertEquals(cam.yaw, s.camera!!.yaw, EPS)
    }

    @Test
    fun 視点が無ければ初期の視点から() {
        val s = EditorState().fitView(aspect = 1.0)
        val (lo, hi) = fitBounds(EditorState().skeleton)!!
        assertEquals(OrbitCamera().fit(lo, hi, 1.0), s.camera)
        assertEquals(OrbitCamera().preset(ViewPreset.TOP), EditorState().preset(ViewPreset.TOP).camera)
    }
}

class LoadPoseTest {
    @Test
    fun 読み込むとポーズを差し替えて全体を表示する_視点の向きは保つ() {
        val cam = OrbitCamera(yaw = 1.2, pitch = 0.3, distance = 4.0)
        val loaded = Skeleton.initial().place("flank").place("pelvis").place("hipL").place("thighL")
        val s = EditorState(camera = cam).select("neck").loadPose(loaded, aspect = 0.5)
        // 置いていない部位は自然な姿勢になる(2026-11-14 の不具合の修正)
        assertEquals(NaturalPose.unplacedNatural(loaded), s.skeleton)
        val (lo, hi) = fitBounds(loaded)!!
        assertEquals(cam.fit(lo, hi, 0.5), s.camera)
    }

    @Test
    fun 選択中の部位が置かれていなければ肋骨を選ぶ() {
        val loaded = Skeleton.initial().place("flank").place("pelvis")
        assertEquals("rib", EditorState().select("neck").loadPose(loaded, 1.0).selected)
        assertEquals("pelvis", EditorState().select("flank").select("pelvis").loadPose(loaded, 1.0).selected)
    }

    @Test
    fun 読み込んでもいまの体型を保つ_ポーズの体型は読み飛ばす() {
        // E07-03: 体型はポーズと別に持つ。古いポーズ(版2)に入っている体型も使わない
        val body = BodyParams(headSize = 1.2, neckLength = 1.5)
        val loaded = Skeleton.initial().place("flank").withBody(BodyParams(headSize = 0.7))
        val s = EditorState().let { it.copy(skeleton = it.skeleton.withBody(body)) }.loadPose(loaded, 1.0)
        assertEquals(body, s.skeleton.body)
        assertTrue(s.skeleton.isPlaced("flank"))
    }
}

/** E07-03: 保存した体型を読み込む */
class LoadBodyTest {
    @Test
    fun 体型だけを差し替え_ポーズと選択は保つ() {
        val posed = EditorState().select("neck").let { it.copy(skeleton = it.skeleton.aim("neck", Vec3(0.2, 1.0, 0.1).normalized())) }
        val body = BodyParams(shoulderWidth = 1.3, thighLength = 1.1)
        val s = posed.loadBody(body)
        assertEquals(posed.skeleton.withBody(body), s.skeleton)
        assertEquals("neck", s.selected)
    }
}
