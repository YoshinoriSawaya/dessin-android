package com.yswy.dessin.core.mesh

/**
 * 床の格子の線(GL_LINES 用の端点の並び)。中心の2本は色を変えるので分けて持つ。
 * @property center 原点を通る x 方向と z 方向の線
 * @property others それ以外の線
 */
class GridLines(val center: FloatArray, val others: FloatArray)

/** 一辺 size の正方形を divisions 等分した格子を、高さ y に置く(three.js r128 の GridHelper) */
fun gridLines(size: Double, divisions: Int, y: Double): GridLines {
    val half = size / 2
    val step = size / divisions
    val center = ArrayList<Float>()
    val others = ArrayList<Float>()
    for (i in 0..divisions) {
        val k = -half + i * step
        val out = if (i * 2 == divisions) center else others
        listOf(-half, y, k, half, y, k, k, y, -half, k, y, half).forEach { out += it.toFloat() }
    }
    return GridLines(center.toFloatArray(), others.toFloatArray())
}
