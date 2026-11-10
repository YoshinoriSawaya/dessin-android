package com.yswy.dessin.core.editor

import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.camera.ViewPreset
import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.mesh.fitBounds
import com.yswy.dessin.core.render.DisplayOptions
import com.yswy.dessin.core.skeleton.Skeleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 期待値は docs/web-reference.html の toolChip(全体を表示・視点・ガイド・正中線・関節の玉・格子)から */
class ViewControlsTest {
    private val cam = OrbitCamera(target = Vec3(0.3, 0.5, 0.1), yaw = 1.0, pitch = 0.4, distance = 5.0)
    private val start = EditorState(camera = cam).select("neck").select("pelvis")

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
        val loaded = Skeleton.initial().place("pelvis").place("hipL").place("thighL")
        val s = EditorState(camera = cam).select("neck").loadPose(loaded, aspect = 0.5)
        assertEquals(loaded, s.skeleton)
        val (lo, hi) = fitBounds(loaded)!!
        assertEquals(cam.fit(lo, hi, 0.5), s.camera)
    }

    @Test
    fun 選択中の部位が置かれていなければ肋骨を選ぶ() {
        val loaded = Skeleton.initial().place("pelvis")
        assertEquals("rib", EditorState().select("neck").loadPose(loaded, 1.0).selected)
        assertEquals("pelvis", EditorState().select("pelvis").loadPose(loaded, 1.0).selected)
    }
}
