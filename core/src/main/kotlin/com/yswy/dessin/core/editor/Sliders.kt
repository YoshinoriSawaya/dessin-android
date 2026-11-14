package com.yswy.dessin.core.editor

import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.skeleton.Adjust
import com.yswy.dessin.core.skeleton.BodyParams
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.lengthScale
import kotlin.math.PI
import kotlin.math.roundToInt

enum class SliderKind {
    RIB_SIZE, RIB_TILT, RIB_TWIST, RIB_SIDE_TILT,
    ROLL, HEAD_SIZE, SHOULDER_WIDTH, PELVIS_WIDTH, HIP_INSET,
    // E07-02
    HEIGHT, NECK_LENGTH, NECK_OFFSET, LIMB_LENGTH, KNEE_ROLL,
    // E08-04
    BREAST_SIZE,
    // E14-03: 胸・脇腹・骨盤の長さ(選んだ部位の長さ)
    TORSO_LENGTH,
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
private fun pct(v: Double) = (v * 100).roundToInt()
private fun mm(v: Double) = (v * 1000).roundToInt()

/** 左右の L・R を除いた部位の名前 */
private fun baseId(id: String) = if (PartSpecs[id].side != null) id.dropLast(1) else id

/** 選択中の部位(上腕・前腕・もも・すね)の長さ */
private fun EditorState.limbLength(body: BodyParams): Slider =
    Slider(SliderKind.LIMB_LENGTH, "長さ(左右)", 70, 130, pct(lengthScale(PartSpecs[selected], body)), "%")

/** 選択中の部位(胸・脇腹・骨盤)の長さ(E14-03) */
private fun EditorState.torsoLength(body: BodyParams): Slider =
    Slider(SliderKind.TORSO_LENGTH, "長さ", 70, 130, pct(lengthScale(PartSpecs[selected], body)), "%")

/** 根の傾きの3本(前後の傾き・ひねり・横の傾き)。rotation をオイラー角(YXZ)に分けた値 */
private fun tiltSliders(rotation: Quat): List<Slider> {
    val e = rotation.toEulerYXZ()
    return listOf(
        Slider(SliderKind.RIB_TILT, "前後の傾き", -90, 90, deg(e.x), "°"),
        Slider(SliderKind.RIB_TWIST, "ひねり", -180, 180, deg(e.y), "°"),
        Slider(SliderKind.RIB_SIDE_TILT, "横の傾き", -90, 90, deg(e.z), "°"),
    )
}

/** 選択中の部位で出すスライダー(Web版の syncUI) */
val EditorState.sliders: List<Slider>
    get() {
        val spec = PartSpecs[selected]
        val body = skeleton.body
        if (spec.isRoot) {
            val root = skeleton.root
            return listOf(Slider(SliderKind.RIB_SIZE, "大きさ", 30, 160, (root.scale * 100).roundToInt(), "%")) +
                tiltSliders(root.rotation) +
                listOf(
                    Slider(SliderKind.HEIGHT, "身長", 70, 130, pct(body.height), "%"),
                    Slider(SliderKind.BREAST_SIZE, "乳房", 0, 100, pct(body.breastSize), "%"),
                    torsoLength(body),
                )
        }
        return buildList {
            when {
                // 骨盤が根のときの骨盤は、胸と同じ傾きの3本(骨盤のワールドの向き)。自分のひねりの行は出さない(E14-04)
                skeleton.isAnchorRoot(selected) -> addAll(tiltSliders(skeleton.worldTransforms().getValue(selected).rotation))
                // 骨盤が根のときの脇腹は、動く側(上半身)が胸が根のときの骨盤と同じ向きに回るよう、値の符号を逆にして見せる(E14-04)
                skeleton.isAnchorSide(selected) ->
                    add(Slider(SliderKind.ROLL, "ひねり", -deg(spec.roll.max), -deg(spec.roll.min), -deg(skeleton.state(selected).roll), "°"))
                else -> add(Slider(SliderKind.ROLL, "ひねり", deg(spec.roll.min), deg(spec.roll.max), deg(skeleton.state(selected).roll), "°"))
            }
            when (spec.adjust) {
                Adjust.HEAD_SIZE -> add(Slider(SliderKind.HEAD_SIZE, "大きさ", 60, 140, (body.headSize * 100).roundToInt(), "%"))
                Adjust.SHOULDER_WIDTH -> add(Slider(SliderKind.SHOULDER_WIDTH, "肩幅(左右)", 40, 150, (body.shoulderWidth * 100).roundToInt(), "%"))
                Adjust.PELVIS_WIDTH -> {
                    add(Slider(SliderKind.PELVIS_WIDTH, "骨盤の横幅", 60, 150, (body.pelvisWidth * 100).roundToInt(), "%"))
                    add(Slider(SliderKind.HIP_INSET, "付け根の内寄り", 0, 80, (body.hipInset * 1000).roundToInt(), "mm"))
                }
                null -> Unit
            }
            when (baseId(selected)) {
                "neck" -> {
                    add(Slider(SliderKind.NECK_LENGTH, "長さ", 50, 200, pct(body.neckLength), "%"))
                    add(Slider(SliderKind.NECK_OFFSET, "付け根の前後", -70, 20, mm(body.neckOffset), "mm"))
                }
                "upperArm", "forearm", "thigh" -> add(limbLength(body))
                "shin" -> {
                    add(limbLength(body))
                    add(Slider(SliderKind.KNEE_ROLL, "ひざの転がり", 0, 60, mm(body.kneeRoll), "mm"))
                }
                "flank", "pelvis" -> add(torsoLength(body))
            }
        }
    }

/**
 * スライダーの値を反映する(Web版の onSlider)。選択中の部位に無いスライダーなら何もしない。
 * 胸の傾きは、いまの回転をオイラー角(YXZ)に分けて1軸だけ差し替える。
 */
fun EditorState.applySlider(kind: SliderKind, value: Int): EditorState {
    if (sliders.none { it.kind == kind }) return this
    val s = skeleton
    val root = s.root
    fun rotate(change: (Vec3) -> Vec3): EditorState {
        if (s.isAnchorRoot(selected)) {
            // 骨盤が根のときの骨盤は、骨盤のワールドの向きを差し替え、骨盤の関節を中心に全体を回す(E14-04)
            val j = s.worldTransforms().getValue(selected)
            val target = Quat.fromEulerYXZ(change(j.rotation.toEulerYXZ()))
            return copy(skeleton = s.rotatedAbout(j.position, (target * j.rotation.inverse()).normalized()))
        }
        return copy(skeleton = s.withRoot(root.copy(rotation = Quat.fromEulerYXZ(change(root.rotation.toEulerYXZ())))))
    }
    return when (kind) {
        SliderKind.RIB_SIZE -> copy(skeleton = s.withRoot(root.copy(scale = value / 100.0)))
        SliderKind.RIB_TILT -> rotate { Vec3(value * D2R, it.y, it.z) }
        SliderKind.RIB_TWIST -> rotate { Vec3(it.x, value * D2R, it.z) }
        SliderKind.RIB_SIDE_TILT -> rotate { Vec3(it.x, it.y, value * D2R) }
        SliderKind.ROLL -> {
            val sign = if (s.isAnchorSide(selected)) -1 else 1
            copy(skeleton = s.withState(selected, s.state(selected).copy(roll = sign * value * D2R)))
        }
        SliderKind.HEAD_SIZE -> copy(skeleton = s.withBody(s.body.copy(headSize = value / 100.0)))
        SliderKind.SHOULDER_WIDTH -> copy(skeleton = s.withBody(s.body.copy(shoulderWidth = value / 100.0)))
        SliderKind.PELVIS_WIDTH -> copy(skeleton = s.withBody(s.body.copy(pelvisWidth = value / 100.0)))
        SliderKind.HIP_INSET -> copy(skeleton = s.withBody(s.body.copy(hipInset = value / 1000.0)))
        SliderKind.HEIGHT -> copy(skeleton = s.withBody(s.body.copy(height = value / 100.0)))
        SliderKind.NECK_LENGTH -> copy(skeleton = s.withBody(s.body.copy(neckLength = value / 100.0)))
        SliderKind.NECK_OFFSET -> copy(skeleton = s.withBody(s.body.copy(neckOffset = value / 1000.0)))
        SliderKind.KNEE_ROLL -> copy(skeleton = s.withBody(s.body.copy(kneeRoll = value / 1000.0)))
        SliderKind.BREAST_SIZE -> copy(skeleton = s.withBody(s.body.copy(breastSize = value / 100.0)))
        SliderKind.TORSO_LENGTH -> {
            val k = value / 100.0
            val b = s.body
            copy(skeleton = s.withBody(when (selected) {
                "flank" -> b.copy(flankLength = k)
                "pelvis" -> b.copy(pelvisLength = k)
                else -> b.copy(chestLength = k)
            }))
        }
        SliderKind.LIMB_LENGTH -> {
            val k = value / 100.0
            val b = s.body
            copy(skeleton = s.withBody(when (baseId(selected)) {
                "upperArm" -> b.copy(upperArmLength = k)
                "forearm" -> b.copy(forearmLength = k)
                "thigh" -> b.copy(thighLength = k)
                else -> b.copy(shinLength = k)
            }))
        }
    }
}
