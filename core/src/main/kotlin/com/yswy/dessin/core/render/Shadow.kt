package com.yswy.dessin.core.render

import com.yswy.dessin.core.math.Mat4
import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.mesh.fitBounds
import com.yswy.dessin.core.skeleton.Skeleton

/**
 * 太陽(平行光)の来る向き。陰影のシェーダ(LitShader の SUN_DIR)と同じ。Web版の sun.position (2.2, 4.5, 3.2)
 */
val SUN_DIRECTION: Vec3 = Vec3(2.2, 4.5, 3.2).normalized()

/**
 * 体に落ちる影(E11)のための、太陽から見た正射影の行列(ビュー × 投影)。
 * 置いた部位を囲む箱がすべて入るように範囲を決める。太陽に近い点ほど奥行き(z)が小さい。
 * 何も置いていなければ null
 */
fun sunLightMatrix(skeleton: Skeleton): Mat4? {
    val (lo, hi) = fitBounds(skeleton) ?: return null
    val center = (lo + hi) * 0.5
    // 箱を囲む球の半径(少し余裕を持たせる)
    val r = (hi - lo).length() / 2 + 0.05
    val eye = center + SUN_DIRECTION * (2 * r)
    // ビューの −z が太陽から中心へ向く(+z が太陽の向き)
    val view = Mat4.compose(eye, Quat.fromUnitVectors(Vec3.Z, SUN_DIRECTION), 1.0).inverseRigid()
    return Mat4.orthographic(r, near = r, far = 3 * r) * view
}
