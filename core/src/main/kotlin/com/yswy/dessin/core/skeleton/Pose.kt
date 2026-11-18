package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3

/**
 * 部位の状態(ラジアン)。phi は休止方向からの振れ角、psi は振る向き、roll は自身の軸まわりのひねり。
 * 可動域へのクランプは入力側(aimPart など)で行う。
 */
data class PartState(val phi: Double, val psi: Double, val roll: Double) {
    companion object {
        /** 向きを戻したときの状態(Web版の resetPart)。ヒンジは psi を曲げる向きにしておく */
        fun rest(spec: PartSpec) = PartState(0.0, spec.hinge?.psi ?: 0.0, 0.0)
    }
}

private val DOWN = Vec3(0.0, -1.0, 0.0)

/**
 * 親の関節から見た部位の回転(Web版の applyState)。
 * 基準フレームの中で、−y 軸まわりに roll ひねってから、−y を dirVec(phi, psi) へ振る。
 */
fun localRotation(spec: PartSpec, state: PartState): Quat {
    val qb = baseQuat(spec.restDir, spec.ref)
    val swing = Quat.fromUnitVectors(DOWN, dirVec(state.phi, state.psi))
    val roll = Quat.fromAxisAngle(DOWN, state.roll)
    return qb * swing * roll * qb.inverse()
}

/**
 * 可動域の表示の上の向き(基準フレーム)。ふつうは dirVec(phi, psi)。
 * ひねりの範囲が曲げ角で変わる部位(すね)は、曲げる面の向きを基準フレームの y 軸まわりにひねりの分だけ回す(E17-04)。
 * 曲げ角が半径方向、ひねりが角度方向の扇形になる。向きは左の外旋(−)で +x、右の外旋(+)で −x
 */
fun guideDir(spec: PartSpec, phi: Double, psi: Double, roll: Double): Vec3 {
    val d = dirVec(phi, psi)
    return if (spec.rollFullBend == null) d else Quat.fromAxisAngle(Vec3.Y, roll).rotate(d)
}

/** 玉(ドラッグする点)の位置。親の空間で、関節からの相対位置 */
fun dotPosition(spec: PartSpec, state: PartState): Vec3 =
    baseQuat(spec.restDir, spec.ref).rotate(guideDir(spec, state.phi, state.psi, state.roll)) * spec.guideRadius
