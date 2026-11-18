package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

private val DOWN = Vec3(0.0, -1.0, 0.0)

/**
 * 回転オーブの操作を部位に加える(Web版の sphereRotate)。rotInParent は親の空間での回転で、
 * ワールドの回転からの変換(pq⁻¹·dw·pq)は呼び出し側で行う。
 *
 * 加えたあとの回転を基準フレームでスイングとツイストに分け、スイングは aimPart と同じ規則で可動域に、
 * ツイストは [−π, π) に折り返してからロールの範囲に収める。
 */
fun orbRotate(spec: PartSpec, current: PartState, rotInParent: Quat): PartState {
    val qb = baseQuat(spec.restDir, spec.ref)
    val rb = qb.inverse() * (rotInParent * localRotation(spec, current)) * qb
    // −y 軸まわりのツイストを取り出す
    val sAx = DOWN.x * rb.x + DOWN.y * rb.y + DOWN.z * rb.z
    val roll = wrapPi(2 * atan2(sAx, rb.w))
    val tl = sqrt(sAx * sAx + rb.w * rb.w)
    val twist = if (tl < 1e-6) Quat.IDENTITY else Quat(DOWN.x * sAx / tl, DOWN.y * sAx / tl, DOWN.z * sAx / tl, rb.w / tl)
    val swing = rb * twist.inverse()
    val aimed = aimPart(spec, current, qb.rotate(swing.rotate(DOWN)))
    val range = rollRange(spec, aimed.phi)
    return aimed.copy(roll = roll.coerceIn(range.min, range.max))
}

/** 根(胸)は可動域が無いので、ワールドの回転をそのまま後から掛ける */
fun orbRotateRoot(current: Quat, rotInWorld: Quat): Quat = rotInWorld * current

private fun wrapPi(a: Double): Double {
    val t = 2 * PI
    return ((a + PI) % t + t) % t - PI
}
