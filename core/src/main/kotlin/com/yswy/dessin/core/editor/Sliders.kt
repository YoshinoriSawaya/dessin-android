package com.yswy.dessin.core.editor

import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.skeleton.Adjust
import com.yswy.dessin.core.skeleton.PartSpecs
import kotlin.math.PI
import kotlin.math.roundToInt

enum class SliderKind {
    RIB_SIZE, RIB_TILT, RIB_TWIST, RIB_SIDE_TILT,
    ROLL, HEAD_SIZE, SHOULDER_WIDTH, PELVIS_WIDTH, HIP_INSET,
}

/**
 * スライダー1本(Web版の setRow)。値は整数で、単位は unit(%・°・mm)。
 * 範囲が0(min == max)なら使えない。
 */
data class Slider(val kind: SliderKind, val label: String, val min: Int, val max: Int, val value: Int, val unit: String) {
    val enabled: Boolean get() = min != max
}

private const val D2R = PI / 180

private fun deg(r: Double) = (r / D2R).roundToInt()

/** 選択中の部位で出すスライダー(Web版の syncUI) */
val EditorState.sliders: List<Slider>
    get() {
        val spec = PartSpecs[selected]
        val body = skeleton.body
        if (spec.isRoot) {
            val root = skeleton.root
            val e = root.rotation.toEulerYXZ()
            return listOf(
                Slider(SliderKind.RIB_SIZE, "大きさ", 30, 160, (root.scale * 100).roundToInt(), "%"),
                Slider(SliderKind.RIB_TILT, "前後の傾き", -90, 90, deg(e.x), "°"),
                Slider(SliderKind.RIB_TWIST, "ひねり", -180, 180, deg(e.y), "°"),
                Slider(SliderKind.RIB_SIDE_TILT, "横の傾き", -90, 90, deg(e.z), "°"),
            )
        }
        return buildList {
            add(Slider(SliderKind.ROLL, "ひねり", deg(spec.roll.min), deg(spec.roll.max), deg(skeleton.state(selected).roll), "°"))
            when (spec.adjust) {
                Adjust.HEAD_SIZE -> add(Slider(SliderKind.HEAD_SIZE, "大きさ", 60, 140, (body.headSize * 100).roundToInt(), "%"))
                Adjust.SHOULDER_WIDTH -> add(Slider(SliderKind.SHOULDER_WIDTH, "肩幅(左右)", 40, 150, (body.shoulderWidth * 100).roundToInt(), "%"))
                Adjust.PELVIS_WIDTH -> {
                    add(Slider(SliderKind.PELVIS_WIDTH, "骨盤の横幅", 60, 150, (body.pelvisWidth * 100).roundToInt(), "%"))
                    add(Slider(SliderKind.HIP_INSET, "付け根の内寄り", 0, 80, (body.hipInset * 1000).roundToInt(), "mm"))
                }
                null -> Unit
            }
        }
    }

/**
 * スライダーの値を反映する(Web版の onSlider)。選択中の部位に無いスライダーなら何もしない。
 * 肋骨の傾きは、いまの回転をオイラー角(YXZ)に分けて1軸だけ差し替える。
 */
fun EditorState.applySlider(kind: SliderKind, value: Int): EditorState {
    if (sliders.none { it.kind == kind }) return this
    val s = skeleton
    val root = s.root
    fun rotate(change: (Vec3) -> Vec3) = copy(skeleton = s.withRoot(root.copy(rotation = Quat.fromEulerYXZ(change(root.rotation.toEulerYXZ())))))
    return when (kind) {
        SliderKind.RIB_SIZE -> copy(skeleton = s.withRoot(root.copy(scale = value / 100.0)))
        SliderKind.RIB_TILT -> rotate { Vec3(value * D2R, it.y, it.z) }
        SliderKind.RIB_TWIST -> rotate { Vec3(it.x, value * D2R, it.z) }
        SliderKind.RIB_SIDE_TILT -> rotate { Vec3(it.x, it.y, value * D2R) }
        SliderKind.ROLL -> copy(skeleton = s.withState(selected, s.state(selected).copy(roll = value * D2R)))
        SliderKind.HEAD_SIZE -> copy(skeleton = s.withBody(s.body.copy(headSize = value / 100.0)))
        SliderKind.SHOULDER_WIDTH -> copy(skeleton = s.withBody(s.body.copy(shoulderWidth = value / 100.0)))
        SliderKind.PELVIS_WIDTH -> copy(skeleton = s.withBody(s.body.copy(pelvisWidth = value / 100.0)))
        SliderKind.HIP_INSET -> copy(skeleton = s.withBody(s.body.copy(hipInset = value / 1000.0)))
    }
}
