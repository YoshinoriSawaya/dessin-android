package com.yswy.dessin.core.editor

import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.render.DisplayOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 画面の状態を文字列にして戻す(画面の作り直しやプロセスの終了で状態を失わないため) */
class EditorStateCodecTest {
    private val edited = EditorState()
        .select("neck").select("head").select("clavL").select("upperArmL")
        .let { it.copy(skeleton = it.skeleton.aim("upperArmL", Vec3(0.3, -0.5, 0.8).normalized())) }
        .withCamera(OrbitCamera(target = Vec3(0.1, 1.0, -0.2), yaw = 1.3, pitch = -0.4, distance = 3.1))
        .copy(options = DisplayOptions(guides = false, lines = true, markers = false, grid = true))
        .togglePanel()

    @Test
    fun 保存して戻すと同じ状態() {
        assertEquals(edited, EditorStateCodec.decode(EditorStateCodec.encode(edited)))
    }

    @Test
    fun 視点がまだ決まっていなければ無いまま() {
        val s = EditorState()
        assertNull(EditorStateCodec.decode(EditorStateCodec.encode(s))!!.camera)
        assertEquals(s, EditorStateCodec.decode(EditorStateCodec.encode(s)))
    }

    @Test
    fun 壊れていれば_null() {
        assertNull(EditorStateCodec.decode(""))
        assertNull(EditorStateCodec.decode("hello"))
        assertNull(EditorStateCodec.decode(EditorStateCodec.encode(edited).replace("camera ", "camera x ")))
    }

    @Test
    fun 選択中の部位が置かれていなければ肋骨を選ぶ() {
        val text = EditorStateCodec.encode(EditorState()).replace("selected rib", "selected head")
        assertEquals("rib", EditorStateCodec.decode(text)!!.selected)
    }
}
