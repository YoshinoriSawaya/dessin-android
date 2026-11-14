package com.yswy.dessin.core.mesh

import com.yswy.dessin.core.math.Vec3
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 三角形のメッシュ。GL にそのまま渡せる並び(xyz を続けた float、三角形ごとに3つの頂点番号)。
 * 法線を持たない面(可動域の表示)は normals が空。
 */
class Mesh(val positions: FloatArray, val normals: FloatArray, val indices: IntArray) {
    val vertexCount: Int get() = positions.size / 3

    fun vertex(i: Int) = Vec3(positions[i * 3].toDouble(), positions[i * 3 + 1].toDouble(), positions[i * 3 + 2].toDouble())

    /**
     * 軸ごとに拡大した形(three.js の BufferGeometry.scale)。
     * 法線は逆の比で傾けて正規化する(法線行列 = 拡大の逆の転置)。
     */
    fun scaled(sx: Double, sy: Double, sz: Double): Mesh {
        val p = FloatArray(positions.size)
        for (i in 0 until vertexCount) {
            p[i * 3] = (positions[i * 3] * sx).toFloat()
            p[i * 3 + 1] = (positions[i * 3 + 1] * sy).toFloat()
            p[i * 3 + 2] = (positions[i * 3 + 2] * sz).toFloat()
        }
        val n = FloatArray(normals.size)
        for (i in 0 until normals.size / 3) {
            val x = normals[i * 3] / sx
            val y = normals[i * 3 + 1] / sy
            val z = normals[i * 3 + 2] / sz
            val len = sqrt(x * x + y * y + z * z)
            val k = if (len == 0.0) 0.0 else 1 / len
            n[i * 3] = (x * k).toFloat()
            n[i * 3 + 1] = (y * k).toFloat()
            n[i * 3 + 2] = (z * k).toFloat()
        }
        return Mesh(p, n, indices)
    }

    /** 平行移動した形(法線は変わらない) */
    fun translated(d: Vec3): Mesh {
        val p = FloatArray(positions.size)
        for (i in 0 until vertexCount) {
            p[i * 3] = (positions[i * 3] + d.x).toFloat()
            p[i * 3 + 1] = (positions[i * 3 + 1] + d.y).toFloat()
            p[i * 3 + 2] = (positions[i * 3 + 2] + d.z).toFloat()
        }
        return Mesh(p, normals, indices)
    }

    /** 頂点を囲む箱 (最小, 最大)。頂点が無ければ null */
    fun bounds(): Pair<Vec3, Vec3>? {
        if (vertexCount == 0) return null
        var lo = vertex(0)
        var hi = lo
        for (i in 1 until vertexCount) {
            val v = vertex(i)
            lo = Vec3(min(lo.x, v.x), min(lo.y, v.y), min(lo.z, v.z))
            hi = Vec3(max(hi.x, v.x), max(hi.y, v.y), max(hi.z, v.z))
        }
        return lo to hi
    }
}

/** 頂点・法線・インデックスを順に積み上げて Mesh を作る */
internal class MeshBuilder {
    private val positions = ArrayList<Double>()
    private val normals = ArrayList<Double>()
    private val indices = ArrayList<Int>()

    val vertexCount: Int get() = positions.size / 3

    fun vertex(x: Double, y: Double, z: Double, nx: Double, ny: Double, nz: Double) {
        positions += x; positions += y; positions += z
        normals += nx; normals += ny; normals += nz
    }

    fun vertex(p: Vec3, n: Vec3) = vertex(p.x, p.y, p.z, n.x, n.y, n.z)

    fun triangle(a: Int, b: Int, c: Int) {
        indices += a; indices += b; indices += c
    }

    fun build(withNormals: Boolean = true) = Mesh(
        FloatArray(positions.size) { positions[it].toFloat() },
        if (withNormals) FloatArray(normals.size) { normals[it].toFloat() } else FloatArray(0),
        indices.toIntArray(),
    )
}
