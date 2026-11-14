package com.yswy.dessin.core.editor

import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.pick.pickAtPress
import com.yswy.dessin.core.skeleton.PartSpecs
import kotlin.math.hypot

/**
 * 3D の画面での指の操作(Web版の pointerdown・pointermove・endPtr)。端末のイベントに依存しない。
 * 座標は画面の左上を原点とする dp(Web版の CSS px に当たる)。width・height は 3D の画面の大きさ。
 *
 * - 1本指: 選択中の部位を押していれば向きを決める(胸なら動かす)。それ以外は視点を回す。6dp 動くまでは動かさない
 * - 2本指: ピンチで距離を変える。ピンチのあとに残った指では何もしない
 * - タップ: 押した部位を選ぶ。選択中の部位の緑の範囲を 420ms 以内・32dp 以内に2回タップすると、その点へ向ける
 * - カメラだけを操作するモード(E12): 部位を拾わない(1本指は視点を回すだけ、タップでは選ばない)。2本指はピンチに加えて、
 *   2本の指の真ん中の動きで平行移動する
 *
 * 指ごとの途中の状態を持つので、画面ごとに1つ作って使い続ける。
 */
class TouchGesture {
    private class Point(var x: Double, var y: Double)
    private class Tap(val time: Long, val x: Double, val y: Double, val ok: Boolean)
    private enum class Mode { PLACE, ORBIT, PINCH }

    private val pointers = LinkedHashMap<Int, Point>()
    private var mode: Mode? = null
    private var startX = 0.0
    private var startY = 0.0
    private var moved = false
    private var pinchDistance = 0.0
    private var pinchMidX = 0.0
    private var pinchMidY = 0.0
    private var downPart: String? = null
    private var downRom: Vec3? = null
    private var lastTap: Tap? = null

    fun down(state: EditorState, id: Int, x: Double, y: Double, time: Long, width: Double, height: Double): EditorState {
        pointers[id] = Point(x, y)
        if (pointers.size == 1) {
            startX = x
            startY = y
            moved = false
            if (state.cameraOnly) {
                downPart = null
                downRom = null
            } else {
                val press = pickAtPress(state.skeleton, state.selected, state.options, camera(state).ray(x, y, width, height))
                downPart = press.part
                downRom = press.romPoint
            }
            mode = if (downPart != null && downPart == state.selected) Mode.PLACE else Mode.ORBIT
        } else if (pointers.size == 2) {
            mode = Mode.PINCH
            moved = true
            pinchDistance = pointerDistance()
            pointerMid().let { (mx, my) -> pinchMidX = mx; pinchMidY = my }
        }
        return state
    }

    fun move(state: EditorState, id: Int, x: Double, y: Double, width: Double, height: Double): EditorState {
        val p = pointers[id] ?: return state
        val dx = x - p.x
        val dy = y - p.y
        p.x = x
        p.y = y
        val cam = camera(state)
        if (mode == Mode.PINCH && pointers.size >= 2) {
            val d = pointerDistance()
            var c = if (pinchDistance > 0 && d > 0) cam.pinch(pinchDistance, d) else cam
            pinchDistance = d
            val (mx, my) = pointerMid()
            if (state.cameraOnly) c = c.pan(mx - pinchMidX, my - pinchMidY, height)
            pinchMidX = mx
            pinchMidY = my
            return if (c == cam) state else state.withCamera(c)
        }
        if (!moved && hypot(x - startX, y - startY) < MOVE_THRESHOLD) return state
        moved = true
        return when (mode) {
            Mode.PLACE -> {
                val skeleton = state.skeleton
                if (PartSpecs[state.selected].isRoot) {
                    val pos = skeleton.root.position
                    state.copy(skeleton = skeleton.withRoot(skeleton.root.copy(position = pos + cam.screenDelta(pos, dx, dy, height))))
                } else {
                    state.copy(skeleton = skeleton.drag(state.selected, cam.ray(x, y, width, height)))
                }
            }
            Mode.ORBIT -> state.withCamera(cam.orbit(dx, dy))
            else -> state
        }
    }

    fun up(state: EditorState, id: Int, x: Double, y: Double, time: Long, width: Double, height: Double): EditorState =
        end(state, id, x, y, time, released = true)

    /** 指が取り消された(タップにはしない) */
    fun cancel(state: EditorState, id: Int): EditorState = end(state, id, 0.0, 0.0, 0, released = false)

    private fun end(state: EditorState, id: Int, x: Double, y: Double, time: Long, released: Boolean): EditorState {
        if (pointers.remove(id) == null) return state
        var next = state
        if (released && !moved && pointers.isEmpty() && mode != Mode.PINCH) {
            val changes = downPart != null && downPart != state.selected
            val last = lastTap
            val rom = downRom
            if (last != null && last.ok && rom != null && time - last.time < DOUBLE_TAP_MS && hypot(x - last.x, y - last.y) < DOUBLE_TAP_DISTANCE) {
                next = next.copy(skeleton = next.skeleton.aimAtPoint(state.selected, rom))
                lastTap = null
            } else {
                lastTap = Tap(time, x, y, ok = !changes && rom != null)
                downPart?.let { next = next.select(it) }
            }
        }
        if (pointers.size < 2 && mode == Mode.PINCH) mode = null
        if (pointers.isEmpty()) mode = null
        return next
    }

    /** 最初の2本の指の真ん中 */
    private fun pointerMid(): Pair<Double, Double> {
        val (a, b) = pointers.values.take(2)
        return Pair((a.x + b.x) / 2, (a.y + b.y) / 2)
    }

    /** 最初の2本の指の間隔 */
    private fun pointerDistance(): Double {
        val (a, b) = pointers.values.take(2)
        return hypot(a.x - b.x, a.y - b.y)
    }

    private fun camera(state: EditorState) = state.camera ?: OrbitCamera()

    companion object {
        /** これだけ動くまではドラッグとみなさない(dp) */
        const val MOVE_THRESHOLD = 6.0
        const val DOUBLE_TAP_MS = 420L
        const val DOUBLE_TAP_DISTANCE = 32.0
    }
}
