package com.yswy.dessin.core.skeleton

/**
 * 手の形(E08-02)。指の関節は作らず、手の形ごと差し替える。ポーズの一部として左右別々に持つ。
 * @property code 保存形式での名前
 */
enum class HandShape(val code: String) {
    /** パー(指を扇状に開く)。既定 */
    OPEN("open"),
    /** グー(ブロック) */
    FIST("fist");

    companion object {
        val DEFAULT = OPEN

        fun ofCode(code: String): HandShape? = entries.firstOrNull { it.code == code }
    }
}

/** 手の部位か(handL・handR) */
fun isHand(id: String): Boolean = id == "handL" || id == "handR"
