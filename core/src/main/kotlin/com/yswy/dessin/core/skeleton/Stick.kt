package com.yswy.dessin.core.skeleton

/**
 * 手に持たせる棒の形(E18-01)。ポーズの一部として左右別々に持つ。
 * @property length 棒の長さ
 * @property grip 握る位置。棒の後ろの端から測った割合(0 で後ろの端、0.5 で真ん中、1 で前の端を握る)
 */
data class StickShape(val length: Double, val grip: Double) {
    /** 範囲に収めた形 */
    fun clamped() = StickShape(length.coerceIn(LENGTH_RANGE), grip.coerceIn(GRIP_RANGE))

    companion object {
        val DEFAULT = StickShape(0.6, 0.5)
        val LENGTH_RANGE = 0.1..2.0
        val GRIP_RANGE = 0.0..1.0
    }
}

/** 棒の部位か(stickL・stickR) */
fun isStick(id: String): Boolean = id == "stickL" || id == "stickR"
