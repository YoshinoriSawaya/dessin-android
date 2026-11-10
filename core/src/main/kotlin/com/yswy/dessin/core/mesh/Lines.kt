package com.yswy.dessin.core.mesh

import com.yswy.dessin.core.math.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * 線を step ごとに細かく区切る(Web版の densify)。閉じた線は最後の点から最初の点へもつなぎ、終点は足さない。
 */
fun densify(points: List<Vec3>, closed: Boolean, step: Double): List<Vec3> {
    val out = ArrayList<Vec3>()
    val n = points.size
    for (i in 0 until n) {
        val a = points[i]
        if (!closed && i == n - 1) {
            out += a
            break
        }
        val b = points[(i + 1) % n]
        val k = max(1, ceil(a.distanceTo(b) / step).toInt())
        for (j in 0 until k) out += a + (b - a) * (j.toDouble() / k)
    }
    return out
}

/**
 * 線に沿った細い管(正中線・輪郭の輪)。Web版はスプライン曲線に沿う TubeGeometry だが、
 * ここでは densify した折れ線の各点に断面の輪を置いてつなぐ(見た目はほぼ同じ)。端はふさがない。
 */
fun tube(points: List<Vec3>, closed: Boolean, radius: Double = 0.0028, sides: Int = 4, step: Double = 0.015): Mesh {
    val pts = densify(points, closed, step)
    val n = pts.size
    val b = MeshBuilder()
    var normal: Vec3? = null
    for (i in 0 until n) {
        val tangent = tangentAt(pts, i, closed)
        // 前の点の法線を接線に直交するよう運ぶ(ねじれを抑える)
        val prev = normal
        normal = if (prev == null) anyPerpendicular(tangent) else (prev - tangent * prev.dot(tangent)).let { if (it.lengthSq() < 1e-12) anyPerpendicular(tangent) else it.normalized() }
        val binormal = tangent.cross(normal)
        for (j in 0 until sides) {
            val a = j.toDouble() / sides * 2 * PI
            val dir = normal * cos(a) + binormal * sin(a)
            b.vertex(pts[i] + dir * radius, dir)
        }
    }
    val segments = if (closed) n else n - 1
    for (i in 0 until segments) {
        val next = (i + 1) % n
        for (j in 0 until sides) {
            val a = i * sides + j
            val bb = next * sides + j
            val c = next * sides + (j + 1) % sides
            val d = i * sides + (j + 1) % sides
            b.triangle(a, bb, d)
            b.triangle(bb, c, d)
        }
    }
    return b.build()
}

private fun tangentAt(pts: List<Vec3>, i: Int, closed: Boolean): Vec3 {
    val n = pts.size
    val prev = if (closed) pts[(i - 1 + n) % n] else pts[max(0, i - 1)]
    val next = if (closed) pts[(i + 1) % n] else pts[minOf(n - 1, i + 1)]
    return (next - prev).normalized()
}

private fun anyPerpendicular(t: Vec3): Vec3 {
    val axis = when {
        abs(t.x) <= abs(t.y) && abs(t.x) <= abs(t.z) -> Vec3.X
        abs(t.y) <= abs(t.z) -> Vec3.Y
        else -> Vec3.Z
    }
    return t.cross(axis).normalized()
}

/**
 * 輪郭線(three.js r128 の EdgesGeometry)。隣り合う面の法線の角度が thresholdDeg を超える辺と、
 * 面が1枚しかない辺を、線分の端点の並び(GL_LINES 用)で返す。頂点は位置が 1e-4 単位で同じなら同じとみなす。
 */
fun edges(mesh: Mesh, thresholdDeg: Double): FloatArray {
    val thresholdDot = cos(thresholdDeg * PI / 180)
    data class Edge(val v0: Vec3, val v1: Vec3, val normal: Vec3)
    val edgeData = LinkedHashMap<String, Edge?>()
    val out = ArrayList<Float>()
    fun add(a: Vec3, b: Vec3) {
        out += a.x.toFloat(); out += a.y.toFloat(); out += a.z.toFloat()
        out += b.x.toFloat(); out += b.y.toFloat(); out += b.z.toFloat()
    }
    fun hash(v: Vec3) = "${(v.x * 1e4).roundToLong()},${(v.y * 1e4).roundToLong()},${(v.z * 1e4).roundToLong()}"

    val idx = mesh.indices
    for (t in idx.indices step 3) {
        val tri = listOf(mesh.vertex(idx[t]), mesh.vertex(idx[t + 1]), mesh.vertex(idx[t + 2]))
        val normal = (tri[2] - tri[1]).cross(tri[0] - tri[1]).normalized()
        val hashes = tri.map(::hash)
        if (hashes[0] == hashes[1] || hashes[1] == hashes[2] || hashes[2] == hashes[0]) continue
        for (j in 0 until 3) {
            val jNext = (j + 1) % 3
            val key = "${hashes[j]}_${hashes[jNext]}"
            val reverse = "${hashes[jNext]}_${hashes[j]}"
            val sibling = edgeData[reverse]
            if (sibling != null) {
                if (normal.dot(sibling.normal) <= thresholdDot) add(sibling.v0, sibling.v1)
                edgeData[reverse] = null
            } else if (key !in edgeData) {
                edgeData[key] = Edge(tri[j], tri[jNext], normal)
            }
        }
    }
    // 相手の面が無い辺(縁)も線にする
    edgeData.values.filterNotNull().forEach { add(it.v0, it.v1) }
    return out.toFloatArray()
}
