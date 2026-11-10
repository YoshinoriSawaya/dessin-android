package com.yswy.dessin.core.mesh

import com.yswy.dessin.core.skeleton.PartSpec
import com.yswy.dessin.core.skeleton.baseQuat
import com.yswy.dessin.core.skeleton.dirVec
import com.yswy.dessin.core.skeleton.phiMax
import kotlin.math.PI

/**
 * 可動域の表示(Web版の buildGuide)。親の空間で、関節を原点として置く。
 * @property area 緑の面(法線なし。半透明で両面を描く)
 * @property outline 縁の線(閉じた折れ線の頂点の並び)
 */
class GuideMesh(val area: Mesh, val outline: FloatArray)

/** 可動域の表示を作る。コーンは phiMax に沿った面、ヒンジは曲げる面の扇 */
fun guideMesh(spec: PartSpec): GuideMesh {
    val qb = baseQuat(spec.restDir, spec.ref)
    val rv = spec.guideRadius
    fun pt(phi: Double, psi: Double) = qb.rotate(dirVec(phi, psi)) * rv
    val b = MeshBuilder()
    val outline = ArrayList<Float>()
    fun line(x: Double, y: Double, z: Double) { outline += x.toFloat(); outline += y.toFloat(); outline += z.toFloat() }
    val hinge = spec.hinge
    if (hinge != null) {
        val n = 30
        b.vertex(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        line(0.0, 0.0, 0.0)
        for (i in 0..n) {
            val a = pt(hinge.min + (hinge.max - hinge.min) * i / n, hinge.psi)
            b.vertex(a.x, a.y, a.z, 0.0, 0.0, 0.0)
            line(a.x, a.y, a.z)
        }
        for (i in 1..n) b.triangle(0, i, i + 1)
        line(0.0, 0.0, 0.0)
    } else {
        val rings = 8
        val m = 64
        val c = pt(0.0, 0.0)
        b.vertex(c.x, c.y, c.z, 0.0, 0.0, 0.0)
        for (k in 1..rings) {
            for (j in 0 until m) {
                val psi = j.toDouble() / m * 2 * PI
                val p = pt(phiMax(spec, psi) * k / rings, psi)
                b.vertex(p.x, p.y, p.z, 0.0, 0.0, 0.0)
                if (k == rings) line(p.x, p.y, p.z)
            }
        }
        for (j in 0 until m) b.triangle(0, 1 + j, 1 + (j + 1) % m)
        for (k in 1 until rings) {
            for (j in 0 until m) {
                val i0 = 1 + (k - 1) * m + j
                val i1 = 1 + (k - 1) * m + (j + 1) % m
                val i2 = 1 + k * m + j
                val i3 = 1 + k * m + (j + 1) % m
                b.triangle(i0, i2, i1)
                b.triangle(i1, i2, i3)
            }
        }
    }
    return GuideMesh(b.build(withNormals = false), outline.toFloatArray())
}

/**
 * 玉のワールドでの半径。視点からの距離に比例させ、画面上でほぼ同じ大きさに見せる(Web版の updateGuide)。
 * 白い縁はこの 1.35 倍。
 */
fun dotWorldRadius(distanceToCamera: Double): Double = 0.03 * distanceToCamera / 4.3 * 1.6

/** 玉の白い縁の、玉に対する倍率 */
const val DOT_RING_SCALE = 1.35
