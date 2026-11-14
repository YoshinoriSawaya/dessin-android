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
import com.yswy.dessin.core.pose.PoseLibrary
import com.yswy.dessin.core.pose.SavedPoseSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 名前を付けて保存する。名前の欄には候補(「ポーズ N」「体型 N」)を入れておく */
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
 * 保存したポーズ・体型の一覧。読み込み・削除(削除は確認してから)。label は「ポーズ」か「体型」。
 * ボタンの色: 主な操作(読み込む・確認の削除)はアクセント、閉じる・キャンセルは墨、一覧の削除は淡い色
 */
@Composable
fun PoseListDialog(label: String, items: List<SavedPoseSummary>, onLoad: (String) -> Unit, onDelete: (String) -> Unit, onDismiss: () -> Unit) {
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
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontSize = 15.sp, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    DateFormat.format(Instant.ofEpochMilli(item.savedAt).atZone(ZoneId.systemDefault())),
                                    fontSize = 12.sp, color = Palette.Muted,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                TextButton(onClick = { onLoad(item.id) }) { Text("読み込む") }
                                TextButton(onClick = { deleting = item }) { Text("削除", color = Palette.Muted) }
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
