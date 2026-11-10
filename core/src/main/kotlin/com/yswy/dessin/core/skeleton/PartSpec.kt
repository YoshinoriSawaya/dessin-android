package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.Vec3

enum class Side { LEFT, RIGHT }

/** 部位ごとの調整スライダー(Web版の scalable・widthLink・widthPelvis) */
enum class Adjust { HEAD_SIZE, SHOULDER_WIDTH, PELVIS_WIDTH }

/**
 * 休止方向から振れる角度の上限(ラジアン)。基準フレームでの方向ごと。
 * front: +z 側、back: −z 側、plusX・minusX: ±x 側。
 */
data class Cone(val front: Double, val back: Double, val plusX: Double, val minusX: Double) {
    /** 右側用。±x を入れ替える */
    fun mirrored() = Cone(front, back, minusX, plusX)
}

/** 肘・膝。曲げる向き psi を固定し、曲げ角 phi を [min, max] に制限する(ラジアン) */
data class Hinge(val psi: Double, val min: Double, val max: Double)

/** ひねり(roll)の範囲(ラジアン) */
data class RollRange(val min: Double, val max: Double) {
    /** 右側用。符号を反転し、上下を入れ替える */
    fun mirrored() = RollRange(-max, -min)
}

/**
 * 部位の定義。左右の鏡像化と度→ラジアンの変換は済んだ値を持つ。
 * 見た目の寸法(メッシュの太さなど)は描画層が持つ。
 *
 * @property attach 親の関節からの取り付け位置
 * @property restDir 休止方向(Web版の a0)
 * @property ref 基準フレームの z 軸を寄せる向き
 * @property length 部位の長さ(Web版の L)
 * @property guideRadius 可動域の表示と玉の半径(Web版の rVis)
 * @property pivot 回転の中心の、関節からのずれ(親の空間)。ひざは大腿骨の丸みの中心で回る(E06-02)。ふつうは 0
 */
data class PartSpec(
    val id: String,
    val label: String,
    val parent: String?,
    val side: Side?,
    val attach: Vec3,
    val restDir: Vec3,
    val ref: Vec3,
    val length: Double,
    val guideRadius: Double,
    val cone: Cone?,
    val hinge: Hinge?,
    val roll: RollRange,
    val adjust: Adjust?,
    val pivot: Vec3 = Vec3.ZERO,
) {
    val isRoot: Boolean get() = parent == null
}
