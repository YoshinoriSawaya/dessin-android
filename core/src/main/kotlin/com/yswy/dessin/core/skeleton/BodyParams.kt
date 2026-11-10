package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.Vec3
import kotlin.math.max
import kotlin.math.sign

/**
 * 体型の調整パラメータ。値の範囲は Web版のスライダーと同じ(UI 側で範囲内に収める)。
 * メッシュの拡大(肩や骨盤の見た目の幅)は描画層が持ち、ここでは骨格の位置と拡大率だけを扱う。
 *
 * @property headSize 頭の大きさ(倍率)
 * @property shoulderWidth 肩幅(倍率、左右共通)
 * @property pelvisWidth 骨盤の横幅(倍率)
 * @property hipInset 付け根の内寄り。骨盤の外縁から股関節までの距離
 */
data class BodyParams(
    val headSize: Double = 1.0,
    val shoulderWidth: Double = 1.0,
    val pelvisWidth: Double = 1.0,
    val hipInset: Double = 0.0407,
) {
    companion object {
        val HEAD_SIZE_RANGE = 0.6..1.4
        val SHOULDER_WIDTH_RANGE = 0.4..1.5
        val PELVIS_WIDTH_RANGE = 0.6..1.5
        val HIP_INSET_RANGE = 0.0..0.08
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
 * 調整を反映した取り付け位置。肩幅は上腕の、骨盤の横幅と内寄りはももの取り付け位置を左右に動かす。
 * ほかの部位は定義のまま。
 */
fun attachOf(spec: PartSpec, params: BodyParams): Vec3 {
    val a = spec.attach
    return when {
        spec.id.startsWith("upperArm") -> Vec3(a.x * params.shoulderWidth, a.y, a.z)
        spec.id.startsWith("thigh") -> Vec3(sign(a.x) * hipLength(params), a.y, a.z)
        else -> a
    }
}

/** 関節の拡大率。頭だけが大きさを変えられる(子孫にも効く) */
fun jointScale(spec: PartSpec, params: BodyParams): Double =
    if (spec.adjust == Adjust.HEAD_SIZE) params.headSize else 1.0
