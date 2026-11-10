package com.yswy.dessin.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.camera.ViewPreset
import com.yswy.dessin.core.editor.EditorState
import com.yswy.dessin.core.editor.EditorStateCodec
import com.yswy.dessin.core.editor.TouchGesture
import com.yswy.dessin.core.editor.applySlider
import com.yswy.dessin.core.mesh.fitBounds
import com.yswy.dessin.gl.DollRenderer
import com.yswy.dessin.gl.DollSurfaceView
import com.yswy.dessin.gl.PointerListener

/**
 * 画面の状態の保存。画面の作り直しやプロセスの終了のあとも同じ状態に戻す。
 * 読み込めなければ理由をログに残して初期状態から始める。
 */
private val EditorStateSaver = Saver<EditorState, String>(
    save = { EditorStateCodec.encode(it) },
    restore = { text ->
        EditorStateCodec.decode(text) ?: run {
            Log.w("DessinScreen", "画面の状態を復元できないので初期状態から始める")
            null
        }
    },
)

/** 画面全体。上が3D、下が操作パネル */
@Composable
fun DessinScreen(start: EditorState) {
    var state by rememberSaveable(stateSaver = EditorStateSaver) { mutableStateOf(start) }
    val actions = PanelActions(
        onSelect = { state = state.select(it) },
        onReset = { state = state.resetSelected() },
        onRemove = { state = state.removeSelected() },
        onTogglePanel = { state = state.togglePanel() },
        onSlider = { kind, value -> state = state.applySlider(kind, value) },
    )

    // 指の操作の途中の状態。画面にひとつ
    val gesture = remember { TouchGesture() }
    val pointers = object : PointerListener {
        override fun onDown(id: Int, x: Double, y: Double, time: Long, width: Double, height: Double) {
            state = gesture.down(state, id, x, y, time, width, height)
        }
        override fun onMove(id: Int, x: Double, y: Double, width: Double, height: Double) {
            state = gesture.move(state, id, x, y, width, height)
        }
        override fun onUp(id: Int, x: Double, y: Double, time: Long, width: Double, height: Double) {
            state = gesture.up(state, id, x, y, time, width, height)
        }
        override fun onCancel(id: Int) {
            state = gesture.cancel(state, id)
        }
    }

    Column(Modifier.fillMaxSize().background(Palette.Paper).statusBarsPadding()) {
        AndroidView(
            factory = { DollSurfaceView(it) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    // 最初の大きさが分かったら全体を表示する(Web版は起動時に fitView を1回行う)
                    if (state.camera == null && size.width > 0 && size.height > 0) {
                        val aspect = size.width.toDouble() / size.height
                        state = state.withCamera(fitBounds(state.skeleton)?.let { (lo, hi) -> OrbitCamera().fit(lo, hi, aspect) } ?: OrbitCamera())
                    }
                },
            update = { view ->
                view.pointerListener = pointers
                view.setScene(DollRenderer.Scene(state.skeleton, state.camera ?: OrbitCamera(), state.selected, state.options))
            },
        )
        ControlPanel(state, actions, Modifier.fillMaxWidth().background(Palette.Panel).navigationBarsPadding()) {
            // 視点の切り替え(全体を表示と表示の切替は E02-10)
            ViewPreset.entries.forEach { preset ->
                ToolChip(preset.label()) { state = state.withCamera((state.camera ?: OrbitCamera()).preset(preset)) }
            }
        }
    }
}

private fun ViewPreset.label() = when (this) {
    ViewPreset.FRONT -> "正面"
    ViewPreset.SIDE -> "横"
    ViewPreset.BACK -> "背面"
    ViewPreset.TOP -> "上"
}
