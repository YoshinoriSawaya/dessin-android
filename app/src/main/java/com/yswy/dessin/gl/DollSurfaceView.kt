package com.yswy.dessin.gl

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.InputDevice
import android.view.MotionEvent

/** 3D の画面の指のイベント。座標は View の左上を原点とする dp、width・height は View の大きさ(dp) */
interface PointerListener {
    fun onDown(id: Int, x: Double, y: Double, time: Long, width: Double, height: Double)
    fun onMove(id: Int, x: Double, y: Double, width: Double, height: Double)
    fun onUp(id: Int, x: Double, y: Double, time: Long, width: Double, height: Double)
    fun onCancel(id: Int)

    /** マウスホイール(E12-02)。steps は上へ回した量(一刻みで 1) */
    fun onWheel(steps: Double)
}

/** OpenGL ES 3.0 で人形を描く View。描き直すのは内容が変わったときだけ(Web版の dirty と同じ) */
@SuppressLint("ViewConstructor")
class DollSurfaceView(context: Context) : GLSurfaceView(context) {
    private val renderer = DollRenderer()
    var pointerListener: PointerListener? = null

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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val l = pointerListener ?: return false
        // Web版の CSS px に合わせて dp で渡す(回転の速さや、ドラッグとみなす距離が同じ感覚になる)
        val density = resources.displayMetrics.density.toDouble()
        val w = width / density
        val h = height / density
        fun x(i: Int) = event.getX(i) / density
        fun y(i: Int) = event.getY(i) / density
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                l.onDown(event.getPointerId(i), x(i), y(i), event.eventTime, w, h)
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) l.onMove(event.getPointerId(i), x(i), y(i), w, h)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = event.actionIndex
                l.onUp(event.getPointerId(i), x(i), y(i), event.eventTime, w, h)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
            MotionEvent.ACTION_CANCEL -> for (i in 0 until event.pointerCount) l.onCancel(event.getPointerId(i))
        }
        return true
    }

    /** マウスホイールを回したとき(E12-02) */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val l = pointerListener
        if (l != null && event.isFromSource(InputDevice.SOURCE_CLASS_POINTER) && event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val steps = event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble()
            if (steps != 0.0) {
                l.onWheel(steps)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    // タップの操作は onTouchEvent で扱う。アクセシビリティのクリックは受けるだけ
    override fun performClick(): Boolean = super.performClick()
}
