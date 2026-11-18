package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min

/**
 * 部位を親の空間での方向 dirInParent へ向けた状態を、可動域に収めて返す(Web版の aimPart)。
 * ロールは曲げ角で範囲が変わる部位(すね。E17-01)だけ、その範囲に収める。ワールドの方向から親の空間への変換は呼び出し側で行う。
 *
 * - ヒンジ: 曲げる面に射影した角度を [min, max] に収める。psi は変えない
 * - コーン: 向き psi はそのままに、振れ角 phi を phiMax(psi) までに収める。
 *   休止方向とほぼ同じ向き(phi ≤ 0.001)なら psi は前の値を保つ
 */
fun aimPart(spec: PartSpec, current: PartState, dirInParent: Vec3): PartState {
    val d = baseQuat(spec.restDir, spec.ref).inverse().rotate(dirInParent.normalized())
    val hinge = spec.hinge
    if (hinge != null && spec.rollFullBend != null) {
        // 扇形の可動域(すね。E17-04)は曲げ角が半径方向なので、軸(−y)からの角度を曲げ角にする。
        // 玉はひねりの分だけ回して出しているので、その分を戻して曲げる側(後ろ)か反対側かを決める
        val u = Quat.fromAxisAngle(Vec3.Y, -current.roll).rotate(d)
        val a = acos((-d.y).coerceIn(-1.0, 1.0))
        val bendSide = u.z * -cos(hinge.psi) >= 0.0
        return clampRoll(spec, current.copy(phi = (if (bendSide) a else -a).coerceIn(hinge.min, hinge.max)))
    }
    if (hinge != null) {
        val hx = -cos(hinge.psi)
        val phi = atan2(d.z * hx, -d.y).coerceIn(hinge.min, hinge.max)
        return clampRoll(spec, current.copy(phi = phi))
    }
    val phi = acos((-d.y).coerceIn(-1.0, 1.0))
    val psi = if (phi > 1e-3) atan2(d.x, -d.z) else current.psi
    return current.copy(phi = min(phi, phiMax(spec, psi)), psi = psi)
}
