package com.yswy.dessin.core.editor

import com.yswy.dessin.core.skeleton.Anchor
import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.pose.BodyCodec
import com.yswy.dessin.core.pose.PoseCodec
import com.yswy.dessin.core.pose.PoseDecodeResult
import com.yswy.dessin.core.render.DisplayOptions

/**
 * 編集画面の状態と文字列の変換。画面の作り直しやプロセスの終了で状態を失わないために使う(利用者の保存とは別)。
 *
 * ```
 * dessin-editor 1
 * selected <部位id>
 * camera <注視点xyz> <yaw> <pitch> <距離>   ← 視点が決まっていなければ無い
 * options <ガイド> <正中線> <関節の玉> <格子> <影> <ほかを淡く>   ← 0 か 1。影(E11)・ほかを淡く(E13-05)が無い古い状態はオン
 * panel <0|1>
 * cameraonly <0|1>              ← E12。無ければ 0
 * bodytype <BodyCodec の1行>   ← E07-02。無ければポーズの体型のまま
 * anchor <rib|pelvis>          ← E10-01。無ければ胸
 * active <操作中の人形の番号>     ← E13。無ければ 0
 * otherdoll <rib|pelvis>|<BodyCodec の1行>|<PoseCodec の文字列。改行の代わりに ;>   ← E13。操作中でない人形を並び順に
 * pose
 * <PoseCodec の文字列>
 * ```
 */
object EditorStateCodec {
    private const val HEADER = "dessin-editor 1"

    fun encode(state: EditorState): String = buildString {
        appendLine(HEADER)
        appendLine("selected ${state.selected}")
        state.camera?.let { c -> appendLine("camera ${c.target.x} ${c.target.y} ${c.target.z} ${c.yaw} ${c.pitch} ${c.distance}") }
        val o = state.options
        appendLine("options ${bit(o.guides)} ${bit(o.lines)} ${bit(o.markers)} ${bit(o.grid)} ${bit(o.shadows)} ${bit(o.fadeOthers)}")
        appendLine("panel ${bit(state.panelOpen)}")
        appendLine("cameraonly ${bit(state.cameraOnly)}")
        appendLine("bodytype ${BodyCodec.encodeLine(state.skeleton.body)}")
        appendLine("anchor ${anchorCode(state.skeleton.anchor)}")
        appendLine("active ${state.activeDoll}")
        state.others.forEach { d ->
            appendLine("otherdoll ${anchorCode(d.anchor)}|${BodyCodec.encodeLine(d.body)}|${PoseCodec.encode(d).trimEnd().replace('\n', ';')}")
        }
        appendLine("pose")
        append(PoseCodec.encode(state.skeleton))
    }

    /** 読み込めなければ null(呼び出し側で初期状態に戻す) */
    fun decode(text: String): EditorState? {
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != HEADER) return null
        val poseAt = lines.indexOfFirst { it.trim() == "pose" }
        if (poseAt < 0) return null
        val skeleton = when (val r = PoseCodec.decode(lines.drop(poseAt + 1).joinToString("\n"))) {
            is PoseDecodeResult.Ok -> r.skeleton
            is PoseDecodeResult.Error -> return null
        }
        var state = EditorState(skeleton = NaturalPose.unplacedNatural(skeleton))
        for (line in lines.subList(1, poseAt).map { it.trim() }.filter { it.isNotEmpty() }) {
            val t = line.split(Regex("\\s+"))
            state = when (t[0]) {
                "selected" -> state.copy(selected = t.getOrNull(1) ?: return null)
                "camera" -> {
                    val n = t.drop(1).map { it.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null }
                    if (n.size != 6) return null
                    state.copy(camera = OrbitCamera(Vec3(n[0], n[1], n[2]), n[3], n[4], n[5]))
                }
                "options" -> {
                    val b = t.drop(1).map { flag(it) ?: return null }
                    if (b.size !in 4..6) return null
                    state.copy(options = DisplayOptions(b[0], b[1], b[2], b[3], b.getOrElse(4) { true }, b.getOrElse(5) { true }))
                }
                "panel" -> state.copy(panelOpen = flag(t.getOrNull(1) ?: return null) ?: return null)
                "cameraonly" -> state.copy(cameraOnly = flag(t.getOrNull(1) ?: return null) ?: return null)
                "active" -> state.copy(activeDoll = t.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 0 } ?: return null)
                "otherdoll" -> {
                    val parts = line.removePrefix("otherdoll").trim().split('|')
                    if (parts.size != 3) return null
                    val anchor = when (parts[0]) {
                        "pelvis" -> Anchor.PELVIS
                        "rib" -> Anchor.CHEST
                        else -> return null
                    }
                    val body = BodyCodec.decodeLine(parts[1]) ?: return null
                    val pose = (PoseCodec.decode(parts[2].replace(';', '\n')) as? PoseDecodeResult.Ok)?.skeleton ?: return null
                    state.copy(others = state.others + NaturalPose.unplacedNatural(pose.withBody(body).withAnchor(anchor)))
                }
                "anchor" -> when (t.getOrNull(1)) {
                    "pelvis" -> state.copy(skeleton = state.skeleton.withAnchor(Anchor.PELVIS))
                    "rib" -> state.copy(skeleton = state.skeleton.withAnchor(Anchor.CHEST))
                    else -> return null
                }
                "bodytype" -> {
                    val body = BodyCodec.decodeLine(line.removePrefix("bodytype")) ?: return null
                    state.copy(skeleton = state.skeleton.withBody(body))
                }
                else -> return null
            }
        }
        // 選んでいた部位が置かれていなければ胸を選ぶ
        // 操作中の人形の番号は、人形の数の範囲に入っていること(E13)
        if (state.activeDoll > state.others.size) return null
        if (!skeleton.isPlaced(state.selected)) state = state.copy(selected = "rib")
        return state
    }

    private fun bit(b: Boolean) = if (b) "1" else "0"

    private fun anchorCode(a: Anchor) = if (a == Anchor.PELVIS) "pelvis" else "rib"

    private fun flag(s: String): Boolean? = when (s) {
        "1" -> true
        "0" -> false
        else -> null
    }
}
