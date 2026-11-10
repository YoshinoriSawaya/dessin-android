package com.yswy.dessin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.editor.EditorState
import com.yswy.dessin.core.editor.ORB_RADIUS
import com.yswy.dessin.core.editor.ORB_SIZE
import com.yswy.dessin.core.editor.OrbGesture
import com.yswy.dessin.core.editor.OrbPlane
import com.yswy.dessin.core.editor.orbPicture
import com.yswy.dessin.core.math.Quat

private val RingColors = mapOf(
    OrbPlane.XZ to Color(0xFF2F6FB0),
    OrbPlane.YZ to Color(0xFF1F9D75),
    OrbPlane.XY to Color(0xFFB02A2A),
)

/**
 * 回転オーブ(Web版の #orb と drawOrb)。選択中の部位の向きを3つの輪と「前」「上」の印で示し、
 * ドラッグで回す(中央は傾け、外周はひねる)。計算は :core の OrbGesture・orbPicture。
 */
@Composable
fun OrbView(state: EditorState, onRotate: (Quat) -> Unit, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(state)
    val rotate by rememberUpdatedState(onRotate)
    val gesture = remember { OrbGesture() }
    val textMeasurer = rememberTextMeasurer()
    val camera = state.camera ?: OrbitCamera()
    val picture = orbPicture(camera.rotation, state.skeleton.worldTransforms().getValue(state.selected).rotation)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(ORB_SIZE.dp)
                .semantics { contentDescription = "回転球。ドラッグでひねりと傾きを操作します" }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val center = size.width / 2f
                        // オーブの中心を原点とする dp に直す
                        fun toDp(p: Offset) = Pair(((p.x - center) / density).toDouble(), ((p.y - center) / density).toDouble())
                        val (x0, y0) = toDp(down.position)
                        gesture.down(x0, y0)
                        down.consume()
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.positionChanged()) {
                                val (x, y) = toDp(change.position)
                                gesture.move(x, y, (current.camera ?: OrbitCamera()).rotation)?.let(rotate)
                                change.consume()
                            }
                        }
                        gesture.up()
                    }
                },
        ) {
            val s = size.width / ORB_SIZE.toFloat()
            val c = size.width / 2f
            val r = ORB_RADIUS.toFloat() * s
            val one = 1.dp.toPx()
            drawCircle(
                Color(0xFFB9B4A8), radius = r + 4 * s, center = Offset(c, c),
                style = Stroke(width = one, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2 * s, 3 * s))),
            )
            drawCircle(
                Brush.radialGradient(listOf(Color.White, Color(0xFFD6D2C8)), center = Offset(c - r * 0.35f, c - r * 0.4f), radius = r),
                radius = r, center = Offset(c, c),
            )
            drawCircle(Color(0xFF8F8A7E), radius = r, center = Offset(c, c), style = Stroke(width = one))
            fun at(x: Double, y: Double) = Offset(c + r * x.toFloat(), c - r * y.toFloat())
            picture.rings.forEach { ring ->
                val color = RingColors.getValue(ring.plane)
                ring.points.zipWithNext { a, b ->
                    val front = a.z + b.z >= 0
                    drawLine(color.copy(alpha = if (front) 0.95f else 0.22f), at(a.x, a.y), at(b.x, b.y), strokeWidth = if (front) 1.6f * one else one)
                }
            }
            listOf(Triple(picture.front, Palette.Accent, "前"), Triple(picture.up, Palette.Ink, "上")).forEach { (p, color, label) ->
                val alpha = if (p.z >= 0) 1f else 0.3f
                val pos = at(p.x, p.y)
                drawCircle(color.copy(alpha = alpha), radius = 6 * s, center = pos)
                val text = textMeasurer.measure(label, TextStyle(color = Color.White.copy(alpha = alpha), fontSize = 9.sp))
                drawText(text, topLeft = Offset(pos.x - text.size.width / 2f, pos.y - text.size.height / 2f))
            }
        }
        Text("球をドラッグ", fontSize = 10.sp, color = Palette.Muted)
    }
}
