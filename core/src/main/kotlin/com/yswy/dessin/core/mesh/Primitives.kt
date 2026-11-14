package com.yswy.dessin.core.mesh

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 基本の形。頂点の並びと三角形の向きは three.js r128 の BoxGeometry・CylinderGeometry・SphereGeometry と同じ
 * (v1 で Web版と照合した)。分割は1段だけ、uv は使わないので作らない。
 */
object Primitives {
    /** 中心が原点の箱(BoxGeometry(width, height, depth)) */
    fun box(width: Double, height: Double, depth: Double): Mesh {
        val b = MeshBuilder()
        // 面ごとに (u, v, w) の軸と向き。three.js の buildPlane の呼び出し順(+x, −x, +y, −y, +z, −z)
        plane(b, 2, 1, 0, -1.0, -1.0, depth, height, width)
        plane(b, 2, 1, 0, 1.0, -1.0, depth, height, -width)
        plane(b, 0, 2, 1, 1.0, 1.0, width, depth, height)
        plane(b, 0, 2, 1, 1.0, -1.0, width, depth, -height)
        plane(b, 0, 1, 2, 1.0, -1.0, width, height, depth)
        plane(b, 0, 1, 2, -1.0, -1.0, width, height, -depth)
        return b.build()
    }

    private fun plane(b: MeshBuilder, u: Int, v: Int, w: Int, udir: Double, vdir: Double, width: Double, height: Double, depth: Double) {
        val start = b.vertexCount
        for (iy in 0..1) {
            val y = iy * height - height / 2
            for (ix in 0..1) {
                val x = ix * width - width / 2
                val p = DoubleArray(3)
                p[u] = x * udir
                p[v] = y * vdir
                p[w] = depth / 2
                val n = DoubleArray(3)
                n[w] = if (depth > 0) 1.0 else -1.0
                b.vertex(p[0], p[1], p[2], n[0], n[1], n[2])
            }
        }
        val a = start
        val bb = start + 2
        val c = start + 3
        val d = start + 1
        b.triangle(a, bb, d)
        b.triangle(bb, c, d)
    }

    /** y 軸に沿った円柱・円錐台(CylinderGeometry(radiusTop, radiusBottom, height, radialSegments)) */
    fun cylinder(radiusTop: Double, radiusBottom: Double, height: Double, radialSegments: Int): Mesh {
        val b = MeshBuilder()
        val half = height / 2
        val slope = (radiusBottom - radiusTop) / height
        val rows = Array(2) { IntArray(radialSegments + 1) }
        for (y in 0..1) {
            val v = y.toDouble()
            val radius = v * (radiusBottom - radiusTop) + radiusTop
            for (x in 0..radialSegments) {
                val theta = x.toDouble() / radialSegments * 2 * PI
                val s = sin(theta)
                val c = cos(theta)
                val len = sqrt(s * s + slope * slope + c * c)
                rows[y][x] = b.vertexCount
                b.vertex(radius * s, -v * height + half, radius * c, s / len, slope / len, c / len)
            }
        }
        for (x in 0 until radialSegments) {
            val a = rows[0][x]
            val bb = rows[1][x]
            val c = rows[1][x + 1]
            val d = rows[0][x + 1]
            b.triangle(a, bb, d)
            b.triangle(bb, c, d)
        }
        if (radiusTop > 0) cap(b, true, radiusTop, half, radialSegments)
        if (radiusBottom > 0) cap(b, false, radiusBottom, half, radialSegments)
        return b.build()
    }

    private fun cap(b: MeshBuilder, top: Boolean, radius: Double, half: Double, radialSegments: Int) {
        val sign = if (top) 1.0 else -1.0
        val centerStart = b.vertexCount
        repeat(radialSegments) { b.vertex(0.0, half * sign, 0.0, 0.0, sign, 0.0) }
        val ringStart = b.vertexCount
        for (x in 0..radialSegments) {
            val theta = x.toDouble() / radialSegments * 2 * PI
            b.vertex(radius * sin(theta), half * sign, radius * cos(theta), 0.0, sign, 0.0)
        }
        for (x in 0 until radialSegments) {
            val c = centerStart + x
            val i = ringStart + x
            if (top) b.triangle(i, i + 1, c) else b.triangle(i + 1, i, c)
        }
    }

    /**
     * y 軸まわりに輪郭を回した形(旋盤体。E08-01)。profile は下から上への (y, 半径) の並び。上下はふたで閉じる。
     * 側面の法線は輪郭の傾きから求める(なめらかにつなぐ)。
     */
    fun lathe(profile: List<Pair<Double, Double>>, radialSegments: Int): Mesh {
        require(profile.size >= 2) { "輪郭は2点以上" }
        val b = MeshBuilder()
        val rows = Array(profile.size) { IntArray(radialSegments + 1) }
        for ((i, pr) in profile.withIndex()) {
            val (y, r) = pr
            // 輪郭の接線(前後の点の差)に垂直な向きが法線。(dr, dy) の接線に対し、外向きは (dy, −dr)
            val (y0, r0) = profile[maxOf(i - 1, 0)]
            val (y1, r1) = profile[minOf(i + 1, profile.size - 1)]
            val ny0 = -(r1 - r0)
            val nr0 = y1 - y0
            val len = sqrt(nr0 * nr0 + ny0 * ny0)
            val nr = nr0 / len
            val ny = ny0 / len
            for (x in 0..radialSegments) {
                val theta = x.toDouble() / radialSegments * 2 * PI
                val s = sin(theta)
                val c = cos(theta)
                rows[i][x] = b.vertexCount
                b.vertex(r * s, y, r * c, nr * s, ny, nr * c)
            }
        }
        for (i in 0 until profile.size - 1) {
            for (x in 0 until radialSegments) {
                val lo0 = rows[i][x]
                val lo1 = rows[i][x + 1]
                val hi0 = rows[i + 1][x]
                val hi1 = rows[i + 1][x + 1]
                b.triangle(hi0, lo0, hi1)
                b.triangle(lo0, lo1, hi1)
            }
        }
        lidOf(b, profile.last(), top = true, radialSegments)
        lidOf(b, profile.first(), top = false, radialSegments)
        return b.build()
    }

    private fun lidOf(b: MeshBuilder, at: Pair<Double, Double>, top: Boolean, radialSegments: Int) {
        val (y, r) = at
        val ny = if (top) 1.0 else -1.0
        val center = b.vertexCount
        b.vertex(0.0, y, 0.0, 0.0, ny, 0.0)
        for (x in 0..radialSegments) {
            val theta = x.toDouble() / radialSegments * 2 * PI
            b.vertex(r * sin(theta), y, r * cos(theta), 0.0, ny, 0.0)
        }
        for (x in 0 until radialSegments) {
            if (top) b.triangle(center + 1 + x, center + 2 + x, center) else b.triangle(center + 2 + x, center + 1 + x, center)
        }
    }

    /**
     * 球、または球の一部(SphereGeometry)。phi は y 軸まわり、theta は +y からの角度。
     * 頂点は (−r cos φ sin θ, r cos θ, r sin φ sin θ)。
     */
    fun sphere(
        radius: Double,
        widthSegments: Int,
        heightSegments: Int,
        phiStart: Double = 0.0,
        phiLength: Double = 2 * PI,
        thetaStart: Double = 0.0,
        thetaLength: Double = PI,
    ): Mesh {
        val b = MeshBuilder()
        val thetaEnd = min(thetaStart + thetaLength, PI)
        val grid = Array(heightSegments + 1) { IntArray(widthSegments + 1) }
        for (iy in 0..heightSegments) {
            val v = iy.toDouble() / heightSegments
            for (ix in 0..widthSegments) {
                val u = ix.toDouble() / widthSegments
                val x = -radius * cos(phiStart + u * phiLength) * sin(thetaStart + v * thetaLength)
                val y = radius * cos(thetaStart + v * thetaLength)
                val z = radius * sin(phiStart + u * phiLength) * sin(thetaStart + v * thetaLength)
                val len = sqrt(x * x + y * y + z * z)
                val k = if (len == 0.0) 0.0 else 1 / len
                grid[iy][ix] = b.vertexCount
                b.vertex(x, y, z, x * k, y * k, z * k)
            }
        }
        for (iy in 0 until heightSegments) {
            for (ix in 0 until widthSegments) {
                val a = grid[iy][ix + 1]
                val bb = grid[iy][ix]
                val c = grid[iy + 1][ix]
                val d = grid[iy + 1][ix + 1]
                if (iy != 0 || thetaStart > 0) b.triangle(a, bb, d)
                if (iy != heightSegments - 1 || thetaEnd < PI) b.triangle(bb, c, d)
            }
        }
        return b.build()
    }
}
