package com.yswy.dessin.core.editor

import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.render.DisplayOptions
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.Skeleton

/** 部位チップの見た目(Web版の chip の aria-pressed・placed・avail・disabled) */
enum class ChipLook { SELECTED, PLACED, AVAILABLE, DISABLED }

data class PartChip(val id: String, val label: String, val look: ChipLook) {
    /** 押せる(置ける部位) */
    val enabled: Boolean get() = look != ChipLook.DISABLED
}

/**
 * 編集画面の状態。不変で、操作は新しい EditorState を返す。
 * @property camera 視点。画面の大きさが分かって全体を表示するまでは null
 * @property panelOpen 操作パネルを開いているか(Web版の折りたたみ)
 */
data class EditorState(
    val skeleton: Skeleton = Skeleton.initial(),
    val selected: String = "rib",
    val camera: OrbitCamera? = null,
    val options: DisplayOptions = DisplayOptions(),
    val panelOpen: Boolean = true,
) {
    /** 部位を選ぶ。置いていなければ置く。置けない部位なら何もしない(Web版の select) */
    fun select(id: String): EditorState {
        if (!skeleton.isAvailable(id)) return this
        return copy(skeleton = skeleton.place(id), selected = id)
    }

    /** 選択中の部位は外せるか(胸は外せない) */
    val canRemove: Boolean get() = !PartSpecs[selected].isRoot

    /** 選択中の部位を子孫ごと外し、親を選ぶ(Web版の「外す」) */
    fun removeSelected(): EditorState {
        val parent = PartSpecs[selected].parent ?: return this
        return copy(skeleton = skeleton.remove(selected)).select(parent)
    }

    /** 選択中の部位の向きを戻す(Web版の「向きを戻す」) */
    fun resetSelected(): EditorState = copy(skeleton = skeleton.reset(selected))

    fun togglePanel(): EditorState = copy(panelOpen = !panelOpen)

    fun withCamera(camera: OrbitCamera): EditorState = copy(camera = camera)

    val selectionLabel: String get() = PartSpecs[selected].label

    /** 選択中の部位の操作の説明(Web版の selInfo) */
    val selectionInfo: String
        get() {
            val spec = PartSpecs[selected]
            return when {
                spec.isRoot -> "ドラッグで画面上の位置を決めます。"
                spec.hinge != null -> "緑の扇の中をドラッグ、または2回タップして曲げます。"
                else -> "緑の範囲の中をドラッグ、または2回タップして向きを決めます。"
            }
        }

    /** 部位チップ。配置の段ごとのまとまり(Web版の CHIP_ORDER) */
    val partChips: List<List<PartChip>>
        get() = CHIP_ORDER.map { group ->
            group.map { id ->
                val look = when {
                    id == selected -> ChipLook.SELECTED
                    skeleton.isPlaced(id) -> ChipLook.PLACED
                    skeleton.isAvailable(id) -> ChipLook.AVAILABLE
                    else -> ChipLook.DISABLED
                }
                PartChip(id, PartSpecs[id].label, look)
            }
        }

    private companion object {
        val CHIP_ORDER = listOf(
            listOf("rib"),
            listOf("clavL", "clavR", "neck", "flank"),
            listOf("upperArmL", "upperArmR", "head", "pelvis"),
            listOf("forearmL", "forearmR", "hipL", "hipR"),
            listOf("handL", "handR", "thighL", "thighR"),
            listOf("shinL", "shinR"),
            listOf("footL", "footR"),
            listOf("toeL", "toeR"),
        )
    }
}
