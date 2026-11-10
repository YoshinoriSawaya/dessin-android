package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.Ray
import com.yswy.dessin.core.math.Vec3
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 指の位置(ray)から、部位を向ける方向を求める(Web版の dragPart の前半)。座標はすべてワールド。
 *
 * 関節 center を中心とする半径 radius の球とレイの交点を取り、2つあれば今の玉 currentDot に近いほうを使う。
 * 当たらなければ、レイ上で中心に最も近い点を使う。中心から交点への単位ベクトルを返し、決まらなければ null。
 * 得た方向は、親の空間に直してから aimPart に渡す。
 */
fun dragDirection(ray: Ray, center: Vec3, radius: Double, currentDot: Vec3): Vec3? {
    val oc = ray.origin - center
    val b = oc.dot(ray.direction)
    val c = oc.lengthSq() - radius * radius
    val disc = b * b - c
    val p = if (disc >= 0) {
        val sq = sqrt(disc)
        val p1 = ray.at(-b - sq)
        val p2 = ray.at(-b + sq)
        if (p1.distanceTo(currentDot) <= p2.distanceTo(currentDot)) p1 else p2
    } else {
        ray.at(max(0.0, -b))
    }
    val dw = p - center
    if (dw.lengthSq() < 1e-10) return null
    return dw.normalized()
}
