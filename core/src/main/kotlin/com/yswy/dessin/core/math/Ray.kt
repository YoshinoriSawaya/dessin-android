package com.yswy.dessin.core.math

/** 半直線。direction は正規化済みを前提にする */
data class Ray(val origin: Vec3, val direction: Vec3) {
    fun at(t: Double) = origin + direction * t
}
