package com.yswy.dessin.core.editor

import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** E12-02: マウスホイールで拡大縮小する。上へ回すと近づき、下へ回すと離れる */
class WheelZoomTest {
    private val camera = OrbitCamera(target = Vec3(0.0, 1.1, 0.0), yaw = 0.3, pitch = 0.1, distance = 3.0)
    private val start = EditorState(camera = camera).select("neck")

    @Test
    fun 上へ回すと近づき_下へ回すと離れる() {
        assertTrue(start.wheelZoom(1.0).camera!!.distance < 3.0)
        assertTrue(start.wheelZoom(-1.0).camera!!.distance > 3.0)
    }

    @Test
    fun 一刻みで1割_上下で元に戻る() {
        assertEquals(3.0 * 0.9, start.wheelZoom(1.0).camera!!.distance, 1e-9)
        assertEquals(3.0, start.wheelZoom(1.0).wheelZoom(-1.0).camera!!.distance, 1e-9)
        // 細かい刻み(タッチパッドなど)は割合で効く
        assertEquals(3.0 * Math.pow(0.9, 0.5), start.wheelZoom(0.5).camera!!.distance, 1e-9)
    }

    @Test
    fun 視点の向きと注視点_ポーズは変えない_モードによらず効く() {
        val z = start.wheelZoom(2.0)
        assertEquals(camera.yaw, z.camera!!.yaw, 0.0)
        assertEquals(camera.target, z.camera!!.target)
        assertEquals(start.skeleton, z.skeleton)
        assertTrue(start.toggleCameraOnly().wheelZoom(1.0).camera!!.distance < 3.0)
    }

    @Test
    fun 距離の範囲に収める_視点が決まっていなければ既定の視点から() {
        assertEquals(OrbitCamera.DISTANCE_MIN, start.wheelZoom(100.0).camera!!.distance, 1e-9)
        assertEquals(OrbitCamera().distance * 0.9, EditorState().wheelZoom(1.0).camera!!.distance, 1e-9)
    }
}
