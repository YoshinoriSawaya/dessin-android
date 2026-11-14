package com.yswy.dessin.core.mesh

import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import kotlin.math.PI

/**
 * 手の形のメッシュ(E08-02)。手の関節の空間で作る(手は −y へ伸びる)。
 * 手のひらは体側(−sg·x。sg は左 +1・右 −1)を向き、親指は前(+z)に付ける(自然に立ったときの向き)。
 * 指の関節は作らず、パー(扇状に開いた指)とグー(ブロック)を差し替える。
 */
internal object HandMeshes {
    private const val D2R = PI / 180

    /** パーの手のひらの下端(手のひらは PartVisuals の箱。高さ 0.09、y = −0.005〜−0.095、幅 z ±0.031) */
    private const val PALM_END = -0.095
    private const val PALM_HALF_WIDTH = 0.031

    private class Piece(val mesh: Mesh, val position: Vec3, val rotation: Quat)

    /** 付け根 base から rotation の向き(−y を回したもの)へ length だけ伸びる箱 */
    private fun segment(base: Vec3, rotation: Quat, thickness: Double, length: Double, width: Double): Piece =
        Piece(Primitives.box(thickness, length, width), base + rotation.rotate(Vec3(0.0, -length / 2, 0.0)), rotation)

    /** x 軸まわりに傾ける。正の角度で前(+z)へ向く */
    private fun tiltForward(deg: Double): Quat = Quat.fromAxisAngle(Vec3.X, -deg * D2R)

    /** パーの4本の指と親指。指は手のひらの下端から扇状に開く */
    fun openFingers(sg: Double): Mesh {
        val pieces = ArrayList<Piece>()
        val centers = listOf(-0.0225, -0.0075, 0.0075, 0.0225)
        for (c in centers) {
            // 外側の指ほど大きく開く(端で 10°)
            pieces += segment(Vec3(0.0, PALM_END, c), tiltForward(10.0 * c / 0.0225), thickness = 0.018, length = 0.065, width = 0.013)
        }
        // 親指は手首に近い前の縁から、前下へ 45° 開く
        pieces += segment(Vec3(-sg * 0.006, -0.03, PALM_HALF_WIDTH), tiltForward(45.0), thickness = 0.018, length = 0.05, width = 0.016)
        return merge(pieces)
    }

    /** グー。指を握ったブロックと、前に添えた親指 */
    fun fist(sg: Double): Mesh = merge(
        listOf(
            Piece(Primitives.box(0.040, 0.075, 0.062), Vec3(-sg * 0.006, -0.0425, 0.0), Quat.IDENTITY),
            Piece(Primitives.box(0.018, 0.045, 0.018), Vec3(-sg * 0.012, -0.04, 0.042), Quat.IDENTITY),
        ),
    )

    /** 置き方を焼き込んで1つのメッシュにまとめる */
    private fun merge(pieces: List<Piece>): Mesh {
        val b = MeshBuilder()
        for (p in pieces) {
            val start = b.vertexCount
            for (i in 0 until p.mesh.vertexCount) {
                val n = Vec3(p.mesh.normals[i * 3].toDouble(), p.mesh.normals[i * 3 + 1].toDouble(), p.mesh.normals[i * 3 + 2].toDouble())
                b.vertex(p.position + p.rotation.rotate(p.mesh.vertex(i)), p.rotation.rotate(n))
            }
            for (t in 0 until p.mesh.indices.size / 3) {
                b.triangle(start + p.mesh.indices[t * 3], start + p.mesh.indices[t * 3 + 1], start + p.mesh.indices[t * 3 + 2])
            }
        }
        return b.build()
    }
}
