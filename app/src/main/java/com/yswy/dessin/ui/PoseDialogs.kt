package com.yswy.dessin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yswy.dessin.core.editor.PresetPose
import com.yswy.dessin.core.pose.PoseLibrary
import com.yswy.dessin.core.pose.SavedPoseSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 名前を付けて保存する。名前の欄には候補(「場面 N」「体型 N」)を入れておく */
@Composable
fun SavePoseDialog(suggestedName: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(suggestedName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("名前を付けて保存") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(PoseLibrary.MAX_NAME_LENGTH) },
                singleLine = true,
                label = { Text("名前") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル", color = Palette.Ink) } },
    )
}

private val DateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

/**
 * 保存した場面・体型の一覧。読み込み・削除(削除は確認してから)。label は「場面」か「体型」。
 * onToggleDefault を渡すと(体型の一覧。E07-04)、各行に「既定にする」を置き、defaultId の行に「既定」の印を付ける。
 * 既定の行のボタンは「既定を外す」になる。
 * ボタンの色: 主な操作(読み込む・確認の削除)はアクセント、閉じる・キャンセルは墨、一覧の削除と既定の切り替えは淡い色
 */
@Composable
fun PoseListDialog(
    label: String,
    items: List<SavedPoseSummary>,
    onLoad: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
    defaultId: String? = null,
    onToggleDefault: ((SavedPoseSummary) -> Unit)? = null,
) {
    var deleting by remember { mutableStateOf<SavedPoseSummary?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存した$label") },
        text = {
            if (items.isEmpty()) {
                Text("保存した${label}はありません", color = Palette.Muted)
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(items, key = { it.id }) { item ->
                        val info = @Composable {
                            Text(item.name, fontSize = 15.sp, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    DateFormat.format(Instant.ofEpochMilli(item.savedAt).atZone(ZoneId.systemDefault())),
                                    fontSize = 12.sp, color = Palette.Muted, maxLines = 1,
                                )
                                if (item.id == defaultId) Text("既定", fontSize = 12.sp, color = Palette.Accent, maxLines = 1)
                            }
                        }
                        val load = @Composable { TextButton(onClick = { onLoad(item.id) }) { Text("読み込む") } }
                        val delete = @Composable { TextButton(onClick = { deleting = item }) { Text("削除", color = Palette.Muted) } }
                        if (onToggleDefault == null) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { info() }
                                Row { load(); delete() }
                            }
                        } else {
                            // ボタンが3つになるので、名前と日時の下の行に並べる
                            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                info()
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    load()
                                    TextButton(onClick = { onToggleDefault(item) }) {
                                        Text(if (item.id == defaultId) "既定を外す" else "既定にする", color = Palette.Muted)
                                    }
                                    delete()
                                }
                            }
                        }
                        HorizontalDivider(color = Palette.Line)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる", color = Palette.Ink) } },
    )
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("削除しますか?") },
            text = { Text("「${item.name}」を削除します。元に戻せません。") },
            confirmButton = { TextButton(onClick = { onDelete(item.id); deleting = null }) { Text("削除", color = Palette.Accent) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("キャンセル", color = Palette.Ink) } },
        )
    }
}

/**
 * プリセットのポーズの一覧(E19-01)。選ぶと操作中の人形に当てて閉じる。
 * 当てるとポーズが差し替わるが、Undo で戻せるので確認は出さない
 */
@Composable
fun PresetPoseDialog(onApply: (PresetPose) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("プリセットのポーズ") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(PresetPose.entries, key = { it.name }) { p ->
                    TextButton(onClick = { onApply(p) }, modifier = Modifier.fillMaxWidth()) {
                        Text(p.label, fontSize = 15.sp, color = Palette.Ink, modifier = Modifier.fillMaxWidth())
                    }
                    HorizontalDivider(color = Palette.Line)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる", color = Palette.Ink) } },
    )
}
