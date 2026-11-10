package com.yswy.dessin.core.mesh

import com.yswy.dessin.core.math.Mat4
import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.skeleton.Adjust
import com.yswy.dessin.core.skeleton.BodyParams
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.Side
import com.yswy.dessin.core.skeleton.Skeleton
import com.yswy.dessin.core.skeleton.HIP_ROOT_X
import com.yswy.dessin.core.skeleton.PELVIS_OUTER
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 部位のメッシュの種類。描き分け(関節の玉は消せる、頭の面は色が違う)に使う */
enum class MeshKind { BODY, MARKER, MASK }

/**
 * 部位を形づくるメッシュ1つ。形・線・輪郭はメッシュの空間で持ち、置き方は PartVisuals.placement で得る。
 * @property edges 輪郭線(GL_LINES 用の端点の並び)。無いメッシュは null
 * @property lines 正中線・輪郭の輪の管
 */
class PartMesh(val kind: MeshKind, val mesh: Mesh, val edges: FloatArray?, val lines: List<Mesh>)

/** メッシュの、関節の空間での置き方。拡大は軸ごと */
data class MeshPlacement(val position: Vec3, val rotation: Quat, val scale: Vec3)

/**
 * 部位の見た目(Web版の spec の build)。寸法は Web版を正とする。
 * 線(管)は線の中心から LE だけ外側に置き、面に埋もれないようにする。
 */
object PartVisuals {
    private const val LE = 0.0025
    /** 胸と脇腹の分かれ目の半径(もとの肋骨の錐台を下から 0.105 の高さで切った半径) */
    private const val SPLIT_R = 0.095 + (0.115 - 0.095) * (0.105 / 0.30)
    private val QZ90 = Quat.fromAxisAngle(Vec3.Z, PI / 2)

    private class Entry(val part: PartMesh, val position: Vec3, val rotation: Quat)

    private val table: Map<String, List<Entry>> by lazy { PartSpecs.all.associate { it.id to build(it.id) } }

    fun meshes(id: String): List<PartMesh> = table.getValue(id).map { it.part }

    /**
     * 体型を反映したメッシュの置き方(Web版の setShoulderWidth・setPelvisWidth・updateHips)。
     * 肩は長さ、骨盤は横幅、付け根は長さが変わる。頭の大きさは関節の拡大率で効くのでここでは扱わない。
     */
    fun placement(id: String, index: Int, body: BodyParams): MeshPlacement {
        val e = table.getValue(id)[index]
        val spec = PartSpecs[id]
        val sg = if (spec.side == Side.RIGHT) -1.0 else 1.0
        if (index == 0) {
            when {
                spec.adjust == Adjust.SHOULDER_WIDTH -> {
                    val k = body.shoulderWidth
                    return MeshPlacement(Vec3(sg * 0.06 * k, e.position.y, e.position.z), e.rotation, Vec3(1.0, k, 1.0))
                }
                spec.adjust == Adjust.PELVIS_WIDTH ->
                    return MeshPlacement(e.position, e.rotation, Vec3(body.pelvisWidth, 1.0, 1.0))
                id.startsWith("hip") -> {
                    // 付け根は骨盤の外面より 0.02 外まで見せる(E04-02。骨盤に埋まって選べなかったため)
                    val len = PELVIS_OUTER * body.pelvisWidth + 0.02 - HIP_ROOT_X
                    return MeshPlacement(Vec3(sg * len / 2, e.position.y, e.position.z), e.rotation, Vec3(1.0, len / 0.05, 1.0))
                }
            }
        }
        return MeshPlacement(e.position, e.rotation, Vec3(1.0, 1.0, 1.0))
    }

    private fun build(id: String): List<Entry> {
        val spec = PartSpecs[id]
        val sg = if (spec.side == Side.RIGHT) -1.0 else 1.0
        val base = id.removeSuffix("L").removeSuffix("R").takeIf { spec.side != null } ?: id
        val out = ArrayList<Entry>()
        fun mk(mesh: Mesh, pos: Vec3, rot: Quat = Quat.IDENTITY, edges: Boolean, kind: MeshKind = MeshKind.BODY, lines: List<Mesh> = emptyList()) {
            out += Entry(PartMesh(kind, mesh, if (edges) edges(mesh, 30.0) else null, lines), pos, rot)
        }
        fun marker(r: Double) = mk(sph(r), Vec3.ZERO, edges = false, kind = MeshKind.MARKER)
        when (base) {
            // 胸と脇腹は、もとの肋骨の錐台(上 0.115・下 0.095・高さ 0.30)を上から 6.5:3.5 で分けたもの(E05-02)
            "rib" -> mk(frustum(0.115, SPLIT_R, 0.195, 0.64), Vec3(0.0, 0.0525, 0.0), edges = true, lines = frustumLines(0.115, SPLIT_R, 0.195, 0.64, listOf(0.0075)))
            "flank" -> {
                mk(frustum(SPLIT_R, 0.095, 0.105, 0.64), Vec3(0.0, -0.0525, 0.035), edges = true, lines = frustumLines(SPLIT_R, 0.095, 0.105, 0.64, listOf(0.0275)))
                marker(0.04)
            }
            "neck" -> {
                mk(cyl(0.032, 0.07), Vec3(0.0, 0.035, 0.0), edges = true, lines = cylLines(0.032, 0.07, ring = false))
                marker(0.038)
            }
            "head" -> {
                mk(Primitives.sphere(0.098, 24, 18), Vec3(0.0, 0.115, 0.0), edges = false, lines = headLines(0.1005))
                val mask = Primitives.sphere(0.125, 32, 24, PI / 2 - 0.95, 1.9, PI / 2 - 0.7, 1.9).scaled(1.0, 1.25, 1.0)
                mk(mask, Vec3(0.0, 0.115, 0.0), edges = true, kind = MeshKind.MASK, lines = maskLines())
            }
            "pelvis" -> {
                mk(frustum(0.10, 0.12, 0.14, 0.68), Vec3(0.0, -0.07, 0.035), edges = true, lines = frustumLines(0.10, 0.12, 0.14, 0.68, listOf(0.0)))
                marker(0.04)
            }
            "clav" -> mk(cyl(0.022, 0.12), Vec3(sg * 0.06, 0.0, 0.0), QZ90, edges = true, lines = cylLines(0.022, 0.12, ring = false))
            "upperArm" -> { mk(cyl(0.032, 0.24), Vec3(0.0, -0.125, 0.0), edges = true, lines = cylLines(0.032, 0.24, ring = true)); marker(0.045) }
            "forearm" -> { mk(cyl(0.028, 0.22), Vec3(0.0, -0.11, 0.0), edges = true, lines = cylLines(0.028, 0.22, ring = true)); marker(0.036) }
            "hand" -> {
                // 平たい面が体側を向くように縦軸まわりに 90° 回す(E06-01)
                mk(Primitives.box(0.062, 0.09, 0.024), Vec3(0.0, -0.05, 0.0), Quat.fromAxisAngle(Vec3.Y, PI / 2), edges = true,
                    lines = listOf(tube(listOf(Vec3(0.0, -0.045, 0.012 + LE), Vec3(0.0, 0.045, 0.012 + LE)), closed = false)))
                marker(0.026)
            }
            "hip" -> mk(Primitives.cylinder(0.045, 0.045, 0.05, 20), Vec3(sg * 0.025, 0.0, 0.0), QZ90, edges = true)
            "thigh" -> { mk(cyl(0.052, 0.39), Vec3(0.0, -0.2, 0.0), edges = true, lines = cylLines(0.052, 0.39, ring = true)); marker(0.055) }
            "shin" -> { mk(cyl(0.038, 0.37), Vec3(0.0, -0.19, 0.0), edges = true, lines = cylLines(0.038, 0.37, ring = true)); marker(0.044) }
            "foot" -> {
                mk(Primitives.box(0.075, 0.045, 0.20), Vec3(0.0, -0.04, 0.05), edges = true,
                    lines = listOf(tube(listOf(Vec3(0.0, 0.0225 + LE, -0.10), Vec3(0.0, 0.0225 + LE, 0.10)), closed = false)))
                marker(0.036)
            }
            else -> error("見た目の定義が無い: $id")
        }
        return out
    }

    private fun cyl(r: Double, h: Double) = Primitives.cylinder(r, r, h, 20)
    private fun sph(r: Double) = Primitives.sphere(r, 18, 14)
    private fun frustum(rt: Double, rb: Double, h: Double, k: Double) = Primitives.cylinder(rt, rb, h, 28).scaled(1.0, 1.0, k)

    private fun circle(r: Double, xz: Boolean, y: Double = 0.0) = (0 until 48).map {
        val t = it / 48.0 * 2 * PI
        if (xz) Vec3(cos(t) * r, y, sin(t) * r) else Vec3(0.0, cos(t) * r, sin(t) * r)
    }

    private fun cylLines(r: Double, h: Double, ring: Boolean): List<Mesh> = buildList {
        add(tube(listOf(Vec3(0.0, -h / 2, r + LE), Vec3(0.0, h / 2, r + LE)), closed = false))
        if (ring) add(tube(circle(r + LE, xz = true), closed = true))
    }

    private fun frustumLines(rt: Double, rb: Double, h: Double, k: Double, ys: List<Double>): List<Mesh> = buildList {
        fun rad(y: Double) = rb + (rt - rb) * ((y + h / 2) / h)
        add(tube(listOf(Vec3(0.0, -h / 2, k * rb + LE), Vec3(0.0, h / 2, k * rt + LE)), closed = false))
        add(tube(listOf(Vec3(0.0, -h / 2, -k * rb - LE), Vec3(0.0, h / 2, -k * rt - LE)), closed = false))
        ys.forEach { y ->
            val r = rad(y) + LE
            add(tube((0 until 48).map { val t = it / 48.0 * 2 * PI; Vec3(r * cos(t), y, k * r * sin(t)) }, closed = true))
        }
    }

    private fun headLines(r: Double) = listOf(tube(circle(r, xz = false), closed = true), tube(circle(r, xz = true), closed = true))

    /** 顔の面の縦と横の線 */
    private fun maskLines(): List<Mesh> {
        val r = 0.125 + LE
        val vertical = (0..12).map { q -> val th = (PI / 2 - 0.7) + 1.9 * q / 12; Vec3(0.0, 1.25 * r * cos(th), r * sin(th)) }
        val horizontal = (0..16).map { w -> val ph = (PI / 2 - 0.95) + 1.9 * w / 16; Vec3(-r * cos(ph), 0.0, r * sin(ph)) }
        return listOf(tube(vertical, closed = false), tube(horizontal, closed = false))
    }
}

/** メッシュの空間からワールドへの行列 = 関節のワールド変換 × メッシュの置き方 */
fun meshWorldMatrix(skeleton: Skeleton, id: String, index: Int): Mat4 {
    val j = skeleton.worldTransforms().getValue(id)
    val p = PartVisuals.placement(id, index, skeleton.body)
    return Mat4.compose(j.position, j.rotation, j.scale) * Mat4.compose(p.position, p.rotation, p.scale)
}

/**
 * 置いた部位のメッシュと線を囲む、ワールドの箱(全体を表示に使う。Web版の fitView の Box3)。
 * 各メッシュの箱の8隅をワールドに移して囲む。何も置いていなければ null。
 */
fun fitBounds(skeleton: Skeleton): Pair<Vec3, Vec3>? {
    var lo: Vec3? = null
    var hi: Vec3? = null
    skeleton.placed.forEach { id ->
        PartVisuals.meshes(id).forEachIndexed { i, pm ->
            val m = meshWorldMatrix(skeleton, id, i)
            (listOf(pm.mesh) + pm.lines).mapNotNull { it.bounds() }.forEach { (a, b) ->
                for (corner in 0 until 8) {
                    val c = m.transformPoint(Vec3(if (corner and 1 == 0) a.x else b.x, if (corner and 2 == 0) a.y else b.y, if (corner and 4 == 0) a.z else b.z))
                    lo = lo?.let { Vec3(min(it.x, c.x), min(it.y, c.y), min(it.z, c.z)) } ?: c
                    hi = hi?.let { Vec3(max(it.x, c.x), max(it.y, c.y), max(it.z, c.z)) } ?: c
                }
            }
        }
    }
    val l = lo ?: return null
    return l to hi!!
}
