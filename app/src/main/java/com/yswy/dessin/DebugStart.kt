package com.yswy.dessin

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.Context
import android.util.Log
import com.yswy.dessin.core.editor.EditorState
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.pose.PoseCodec
import com.yswy.dessin.core.pose.PoseDecodeResult
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.Skeleton

private const val TAG = "DessinDebug"

/**
 * デバッグビルドだけ、起動時の状態を adb から指定できる(エミュレータでの見た目の確認用)。
 * - `--es demo all` 全部位を置き、腕と脚を少し動かした姿勢
 * - `--es pose "<PoseCodec の文字列。改行の代わりに ;>"` 保存形式で指定
 * - `--es select <部位id>` 選択する部位(置けるなら置く)
 * 例: `adb shell am start -n com.yswy.dessin/.MainActivity --es demo all --es select upperArmL`
 */
fun debugStartState(context: Context, intent: Intent?): EditorState {
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!debuggable || intent == null) return EditorState()
    var skeleton = when {
        intent.getStringExtra("demo") == "all" -> demoPose()
        intent.getStringExtra("pose") != null -> when (val r = PoseCodec.decode(intent.getStringExtra("pose")!!.replace(';', '\n'))) {
            is PoseDecodeResult.Ok -> r.skeleton
            is PoseDecodeResult.Error -> {
                Log.w(TAG, "起動時の姿勢を読み込めない: ${r.reason}")
                Skeleton.initial()
            }
        }
        else -> Skeleton.initial()
    }
    val select = intent.getStringExtra("select")?.takeIf { id -> PartSpecs.all.any { it.id == id } } ?: "rib"
    skeleton = skeleton.place(select)
    return EditorState(skeleton = skeleton, selected = if (skeleton.isPlaced(select)) select else "rib")
}

/** 全部位を置き、腕と脚を少し動かした姿勢 */
private fun demoPose(): Skeleton {
    var s = PartSpecs.all.fold(Skeleton.initial()) { acc, p -> acc.place(p.id) }
    s = s.aim("upperArmL", Vec3(0.3, -0.6, 0.7).normalized())
    s = s.aim("forearmL", Vec3(0.0, 0.4, 1.0).normalized())
    s = s.aim("upperArmR", Vec3(-0.5, -0.8, -0.2).normalized())
    s = s.aim("thighR", Vec3(0.0, -0.7, 0.7).normalized())
    s = s.aim("shinR", Vec3(0.0, -1.0, -0.4).normalized())
    s = s.aim("neck", Vec3(0.1, 1.0, 0.2).normalized())
    return s
}
