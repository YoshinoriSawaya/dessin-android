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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yswy.dessin.core.editor.ChipLook
import com.yswy.dessin.core.editor.EditorState
import com.yswy.dessin.core.editor.PartChip

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
)

/**
 * 下の操作パネル(Web版の #panel)。選択中の部位、向きを戻す・外す・折りたたみ、説明、部位チップ。
 * tools には「表示」の段に並べるボタンを渡す(E02-10 で整える)。スライダーとオーブは E02-08・E02-09。
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

/** 「表示」の段などに並べるチップ(押すだけのもの) */
@Composable
fun ToolChip(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.heightIn(min = 34.dp).background(Color.White, shape).border(1.dp, Palette.Line, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 13.sp, color = Palette.Ink, maxLines = 1) }
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
