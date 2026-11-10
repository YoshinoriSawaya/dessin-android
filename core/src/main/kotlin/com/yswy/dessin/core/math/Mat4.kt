package com.yswy.dessin.core.math

import kotlin.math.tan

/**
 * 4×4 行列。要素は列優先(OpenGL・three.js の elements と同じ並び)。行 r・列 c は elements[c * 4 + r]。
 * 描画に渡すための型で、ロジック層の計算は Vec3・Quat で行う。
 */
class Mat4(val elements: DoubleArray) {
    init {
        require(elements.size == 16) { "要素は16個" }
    }

    operator fun get(row: Int, col: Int): Double = elements[col * 4 + row]

    operator fun times(b: Mat4): Mat4 {
        val out = DoubleArray(16)
        for (c in 0 until 4) for (r in 0 until 4) {
            var s = 0.0
            for (k in 0 until 4) s += this[r, k] * b[k, c]
            out[c * 4 + r] = s
        }
        return Mat4(out)
    }

    /** 点に掛けて w で割る(three.js の applyMatrix4)。透視投影なら正規化デバイス座標になる */
    fun transformPoint(p: Vec3): Vec3 {
        val x = this[0, 0] * p.x + this[0, 1] * p.y + this[0, 2] * p.z + this[0, 3]
        val y = this[1, 0] * p.x + this[1, 1] * p.y + this[1, 2] * p.z + this[1, 3]
        val z = this[2, 0] * p.x + this[2, 1] * p.y + this[2, 2] * p.z + this[2, 3]
        val w = this[3, 0] * p.x + this[3, 1] * p.y + this[3, 2] * p.z + this[3, 3]
        return Vec3(x / w, y / w, z / w)
    }

    /** 透視投影の行列なら、点を正規化デバイス座標に移す */
    fun project(p: Vec3): Vec3 = transformPoint(p)

    /** 回転と平行移動だけの行列(拡大なし)の逆 */
    fun inverseRigid(): Mat4 {
        val e = DoubleArray(16)
        for (r in 0 until 3) for (c in 0 until 3) e[c * 4 + r] = this[c, r]
        val t = Vec3(this[0, 3], this[1, 3], this[2, 3])
        for (r in 0 until 3) e[12 + r] = -(this[0, r] * t.x + this[1, r] * t.y + this[2, r] * t.z)
        e[15] = 1.0
        return Mat4(e)
    }

    fun toFloatArray(): FloatArray = FloatArray(16) { elements[it].toFloat() }

    companion object {
        val IDENTITY = Mat4(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0))

        /** 位置・回転・等方の拡大から作る(three.js の Matrix4.compose) */
        fun compose(position: Vec3, rotation: Quat, scale: Double): Mat4 {
            val ax = rotation.rotate(Vec3.X) * scale
            val ay = rotation.rotate(Vec3.Y) * scale
            val az = rotation.rotate(Vec3.Z) * scale
            return Mat4(
                doubleArrayOf(
                    ax.x, ax.y, ax.z, 0.0,
                    ay.x, ay.y, ay.z, 0.0,
                    az.x, az.y, az.z, 0.0,
                    position.x, position.y, position.z, 1.0,
                ),
            )
        }

        /** 透視投影(three.js の PerspectiveCamera.updateProjectionMatrix)。fovY は縦の視野角(ラジアン) */
        fun perspective(fovY: Double, aspect: Double, near: Double, far: Double): Mat4 {
            val top = near * tan(fovY / 2)
            val right = top * aspect
            val e = DoubleArray(16)
            e[0] = near / right
            e[5] = near / top
            e[10] = -(far + near) / (far - near)
            e[11] = -1.0
            e[14] = -2 * far * near / (far - near)
            return Mat4(e)
        }
    }
}
