package com.yswy.dessin.core.math

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 回転を表す四元数。three.js の Quaternion と同じ式で計算する。不変。
 * `a * b` は b を先に、a を後に適用する回転。
 */
data class Quat(val x: Double, val y: Double, val z: Double, val w: Double) {
    operator fun times(b: Quat) = Quat(
        x * b.w + w * b.x + y * b.z - z * b.y,
        y * b.w + w * b.y + z * b.x - x * b.z,
        z * b.w + w * b.z + x * b.y - y * b.x,
        w * b.w - x * b.x - y * b.y - z * b.z,
    )

    /** 単位四元数を前提に、共役を逆元として返す(three.js の invert と同じ) */
    fun inverse() = Quat(-x, -y, -z, w)

    fun normalized(): Quat {
        val len = sqrt(x * x + y * y + z * z + w * w)
        return if (len == 0.0) IDENTITY else Quat(x / len, y / len, z / len, w / len)
    }

    /** ベクトルを回転する(three.js の applyQuaternion) */
    fun rotate(v: Vec3): Vec3 {
        val tx = 2 * (y * v.z - z * v.y)
        val ty = 2 * (z * v.x - x * v.z)
        val tz = 2 * (x * v.y - y * v.x)
        return Vec3(
            v.x + w * tx + y * tz - z * ty,
            v.y + w * ty + z * tx - x * tz,
            v.z + w * tz + x * ty - y * tx,
        )
    }

    /**
     * オイラー角(順序 YXZ、ラジアン)に分解する(three.js の Euler.setFromRotationMatrix)。
     * x が ±90度のときは z を 0 にして y で表す。
     */
    fun toEulerYXZ(): Vec3 {
        val m11 = 1 - 2 * (y * y + z * z)
        val m13 = 2 * (x * z + w * y)
        val m21 = 2 * (x * y + w * z)
        val m22 = 1 - 2 * (x * x + z * z)
        val m23 = 2 * (y * z - w * x)
        val m31 = 2 * (x * z - w * y)
        val m33 = 1 - 2 * (x * x + y * y)
        val ex = asin(-m23.coerceIn(-1.0, 1.0))
        return if (abs(m23) < 0.9999999) Vec3(ex, atan2(m13, m33), atan2(m21, m22))
        else Vec3(ex, atan2(-m31, m11), 0.0)
    }

    companion object {
        val IDENTITY = Quat(0.0, 0.0, 0.0, 1.0)

        /** オイラー角(順序 YXZ、ラジアン)から作る。回転は Ry·Rx·Rz(three.js の setFromEuler) */
        fun fromEulerYXZ(e: Vec3): Quat {
            val c1 = cos(e.x / 2); val s1 = sin(e.x / 2)
            val c2 = cos(e.y / 2); val s2 = sin(e.y / 2)
            val c3 = cos(e.z / 2); val s3 = sin(e.z / 2)
            return Quat(
                s1 * c2 * c3 + c1 * s2 * s3,
                c1 * s2 * c3 - s1 * c2 * s3,
                c1 * c2 * s3 - s1 * s2 * c3,
                c1 * c2 * c3 + s1 * s2 * s3,
            )
        }

        /** 軸まわりに angle(ラジアン)回す。軸は正規化してから使う */
        fun fromAxisAngle(axis: Vec3, angle: Double): Quat {
            val a = axis.normalized()
            val s = sin(angle / 2)
            return Quat(a.x * s, a.y * s, a.z * s, cos(angle / 2))
        }

        /** 単位ベクトル from を to に向ける最短の回転。正反対のときは直交する軸で180度回す */
        fun fromUnitVectors(from: Vec3, to: Vec3): Quat {
            val r = from.dot(to) + 1
            val q = if (r < 1e-8) {
                if (abs(from.x) > abs(from.z)) Quat(-from.y, from.x, 0.0, 0.0)
                else Quat(0.0, -from.z, from.y, 0.0)
            } else {
                val c = from.cross(to)
                Quat(c.x, c.y, c.z, r)
            }
            return q.normalized()
        }

        /** x・y・z 軸の行き先(正規直交基底)から回転を作る。three.js の makeBasis → setFromRotationMatrix */
        fun fromBasis(bx: Vec3, by: Vec3, bz: Vec3): Quat {
            val m11 = bx.x; val m12 = by.x; val m13 = bz.x
            val m21 = bx.y; val m22 = by.y; val m23 = bz.y
            val m31 = bx.z; val m32 = by.z; val m33 = bz.z
            val trace = m11 + m22 + m33
            return when {
                trace > 0 -> {
                    val s = 0.5 / sqrt(trace + 1.0)
                    Quat((m32 - m23) * s, (m13 - m31) * s, (m21 - m12) * s, 0.25 / s)
                }
                m11 > m22 && m11 > m33 -> {
                    val s = 2.0 * sqrt(1.0 + m11 - m22 - m33)
                    Quat(0.25 * s, (m12 + m21) / s, (m13 + m31) / s, (m32 - m23) / s)
                }
                m22 > m33 -> {
                    val s = 2.0 * sqrt(1.0 + m22 - m11 - m33)
                    Quat((m12 + m21) / s, 0.25 * s, (m23 + m32) / s, (m13 - m31) / s)
                }
                else -> {
                    val s = 2.0 * sqrt(1.0 + m33 - m11 - m22)
                    Quat((m13 + m31) / s, (m23 + m32) / s, 0.25 * s, (m21 - m12) / s)
                }
            }
        }
    }
}
