package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.Vec3
import kotlin.math.max
import kotlin.math.sign

/**
 * 体型の調整パラメータ。値の範囲は Web版のスライダーと同じ(UI 側で範囲内に収める)。
 * メッシュの拡大(肩や骨盤の見た目の幅)は描画層が持ち、ここでは骨格の位置と拡大率だけを扱う。
 *
 * @property headSize 頭の大きさ(倍率。基準 HEAD_BASE_SCALE に対する倍率)
 * @property shoulderWidth 肩幅(倍率、左右共通)
 * @property pelvisWidth 骨盤の横幅(倍率)
 * @property hipInset 付け根の内寄り。骨盤の外縁から股関節までの距離
 * @property neckLength 首の長さ(倍率)。E07-02
 * @property neckOffset 首の付け根の前後の位置(z。負が後ろ)。E07-02
 * @property upperArmLength 上腕の長さ(倍率、左右共通)。E07-02
 * @property forearmLength 前腕の長さ(倍率、左右共通)。E07-02
 * @property thighLength ももの長さ(倍率、左右共通)。E07-02
 * @property shinLength すねの長さ(倍率、左右共通)。E07-02
 * @property height 身長(人形全体の倍率。胸の大きさと掛け合わさる)。E07-02
 * @property kneeRoll ひざの転がりのずれ。すねの回転の中心の、ひざの関節からの高さ(E06-02・E07-02)
 */
data class BodyParams(
    val headSize: Double = 1.0,
    val shoulderWidth: Double = 1.0,
    val pelvisWidth: Double = 1.0,
    val hipInset: Double = 0.0407,
    val neckLength: Double = 1.0,
    val neckOffset: Double = -0.035,
    val upperArmLength: Double = 1.0,
    val forearmLength: Double = 1.0,
    val thighLength: Double = 1.0,
    val shinLength: Double = 1.0,
    val height: Double = 1.0,
    val kneeRoll: Double = 0.03,
) {
    /** 範囲に収めた値 */
    fun clamped() = BodyParams(
        headSize.coerceIn(HEAD_SIZE_RANGE),
        shoulderWidth.coerceIn(SHOULDER_WIDTH_RANGE),
        pelvisWidth.coerceIn(PELVIS_WIDTH_RANGE),
        hipInset.coerceIn(HIP_INSET_RANGE),
        neckLength.coerceIn(NECK_LENGTH_RANGE),
        neckOffset.coerceIn(NECK_OFFSET_RANGE),
        upperArmLength.coerceIn(LIMB_LENGTH_RANGE),
        forearmLength.coerceIn(LIMB_LENGTH_RANGE),
        thighLength.coerceIn(LIMB_LENGTH_RANGE),
        shinLength.coerceIn(LIMB_LENGTH_RANGE),
        height.coerceIn(HEIGHT_RANGE),
        kneeRoll.coerceIn(KNEE_ROLL_RANGE),
    )

    companion object {
        val HEAD_SIZE_RANGE = 0.6..1.4
        val SHOULDER_WIDTH_RANGE = 0.4..1.5
        val PELVIS_WIDTH_RANGE = 0.6..1.5
        val HIP_INSET_RANGE = 0.0..0.08
        val NECK_LENGTH_RANGE = 0.5..2.0
        val NECK_OFFSET_RANGE = -0.07..0.02
        val LIMB_LENGTH_RANGE = 0.7..1.3
        val HEIGHT_RANGE = 0.7..1.3
        val KNEE_ROLL_RANGE = 0.0..0.06
    }
}

/** 骨盤の外縁の x(横幅が1倍のとき) */
internal const val PELVIS_OUTER = 0.1157
/** 股関節が骨盤の中心に寄れる限界の x */
private const val HIP_MIN_X = 0.03
/** 付け根の取り付け位置の x(PartSpecs の hipL と同じ) */
internal const val HIP_ROOT_X = 0.025

/**
 * 付け根(hip)の長さ。股関節は骨盤の外縁から hipInset だけ内側に置く(Web版の updateHips)。
 * 描画層は付け根のメッシュをこの長さに合わせる。
 */
fun hipLength(params: BodyParams): Double {
    val x = max(HIP_MIN_X, PELVIS_OUTER * params.pelvisWidth - params.hipInset)
    return x - HIP_ROOT_X
}

/**
 * 部位の長さの倍率(E07-02)。首・上腕・前腕・もも・すねだけが変わる(左右共通)。
 * 部位の形は縦に伸び、子の取り付け位置と可動域の表示の半径もこの倍率に従う。
 */
fun lengthScale(spec: PartSpec, params: BodyParams): Double = when (spec.id.removeSuffix("L").removeSuffix("R").takeIf { spec.side != null } ?: spec.id) {
    "neck" -> params.neckLength
    "upperArm" -> params.upperArmLength
    "forearm" -> params.forearmLength
    "thigh" -> params.thighLength
    "shin" -> params.shinLength
    else -> 1.0
}

/**
 * 調整を反映した取り付け位置。肩幅は上腕の、骨盤の横幅と内寄りはももの取り付け位置を左右に動かす。
 * 首の付け根は前後に動く。長さを変えた部位の子は、親の長さの倍率だけ離れる(E07-02)。
 */
fun attachOf(spec: PartSpec, params: BodyParams): Vec3 {
    val a = spec.attach
    val placed = when {
        spec.id == "neck" -> Vec3(a.x, a.y, params.neckOffset)
        spec.id.startsWith("upperArm") -> Vec3(a.x * params.shoulderWidth, a.y, a.z)
        spec.id.startsWith("thigh") -> Vec3(sign(a.x) * hipLength(params), a.y, a.z)
        else -> a
    }
    val parent = spec.parent?.let { PartSpecs[it] } ?: return placed
    return placed * lengthScale(parent, params)
}

/** 回転の中心の、関節からのずれ。ひざ(すね)は体型の kneeRoll だけ上(E07-02)。ほかは定義のまま */
fun pivotOf(spec: PartSpec, params: BodyParams): Vec3 =
    if (spec.pivot == Vec3.ZERO) spec.pivot else Vec3(0.0, params.kneeRoll, 0.0)

/** 頭の大きさの基準。headSize = 1.0(100%)のときの頭は、v1 の頭のこの倍率(E07-01) */
const val HEAD_BASE_SCALE = 0.7

/** 関節の拡大率。頭だけが大きさを変えられる(子孫にも効く)。倍率は HEAD_BASE_SCALE に対して掛かる */
fun jointScale(spec: PartSpec, params: BodyParams): Double =
    if (spec.adjust == Adjust.HEAD_SIZE) HEAD_BASE_SCALE * params.headSize else 1.0
