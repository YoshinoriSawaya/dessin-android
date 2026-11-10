package com.yswy.dessin.gl

import android.content.Context
import android.opengl.GLSurfaceView
import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.skeleton.Skeleton

/** OpenGL ES 3.0 で人形を描く View。描き直すのは内容が変わったときだけ(Web版の dirty と同じ) */
class DollSurfaceView(context: Context) : GLSurfaceView(context) {
    private val renderer = DollRenderer()

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setScene(skeleton: Skeleton, camera: OrbitCamera) {
        renderer.scene = DollRenderer.Scene(skeleton, camera)
        requestRender()
    }
}
