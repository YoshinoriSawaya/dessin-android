package com.yswy.dessin.core.editor

import com.yswy.dessin.core.camera.OrbitCamera
import com.yswy.dessin.core.render.DisplayOptions
import com.yswy.dessin.core.skeleton.BodyParams
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
 * @property cameraOnly カメラだけを操作するモード(E12)。指の操作で部位を動かさず、選ばない
 * @property skeleton 操作中の人形(E13 で複数の人形を置けるようにした。今までの操作はすべて操作中の人形に効く)
 * @property others 操作中でない人形(場面での並び順。操作中の人形は activeDoll 番目に入る)
 * @property activeDoll 操作中の人形が場面の何番目か
 * @property dollMode 人形のモード(E13-02)。パネルで人形を選ぶ・足す・消す。3D では部位を動かさず、タップした人形に切り替える
 */
data class EditorState(
    val skeleton: Skeleton = NaturalPose.initial,
    val selected: String = "rib",
    val camera: OrbitCamera? = null,
    val options: DisplayOptions = DisplayOptions(),
    val panelOpen: Boolean = true,
    val cameraOnly: Boolean = false,
    val others: List<Skeleton> = emptyList(),
    val activeDoll: Int = 0,
    val dollMode: Boolean = false,
) {
    /** 場面のすべての人形(並び順。activeDoll 番目が操作中) */
    val dolls: List<Skeleton> get() = others.take(activeDoll) + skeleton + others.drop(activeDoll)

    /** i 番目の人形を操作中にする。選んでいた部位がその人形に無ければ胸を選ぶ。範囲外や今の人形なら何もしない */
    fun switchDoll(i: Int): EditorState {
        val all = dolls
        if (i !in all.indices || i == activeDoll) return this
        return withActive(all, i)
    }

    /**
     * 人形を足して操作中にする(10体まで)。今いる人形のいちばん右の隣に、胸だけを置いた初めの状態で置く。
     * 体型は body(画面からは既定の体型を渡す。E07-04)
     */
    fun addDoll(body: BodyParams = BodyParams()): EditorState {
        val all = dolls
        if (all.size >= MAX_DOLLS) return this
        val start = NaturalPose.initial
        val x = all.maxOf { it.root.position.x } + DOLL_SPACING
        val doll = start.withRoot(start.root.copy(position = start.root.position.copy(x = x))).withBody(body)
        return withActive(all + doll, all.size).copy(selected = "rib")
    }

    /** 操作中の人形を消し、前の人形を操作中にする。最後の1体は消せない */
    fun removeDoll(): EditorState {
        val all = dolls
        if (all.size <= 1) return this
        val rest = all.filterIndexed { i, _ -> i != activeDoll }
        return withActive(rest, maxOf(0, activeDoll - 1))
    }

    private fun withActive(all: List<Skeleton>, i: Int): EditorState {
        val target = all[i]
        return copy(
            skeleton = target,
            others = all.filterIndexed { j, _ -> j != i },
            activeDoll = i,
            selected = if (target.isPlaced(selected)) selected else "rib",
        )
    }

    /** 部位を選ぶ。置いていなければ自然な姿勢(E09)で置く。置けない部位なら何もしない(Web版の select) */
    fun select(id: String): EditorState {
        if (!skeleton.isAvailable(id)) return this
        if (skeleton.isPlaced(id)) return copy(selected = id)
        return copy(skeleton = skeleton.place(id).withState(id, NaturalPose.state(PartSpecs[id])), selected = id)
    }

    /** 選択中の部位は外せるか(胸は外せない) */
    val canRemove: Boolean get() = !PartSpecs[selected].isRoot

    /** 選択中の部位を子孫ごと外し、親を選ぶ(Web版の「外す」) */
    fun removeSelected(): EditorState {
        val parent = PartSpecs[selected].parent ?: return this
        return copy(skeleton = NaturalPose.unplacedNatural(skeleton.remove(selected))).select(parent)
    }

    /** 選択中の部位の向きを戻す(Web版の「向きを戻す」)。胸は位置・回転・大きさを、ほかは自然な姿勢(E09)に戻す */
    fun resetSelected(): EditorState {
        val spec = PartSpecs[selected]
        return copy(skeleton = if (spec.isRoot) skeleton.reset(selected) else skeleton.withState(selected, NaturalPose.state(spec)))
    }

    fun togglePanel(): EditorState = copy(panelOpen = !panelOpen)

    fun toggleCameraOnly(): EditorState = copy(cameraOnly = !cameraOnly)

    fun toggleDollMode(): EditorState = copy(dollMode = !dollMode)

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

/** 場面に置ける人形の数(E13) */
const val MAX_DOLLS = 10

/** 人形を足すときの横の間隔 */
private const val DOLL_SPACING = 0.6
