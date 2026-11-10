package com.yswy.dessin.gl

import android.content.Context
import android.opengl.GLSurfaceView

/** OpenGL ES 3.0 で人形を描く View。描き直すのは内容が変わったときだけ(Web版の dirty と同じ) */
class DollSurfaceView(context: Context) : GLSurfaceView(context) {
    private val renderer = DollRenderer()

    init {
        setEGLContextClientVersion(3)
        // 奥行きは24ビット(16ビットだと関節の玉と面の境目がギザギザになる)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setScene(scene: DollRenderer.Scene) {
        renderer.scene = scene
        requestRender()
    }
}
