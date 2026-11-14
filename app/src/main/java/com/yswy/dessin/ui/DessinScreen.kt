package com.yswy.dessin.ui

import com.yswy.dessin.core.editor.setAnchor
import com.yswy.dessin.core.editor.setSex
import com.yswy.dessin.core.editor.setHandShape
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.camera.ViewPreset
import com.yswy.dessin.core.editor.Display
import com.yswy.dessin.core.editor.EditorState
import com.yswy.dessin.core.editor.EditorStateCodec
import com.yswy.dessin.core.editor.TouchGesture
import com.yswy.dessin.core.editor.applyOrb
import com.yswy.dessin.core.editor.applySlider
import com.yswy.dessin.core.editor.fitView
import com.yswy.dessin.core.editor.isOn
import com.yswy.dessin.core.editor.loadBody
import com.yswy.dessin.core.editor.loadPose
import com.yswy.dessin.core.editor.preset
import com.yswy.dessin.core.editor.toggle
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

private const val HINT = "まず胸をドラッグして置き、大きさや傾きを決める。下の部位から順に選び、緑の範囲の中をドラッグして向きを決める。"

/** 画面全体。上が3D(ヒントと凡例を重ねる)、下が操作パネル */
@Composable
fun DessinScreen(start: EditorState) {
    var state by rememberSaveable(stateSaver = EditorStateSaver) { mutableStateOf(start) }
    // ヒントは 3D に最初に触ったら消す。画面が低ければ最初から出さない(Web版の max-height:620px)
    var hintVisible by rememberSaveable { mutableStateOf(true) }
    val tallEnough = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } > 620.dp
    // 全体を表示に使う 3D の画面の縦横比
    var aspect by remember { mutableDoubleStateOf(1.0) }
    // 名前を付けて保存したポーズと体型(体型はポーズと別に保存する。E07-03)
    val context = LocalContext.current
    val poses = remember { Shelf.poses(context) }
    val bodies = remember { Shelf.bodies(context) }
    var dialog by remember { mutableStateOf<PoseDialog?>(null) }

    val actions = PanelActions(
        onSelect = { state = state.select(it) },
        onReset = { state = state.resetSelected() },
        onRemove = { state = state.removeSelected() },
        onTogglePanel = { state = state.togglePanel() },
        onSlider = { kind, value -> state = state.applySlider(kind, value) },
        onOrb = { q -> state = state.applyOrb(q) },
        onHandShape = { shape -> state = state.setHandShape(shape) },
        onSex = { sex -> state = state.setSex(sex) },
        onAnchor = { anchor -> state = state.setAnchor(anchor) },
    )

    // 指の操作の途中の状態。画面にひとつ
    val gesture = remember { TouchGesture() }
    val pointers = object : PointerListener {
        override fun onDown(id: Int, x: Double, y: Double, time: Long, width: Double, height: Double) {
            hintVisible = false
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
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { DollSurfaceView(it) },
                modifier = Modifier.fillMaxSize().onSizeChanged { size ->
                    if (size.width > 0 && size.height > 0) {
                        aspect = size.width.toDouble() / size.height
                        // 最初の大きさが分かったら全体を表示する(Web版は起動時に fitView を1回行う)
                        if (state.camera == null) state = state.fitView(aspect)
                    }
                },
                update = { view ->
                    view.pointerListener = pointers
                    view.setScene(DollRenderer.Scene(state.skeleton, state.camera ?: OrbitCamera(), state.selected, state.options))
                },
            )
            if (hintVisible && tallEnough) {
                // 右上の「カメラ」のボタンと重ならないよう、右に余白を取る
                Text(HINT, fontSize = 12.sp, lineHeight = 19.sp, color = Palette.Muted, modifier = Modifier.padding(start = 12.dp, end = 104.dp, top = 10.dp))
            }
            if (state.options.guides) Legend(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 8.dp))
            // カメラだけを操作するモード(E12)。押している間は部位を動かさず、視点だけを動かす
            Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp)) {
                ToolChip("カメラ", pressed = state.cameraOnly) { state = state.toggleCameraOnly() }
            }
        }
        ControlPanel(
            state, actions, Modifier.fillMaxWidth().background(Palette.Panel).navigationBarsPadding(),
            tools = {
                ToolChip("全体を表示") { state = state.fitView(aspect) }
                ViewPreset.entries.forEach { preset -> ToolChip(preset.label()) { state = state.preset(preset) } }
                Display.entries.forEach { d -> ToolChip(d.label(), pressed = state.isOn(d)) { state = state.toggle(d) } }
            },
            saveTools = {
                ToolChip("名前を付けて保存") { dialog = PoseDialog.Save(SaveKind.POSE, poses.suggestName()) }
                ToolChip("保存したポーズ") { poses.list()?.let { dialog = PoseDialog.List(SaveKind.POSE, it) } }
            },
            bodyTools = {
                ToolChip("名前を付けて保存") { dialog = PoseDialog.Save(SaveKind.BODY, bodies.suggestName()) }
                ToolChip("保存した体型") { bodies.list()?.let { dialog = PoseDialog.List(SaveKind.BODY, it) } }
            },
        )
    }

    when (val d = dialog) {
        is PoseDialog.Save -> SavePoseDialog(
            suggestedName = d.suggestedName,
            onSave = { name ->
                val saved = when (d.kind) {
                    SaveKind.POSE -> poses.save(name, state.skeleton)
                    SaveKind.BODY -> bodies.save(name, state.skeleton.body)
                }
                if (saved) dialog = null
            },
            onDismiss = { dialog = null },
        )
        is PoseDialog.List -> PoseListDialog(
            label = d.kind.label,
            items = d.items,
            onLoad = { id ->
                when (d.kind) {
                    SaveKind.POSE -> poses.load(id)?.let { pose -> state = state.loadPose(pose, aspect); dialog = null }
                    SaveKind.BODY -> bodies.load(id)?.let { body -> state = state.loadBody(body); dialog = null }
                }
            },
            onDelete = { id ->
                if (d.kind == SaveKind.POSE) {
                    poses.delete(id)
                    dialog = poses.list()?.let { PoseDialog.List(d.kind, it) }
                } else {
                    bodies.delete(id)
                    dialog = bodies.list()?.let { PoseDialog.List(d.kind, it) }
                }
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

/** 凡例(Web版の #legend): 可動域・ドラッグする点・次に置ける部位 */
@Composable
private fun Legend(modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem("可動域") { Box(Modifier.size(9.dp).background(Color(0xFF1F9D75).copy(alpha = 0.7f), CircleShape)) }
        LegendItem("ドラッグする点") { Box(Modifier.size(9.dp).background(Palette.Accent, CircleShape)) }
        LegendItem("次に置ける部位") { Box(Modifier.size(9.dp).border(1.dp, Palette.Muted, CircleShape)) }
    }
}

@Composable
private fun LegendItem(label: String, dot: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        dot()
        Text(label, fontSize = 11.sp, color = Palette.Muted)
    }
}

private fun ViewPreset.label() = when (this) {
    ViewPreset.FRONT -> "正面"
    ViewPreset.SIDE -> "横"
    ViewPreset.BACK -> "背面"
    ViewPreset.TOP -> "上"
}

private fun Display.label() = when (this) {
    Display.GUIDES -> "ガイド"
    Display.LINES -> "正中線"
    Display.MARKERS -> "関節の玉"
    Display.GRID -> "格子"
    Display.SHADOWS -> "影"
}
