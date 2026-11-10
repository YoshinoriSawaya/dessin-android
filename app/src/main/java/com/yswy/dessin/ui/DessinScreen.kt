package com.yswy.dessin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yswy.dessin.StartState
import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.camera.ViewPreset
import com.yswy.dessin.core.mesh.fitBounds
import com.yswy.dessin.core.render.DisplayOptions
import com.yswy.dessin.gl.DollRenderer
import com.yswy.dessin.gl.DollSurfaceView

private val Paper = Color(0xFFE6E4DF)
private val Panel = Color(0xFFF7F6F3)

/**
 * 画面全体。上が3D、下が操作パネル。
 * いまのパネルは視点の切り替えだけの仮のもの(E02-06・E02-10 で作り直す)。
 */
@Composable
fun DessinScreen(start: StartState) {
    val skeleton = start.skeleton
    val selected = start.selected
    val options = remember { DisplayOptions() }
    // 最初の大きさが分かったら全体を表示する(Web版は起動時に fitView を1回行う)
    var camera by remember { mutableStateOf<OrbitCamera?>(null) }

    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding()) {
        AndroidView(
            factory = { DollSurfaceView(it) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    if (camera == null && size.width > 0 && size.height > 0) {
                        val aspect = size.width.toDouble() / size.height
                        camera = fitBounds(skeleton)?.let { (lo, hi) -> OrbitCamera().fit(lo, hi, aspect) } ?: OrbitCamera()
                    }
                },
            update = { view -> view.setScene(DollRenderer.Scene(skeleton, camera ?: OrbitCamera(), selected, options)) },
        )
        Row(
            Modifier.fillMaxWidth().background(Panel).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ViewPreset.entries.forEach { preset ->
                OutlinedButton(onClick = { camera = (camera ?: OrbitCamera()).preset(preset) }) { Text(preset.label()) }
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
