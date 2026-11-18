package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 部位の基準フレーム(Web版の baseQuat)。
 * フレームの −y が休止方向 restDir、z はできるだけ ref に寄せる。restDir と ref が平行だと決まらない。
 */
fun baseQuat(restDir: Vec3, ref: Vec3): Quat {
    val by = -restDir.normalized()
    val bx = by.cross(ref).normalized()
    val bz = bx.cross(by)
    return Quat.fromBasis(bx, by, bz)
}

/**
 * 基準フレームでの向き(Web版の dirVec)。
 * phi は休止方向(−y)からの振れ角、psi は振る向きで、0 が −z、π/2 が +x。
 */
fun dirVec(phi: Double, psi: Double): Vec3 {
    val sp = sin(phi)
    return Vec3(sp * sin(psi), -cos(phi), -sp * cos(psi))
}

/**
 * 向き psi で振れる角度の上限(Web版の phiMax)。
 * 4象限それぞれで、隣り合う2方向の値を半径とする楕円にする。上限は π。
 */
fun phiMax(cone: Cone, psi: Double): Double {
    val s = sin(psi)
    val c = cos(psi)
    // 0度の方向は 0除算を避けて 0.001 ラジアンにする(Web版と同じ)
    val su = max(0.001, if (s >= 0) cone.plusX else cone.minusX)
    val sw = max(0.001, if (c >= 0) cone.back else cone.front)
    val m = 1 / sqrt((s / su) * (s / su) + (c / sw) * (c / sw))
    return min(PI, m)
}

/** ヒンジなら曲げ角の最大値、そうでなければコーンで求める */
fun phiMax(spec: PartSpec, psi: Double): Double {
    spec.hinge?.let { return it.max }
    val cone = requireNotNull(spec.cone) { "${spec.id} は可動域を持たない" }
    return phiMax(cone, psi)
}

/**
 * 曲げ角 phi のときのひねりの範囲(E17-01)。ふつうは定義の roll のまま。
 * すねは伸ばしていると 0 で、rollFullBend まで曲げ角に比例して広がる
 */
fun rollRange(spec: PartSpec, phi: Double): RollRange {
    val full = spec.rollFullBend ?: return spec.roll
    val k = (phi / full).coerceIn(0.0, 1.0)
    return RollRange(spec.roll.min * k, spec.roll.max * k)
}

/** ひねりを曲げ角の範囲に収める */
fun clampRoll(spec: PartSpec, state: PartState): PartState {
    val r = rollRange(spec, state.phi)
    return state.copy(roll = state.roll.coerceIn(r.min, r.max))
}
