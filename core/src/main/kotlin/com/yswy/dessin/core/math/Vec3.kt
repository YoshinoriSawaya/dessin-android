package com.yswy.dessin.core.math

import kotlin.math.sqrt

/** 3次元ベクトル。three.js の Vector3 のうち、ロジック層で使う操作だけを持つ。不変 */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun lengthSq() = dot(this)
    fun length() = sqrt(lengthSq())
    fun distanceTo(o: Vec3) = (this - o).length()

    /** 長さ0のときは0を返す(three.js の normalize と同じ) */
    fun normalized(): Vec3 {
        val len = length()
        return if (len == 0.0) this else this * (1.0 / len)
    }

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val X = Vec3(1.0, 0.0, 0.0)
        val Y = Vec3(0.0, 1.0, 0.0)
        val Z = Vec3(0.0, 0.0, 1.0)
    }
}
