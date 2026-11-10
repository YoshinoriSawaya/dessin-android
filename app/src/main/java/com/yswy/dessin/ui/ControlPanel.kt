package com.yswy.dessin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yswy.dessin.core.editor.ChipLook
import com.yswy.dessin.core.editor.EditorState
import com.yswy.dessin.core.editor.PartChip
import com.yswy.dessin.core.editor.Slider
import com.yswy.dessin.core.editor.SliderKind
import com.yswy.dessin.core.editor.sliders
import com.yswy.dessin.core.math.Quat
import kotlin.math.roundToInt

/** 色は Web版の CSS の値 */
object Palette {
    val Paper = Color(0xFFE6E4DF)
    val Panel = Color(0xFFF7F6F3)
    val Ink = Color(0xFF23221E)
    val Muted = Color(0xFF6B675E)
    val Line = Color(0xFFD3D0C7)
    val Accent = Color(0xFFD9481F)
}

/** 下の操作パネルの操作 */
class PanelActions(
    val onSelect: (String) -> Unit,
    val onReset: () -> Unit,
    val onRemove: () -> Unit,
    val onTogglePanel: () -> Unit,
    val onSlider: (SliderKind, Int) -> Unit,
    val onOrb: (Quat) -> Unit,
)

/**
 * 下の操作パネル(Web版の #panel)。選択中の部位、向きを戻す・外す・折りたたみ、説明、スライダー、部位チップ。
 * 回転オーブはスライダーの右に置く。tools には「表示」の段に並べるボタンを渡す(E02-10 で整える)。
 */
@Composable
fun ControlPanel(state: EditorState, actions: PanelActions, modifier: Modifier = Modifier, tools: @Composable () -> Unit) {
    Column(modifier.background(Palette.Panel).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("選択中の部位", fontSize = 11.sp, color = Palette.Muted)
                Text(state.selectionLabel, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GhostButton("向きを戻す", enabled = true, onClick = actions.onReset)
            GhostButton("外す", enabled = state.canRemove, onClick = actions.onRemove)
            FoldButton(open = state.panelOpen, onClick = actions.onTogglePanel)
        }
        if (state.panelOpen) {
            Text(state.selectionInfo, fontSize = 12.sp, color = Palette.Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
            // スライダーと回転オーブを横に並べる(Web版の .ctl)
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    state.sliders.forEach { SliderRow(it, actions.onSlider) }
                }
                OrbView(state, actions.onOrb)
            }
            Group("部位") {
                state.partChips.forEachIndexed { i, group ->
                    if (i > 0) Separator()
                    group.forEach { chip -> PartChipView(chip) { actions.onSelect(chip.id) } }
                }
            }
            Group("表示") { tools() }
        }
    }
}

/** スライダー1行(Web版の .row: ラベル・スライダー・値) */
// 溝を細くする Slider の track 指定は Material3 で実験的な API(変わったら標準の見た目に戻せばよい)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderRow(slider: Slider, onChange: (SliderKind, Int) -> Unit) {
    val colors = SliderDefaults.colors(thumbColor = Palette.Accent, activeTrackColor = Palette.Accent, inactiveTrackColor = Palette.Line)
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(slider.label, fontSize = 13.sp, color = Palette.Ink, maxLines = 1, modifier = Modifier.width(96.dp))
        androidx.compose.material3.Slider(
            value = slider.value.toFloat(),
            onValueChange = { v -> val i = v.roundToInt(); if (i != slider.value) onChange(slider.kind, i) },
            // 範囲が0のスライダーは使えないが、Slider は幅0の範囲を受け付けないので1だけ広げて表示する
            valueRange = slider.min.toFloat()..maxOf(slider.max, slider.min + 1).toFloat(),
            enabled = slider.enabled,
            colors = colors,
            modifier = Modifier.weight(1f).height(28.dp).semantics { contentDescription = slider.label },
            // Web版の細い range に合わせて、溝を細くし、つまみとの隙間と終点の印をなくす
            track = { st ->
                SliderDefaults.Track(
                    sliderState = st,
                    modifier = Modifier.height(4.dp),
                    enabled = slider.enabled,
                    colors = colors,
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                )
            },
        )
        Text("${slider.value}${slider.unit}", fontSize = 13.sp, color = Palette.Muted, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(44.dp))
    }
}

@Composable
private fun Group(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.width(40.dp))
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

@Composable
private fun Separator() {
    Box(Modifier.padding(vertical = 6.dp, horizontal = 2.dp).width(1.dp).height(22.dp).background(Palette.Line))
}

/** 部位チップ。選択中は塗り、置いた部位は濃い枠、置ける部位はアクセント色の点線、置けない部位は薄く */
@Composable
private fun PartChipView(chip: PartChip, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val base = Modifier
        .heightIn(min = 34.dp)
        .semantics { selected = chip.look == ChipLook.SELECTED }
        .alpha(if (chip.enabled) 1f else 0.35f)
    val styled = when (chip.look) {
        ChipLook.SELECTED -> base.background(Palette.Ink, shape).border(1.dp, Palette.Ink, shape)
        ChipLook.PLACED -> base.background(Color.White, shape).border(1.dp, Palette.Ink, shape)
        ChipLook.AVAILABLE -> base.background(Color.White, shape).drawBehind {
            val r = size.height / 2
            drawRoundRect(
                Palette.Accent,
                cornerRadius = CornerRadius(r, r),
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
            )
        }
        ChipLook.DISABLED -> base.background(Color.White, shape).border(1.dp, Palette.Line, shape)
    }
    Box(
        styled.clickable(enabled = chip.enabled, role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(chip.label, fontSize = 13.sp, color = if (chip.look == ChipLook.SELECTED) Color.White else Palette.Ink, maxLines = 1)
    }
}

/**
 * 「表示」の段に並べるチップ。pressed が null なら押すだけ、true・false なら入/切で、入のときは塗る
 * (Web版の toolChip と aria-pressed)
 */
@Composable
fun ToolChip(label: String, pressed: Boolean? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val on = pressed == true
    Box(
        Modifier.heightIn(min = 34.dp)
            .semantics { if (pressed != null) selected = pressed }
            .background(if (on) Palette.Ink else Color.White, shape).border(1.dp, if (on) Palette.Ink else Palette.Line, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 13.sp, color = if (on) Color.White else Palette.Ink, maxLines = 1) }
}

@Composable
private fun GhostButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.heightIn(min = 34.dp).alpha(if (enabled) 1f else 0.35f).border(1.dp, Palette.Line, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 13.sp, color = Palette.Ink, maxLines = 1) }
}

@Composable
private fun FoldButton(open: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.size(34.dp).border(1.dp, Palette.Line, shape)
            .semantics { contentDescription = if (open) "パネルを折りたたむ" else "パネルを開く" }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("▾", fontSize = 14.sp, color = Palette.Ink, modifier = Modifier.rotate(if (open) 0f else 180f)) }
}
