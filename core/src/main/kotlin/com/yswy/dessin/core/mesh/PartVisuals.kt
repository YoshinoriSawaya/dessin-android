package com.yswy.dessin.core.mesh

import com.yswy.dessin.core.math.Mat4
import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.skeleton.Adjust
import com.yswy.dessin.core.skeleton.BodyParams
import com.yswy.dessin.core.skeleton.HandShape
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.Sex
import com.yswy.dessin.core.skeleton.Side
import com.yswy.dessin.core.skeleton.Skeleton
import com.yswy.dessin.core.skeleton.HIP_ROOT_X
import com.yswy.dessin.core.skeleton.PELVIS_OUTER
import com.yswy.dessin.core.skeleton.lengthScale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.tan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 部位のメッシュの種類。描き分け(関節の玉は消せる、頭の面は色が違う)に使う */
enum class MeshKind { BODY, MARKER, MASK }

/** 体型で出し分ける体の形(E08-04)。乳房は大きさが 0 なら描かず、性器は性別の形だけを描く */
enum class BodyFeature { BREAST, MALE, FEMALE }

/**
 * 部位を形づくるメッシュ1つ。形・線・輪郭はメッシュの空間で持ち、置き方は PartVisuals.placement で得る。
 * @property edges 輪郭線(GL_LINES 用の端点の並び)。無いメッシュは null
 * @property lines 正中線・輪郭の輪の管
 * @property handShape 手の形のどちらで描くか(E08-02)。null なら常に描く
 * @property feature 体型で出し分ける体の形(E08-04)。null なら常に描く
 */
class PartMesh(
    val kind: MeshKind,
    val mesh: Mesh,
    val edges: FloatArray?,
    val lines: List<Mesh>,
    val handShape: HandShape? = null,
    val feature: BodyFeature? = null,
)

/** メッシュの、関節の空間での置き方。拡大は軸ごと */
data class MeshPlacement(val position: Vec3, val rotation: Quat, val scale: Vec3)

/**
 * 部位の見た目(Web版の spec の build)。寸法は Web版を正とする。
 * 線(管)は線の中心から LE だけ外側に置き、面に埋もれないようにする。
 */
object PartVisuals {
    private const val LE = 0.0025
    /** 胸の前後の厚みの、横幅に対する比 */
    private const val CHEST_DEPTH = 0.64
    /** 胸の下端(みぞおちの高さ、胸の関節の空間で y = −0.045)と上端(0.15) */
    private const val CHEST_BOTTOM = -0.045
    private const val CHEST_TOP = 0.15
    /** 胸が最も広い高さと、その半径 */
    private const val CHEST_WIDEST_Y = 0.03
    private const val CHEST_WIDEST_R = 0.12

    /**
     * 胸の輪郭の半径(E08-01)。肋骨のように、中ほど(y = 0.03)で最も広く、上(0.085)と下(0.106)へ狭まる。
     * 上下それぞれ放物線で、最も広い高さでなめらかにつなぐ。
     */
    internal fun chestRadius(y: Double): Double =
        if (y >= CHEST_WIDEST_Y) {
            val t = (y - CHEST_WIDEST_Y) / (CHEST_TOP - CHEST_WIDEST_Y)
            CHEST_WIDEST_R - 0.035 * t * t
        } else {
            val t = (CHEST_WIDEST_Y - y) / (CHEST_WIDEST_Y - CHEST_BOTTOM)
            CHEST_WIDEST_R - 0.014 * t * t
        }

    /** 胸の上端の切り口の角度(E14-02): 前へ 25°、中央から肩へ 15° 下がる */
    private const val CHEST_CUT_FRONT = 25 * PI / 180
    private const val CHEST_CUT_SIDE = 15 * PI / 180
    /** 胸の上端の背中側の縁の z。切り口はここで CHEST_TOP の高さ */
    private val CHEST_BACK_Z = -CHEST_DEPTH * chestRadius(CHEST_TOP)

    /** 胸と脇腹の分かれ目の半径(胸の下端) */
    private val SPLIT_R = chestRadius(CHEST_BOTTOM)
    private val QZ90 = Quat.fromAxisAngle(Vec3.Z, PI / 2)
    /** 乳房の大きさ 1 のときの半径 */
    private const val BREAST_R = 0.055
    private const val CHEST_ROWS = 16
    /** 頭の形の、首の軸からの前へのずれ(頭の関節の空間。E14-01) */
    private const val HEAD_FORWARD = 0.025

    private class Entry(val part: PartMesh, val position: Vec3, val rotation: Quat)

    private val table: Map<String, List<Entry>> by lazy { PartSpecs.all.associate { it.id to build(it.id) } }

    fun meshes(id: String): List<PartMesh> = table.getValue(id).map { it.part }

    /**
     * 体型を反映したメッシュの置き方(Web版の setShoulderWidth・setPelvisWidth・updateHips)。
     * 肩は長さ、骨盤は横幅、付け根は長さが変わる。首・上腕・前腕・もも・すねは長さの倍率で縦に伸びる。
     * 頭の大きさと身長は関節の拡大率で効くのでここでは扱わない。
     */
    fun placement(id: String, index: Int, body: BodyParams): MeshPlacement {
        val e = table.getValue(id)[index]
        // 乳房は大きさで拡大する(前後は少し平たく)。E08-04
        if (e.part.feature == BodyFeature.BREAST) {
            val s = body.breastSize
            return MeshPlacement(e.position, e.rotation, Vec3(s, s, 0.95 * s))
        }
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
        // 長さを変えた部位(首・上腕・前腕・もも・すね)の形は縦に伸ばす。関節の玉(MARKER)はそのまま(E07-02)
        val k = lengthScale(spec, body)
        if (k != 1.0 && e.part.kind == MeshKind.BODY) {
            return MeshPlacement(Vec3(e.position.x, e.position.y * k, e.position.z), e.rotation, Vec3(1.0, k, 1.0))
        }
        return MeshPlacement(e.position, e.rotation, Vec3(1.0, 1.0, 1.0))
    }

    private fun build(id: String): List<Entry> {
        val spec = PartSpecs[id]
        val sg = if (spec.side == Side.RIGHT) -1.0 else 1.0
        val base = id.removeSuffix("L").removeSuffix("R").takeIf { spec.side != null } ?: id
        val out = ArrayList<Entry>()
        fun mk(
            mesh: Mesh, pos: Vec3, rot: Quat = Quat.IDENTITY, edges: Boolean, kind: MeshKind = MeshKind.BODY,
            lines: List<Mesh> = emptyList(), hand: HandShape? = null, feature: BodyFeature? = null,
        ) {
            out += Entry(PartMesh(kind, mesh, if (edges) edges(mesh, 30.0) else null, lines, hand, feature), pos, rot)
        }
        fun marker(r: Double) = mk(sph(r), Vec3.ZERO, edges = false, kind = MeshKind.MARKER)
        when (base) {
            // 胸と脇腹は、もとの肋骨の錐台(上 0.115・下 0.095・高さ 0.30)を上から 6.5:3.5 で分けたもの(E05-02)
            // 胸は肋骨の形(E08-01)。輪郭を回し、前後を CHEST_DEPTH 倍に縮める。関節の空間でそのまま作る
            "rib" -> {
                mk(chest(), Vec3.ZERO, edges = true, lines = chestLines())
                // 乳房(E08-04)。胸の前面に左右対称に置き、大きさは置き方の拡大で変える。乳頭は線の色の小さな球
                for (side in listOf(1.0, -1.0)) {
                    mk(Primitives.sphere(BREAST_R, 20, 14), breastCenter(side), edges = false,
                        lines = listOf(Primitives.sphere(0.006, 8, 6).translated(Vec3(0.0, 0.0, BREAST_R))), feature = BodyFeature.BREAST)
                }
            }
            "flank" -> {
                mk(frustum(SPLIT_R, 0.095, 0.105, 0.64), Vec3(0.0, -0.0525, 0.035), edges = true, lines = frustumLines(SPLIT_R, 0.095, 0.105, 0.64, listOf(0.0275)))
                marker(0.04)
            }
            "neck" -> {
                mk(cyl(0.032, 0.07), Vec3(0.0, 0.035, 0.0), edges = true, lines = cylLines(0.032, 0.07, ring = false))
                marker(0.038)
            }
            "head" -> {
                // 頭の形は首の軸より前へ HEAD_FORWARD ずらす(頭蓋は首の真上より前へ張り出す。E14-01)
                mk(Primitives.sphere(0.098, 24, 18), Vec3(0.0, 0.115, HEAD_FORWARD), edges = false, lines = headLines(0.1005))
                val mask = Primitives.sphere(0.125, 32, 24, PI / 2 - 0.95, 1.9, PI / 2 - 0.7, 1.9).scaled(1.0, 1.25, 1.0)
                mk(mask, Vec3(0.0, 0.115, HEAD_FORWARD), edges = true, kind = MeshKind.MASK, lines = maskLines())
            }
            "pelvis" -> {
                mk(frustum(0.10, 0.12, 0.14, 0.68), Vec3(0.0, -0.07, 0.035), edges = true, lines = frustumLines(0.10, 0.12, 0.14, 0.68, listOf(0.0)))
                marker(0.04)
                // 性器(E08-04)。骨盤の前の下、中央。男は小さな膨らみ、女は下向きの三角の面
                mk(Primitives.sphere(0.028, 16, 12).scaled(0.9, 1.2, 0.75), Vec3(0.0, -0.135, 0.115), edges = false, feature = BodyFeature.MALE)
                mk(Primitives.cylinder(0.03, 0.03, 0.004, 3), Vec3(0.0, -0.105, 0.1152), Quat.fromAxisAngle(Vec3.X, PI / 2), edges = true, feature = BodyFeature.FEMALE)
            }
            "clav" -> mk(cyl(0.022, 0.12), Vec3(sg * 0.06, 0.0, 0.0), QZ90, edges = true, lines = cylLines(0.022, 0.12, ring = false))
            "upperArm" -> { mk(cyl(0.032, 0.24), Vec3(0.0, -0.125, 0.0), edges = true, lines = cylLines(0.032, 0.24, ring = true)); marker(0.045) }
            "forearm" -> { mk(cyl(0.028, 0.22), Vec3(0.0, -0.11, 0.0), edges = true, lines = cylLines(0.028, 0.22, ring = true)); marker(0.036) }
            "hand" -> {
                // 平たい面が体側を向くように縦軸まわりに 90° 回す(E06-01)。パーの手のひら
                mk(Primitives.box(0.062, 0.09, 0.024), Vec3(0.0, -0.05, 0.0), Quat.fromAxisAngle(Vec3.Y, PI / 2), edges = true,
                    lines = listOf(tube(listOf(Vec3(0.0, -0.045, 0.012 + LE), Vec3(0.0, 0.045, 0.012 + LE)), closed = false)), hand = HandShape.OPEN)
                marker(0.026)
                // パーの指と親指(E08-02)。グーはブロックと親指。親指は前(+z)、手のひらは体側(−sg·x)
                mk(HandMeshes.openFingers(sg), Vec3.ZERO, edges = true, hand = HandShape.OPEN)
                mk(HandMeshes.fist(sg), Vec3.ZERO, edges = true, hand = HandShape.FIST)
            }
            "hip" -> mk(Primitives.cylinder(0.045, 0.045, 0.05, 20), Vec3(sg * 0.025, 0.0, 0.0), QZ90, edges = true)
            "thigh" -> { mk(cyl(0.052, 0.39), Vec3(0.0, -0.2, 0.0), edges = true, lines = cylLines(0.052, 0.39, ring = true)); marker(0.055) }
            "shin" -> { mk(cyl(0.038, 0.37), Vec3(0.0, -0.19, 0.0), edges = true, lines = cylLines(0.038, 0.37, ring = true)); marker(0.044) }
            "foot" -> {
                // 足はかかとからつま先の付け根まで(z −0.05〜0.105)。かかとは横向きの円柱で丸める(E08-03)
                mk(Primitives.box(0.075, 0.045, 0.1325), Vec3(0.0, -0.04, 0.03875), edges = true,
                    lines = listOf(tube(listOf(Vec3(0.0, 0.0225 + LE, -0.06625), Vec3(0.0, 0.0225 + LE, 0.06625)), closed = false)))
                marker(0.036)
                mk(Primitives.cylinder(0.0225, 0.0225, 0.075, 20), Vec3(0.0, -0.04, -0.0275), QZ90, edges = true)
            }
            "toe" -> {
                // つま先(z 0.105〜0.15)。裏は足の裏と同じ高さで、上は少し低い(E08-03)
                // 関節は足の空間で y = −0.045。箱の中心を関節の高さに置くと、裏(−0.0175)が足の裏(−0.0625)と揃う
                mk(Primitives.box(0.072, 0.035, 0.045), Vec3(0.0, 0.0, 0.0225), edges = true)
                marker(0.022)
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

    /**
     * 胸の上端の切り口の高さ(E14-02。胸の関節の空間)。後ろの中央(上端の背中側の縁)が最も高く CHEST_TOP で、
     * 前へ CHEST_CUT_FRONT、中央から肩へ CHEST_CUT_SIDE の角度で下がる
     */
    internal fun chestTopY(x: Double, z: Double): Double =
        CHEST_TOP - tan(CHEST_CUT_FRONT) * (z - CHEST_BACK_Z) - tan(CHEST_CUT_SIDE) * abs(x)

    /** 胸の側面の、角度 theta の列での点(theta = 0 が前、x = r sin θ・z = 深さ × r cos θ) */
    private fun chestPoint(y: Double, theta: Double): Vec3 {
        val r = chestRadius(y)
        return Vec3(r * sin(theta), y, CHEST_DEPTH * r * cos(theta))
    }

    /** 角度 theta の列が切り口に当たる高さ。下端から上端の間で二分法で求める */
    private fun chestColumnTop(theta: Double): Double {
        fun above(y: Double) = chestPoint(y, theta).let { it.y > chestTopY(it.x, it.z) }
        if (!above(CHEST_TOP)) return CHEST_TOP
        var lo = CHEST_BOTTOM
        var hi = CHEST_TOP
        repeat(40) { val mid = (lo + hi) / 2; if (above(mid)) hi = mid else lo = mid }
        return lo
    }

    /** 角度 theta の列の輪郭の高さ(下端から切り口まで CHEST_ROWS 等分) */
    private fun chestColumn(theta: Double): List<Double> {
        val top = chestColumnTop(theta)
        return (0..CHEST_ROWS).map { i -> CHEST_BOTTOM + (top - CHEST_BOTTOM) * i / CHEST_ROWS }
    }

    /**
     * 胸の形(E08-01・E14-02)。肋骨の輪郭を回した面(前後を CHEST_DEPTH 倍)を、列ごとに切り口の高さで止め、
     * 切り口には斜めのふたをする。下端は水平のふた
     */
    private fun chest(): Mesh {
        val segments = 28
        val b = MeshBuilder()
        val thetas = (0..segments).map { it.toDouble() / segments * 2 * PI }
        val columns = thetas.map { chestColumn(it) }
        val rows = Array(segments + 1) { IntArray(CHEST_ROWS + 1) }
        for ((x, theta) in thetas.withIndex()) {
            val s = sin(theta)
            val c = cos(theta)
            for ((i, y) in columns[x].withIndex()) {
                // 輪郭の接線に垂直な向き(lathe と同じ)を、前後の縮みに合わせて傾ける
                val y0 = columns[x][maxOf(i - 1, 0)]
                val y1 = columns[x][minOf(i + 1, CHEST_ROWS)]
                val nr0 = y1 - y0
                val ny0 = -(chestRadius(y1) - chestRadius(y0))
                rows[x][i] = b.vertexCount
                b.vertex(chestPoint(y, theta), Vec3(nr0 * s, ny0, nr0 * c / CHEST_DEPTH).normalized())
            }
        }
        for (x in 0 until segments) {
            for (i in 0 until CHEST_ROWS) {
                b.triangle(rows[x][i + 1], rows[x][i], rows[x + 1][i + 1])
                b.triangle(rows[x][i], rows[x + 1][i], rows[x + 1][i + 1])
            }
        }
        // 上のふた: 切り口の縁を、縁の重心から扇で張る。法線は切り口の面の向き
        fun cutNormal(px: Double) = Vec3(tan(CHEST_CUT_SIDE) * sign(px), 1.0, tan(CHEST_CUT_FRONT)).normalized()
        val rim = thetas.mapIndexed { x, theta -> chestPoint(columns[x].last(), theta) }
        val cx = rim.dropLast(1).sumOf { it.x } / segments
        val cz = rim.dropLast(1).sumOf { it.z } / segments
        val center = b.vertexCount
        b.vertex(Vec3(cx, chestTopY(cx, cz), cz), cutNormal(0.0))
        rim.forEach { b.vertex(it, cutNormal(it.x)) }
        for (x in 0 until segments) b.triangle(center + 1 + x, center + 2 + x, center)
        // 下のふた(水平)
        val bottomCenter = b.vertexCount
        b.vertex(Vec3(0.0, CHEST_BOTTOM, 0.0), Vec3(0.0, -1.0, 0.0))
        thetas.forEach { b.vertex(chestPoint(CHEST_BOTTOM, it), Vec3(0.0, -1.0, 0.0)) }
        for (x in 0 until segments) b.triangle(bottomCenter + 2 + x, bottomCenter + 1 + x, bottomCenter)
        return b.build()
    }

    /** 前後の縦線(輪郭に沿い、切り口で止まる)と、y = 0.06 の輪(もとの肋骨の輪の高さ) */
    private fun chestLines(): List<Mesh> = buildList {
        add(tube(chestColumn(0.0).map { y -> Vec3(0.0, y, CHEST_DEPTH * chestRadius(y) + LE) }, closed = false))
        add(tube(chestColumn(PI).map { y -> Vec3(0.0, y, -CHEST_DEPTH * chestRadius(y) - LE) }, closed = false))
        val ringY = 0.06
        val r = chestRadius(ringY) + LE
        add(tube((0 until 48).map { val t = it / 48.0 * 2 * PI; Vec3(r * cos(t), ringY, CHEST_DEPTH * r * sin(t)) }, closed = true))
    }

    /** 乳房の中心(胸の関節の空間)。胸の前面の、y = 0.055・横 0.06 の点 */
    private fun breastCenter(side: Double): Vec3 {
        val y = 0.055
        val x = 0.06
        val r = chestRadius(y)
        return Vec3(side * x, y, CHEST_DEPTH * kotlin.math.sqrt(r * r - x * x))
    }

    /**
     * このメッシュを今の骨格で描くか。手は今の形のものだけ(E08-02)、乳房は大きさが 0 でないとき、
     * 性器は今の性別の形だけ(E08-04)
     */
    fun isShown(id: String, index: Int, skeleton: Skeleton): Boolean {
        val pm = table.getValue(id)[index].part
        if (pm.handShape != null && pm.handShape != skeleton.handShape(id)) return false
        return when (pm.feature) {
            null -> true
            BodyFeature.BREAST -> skeleton.body.breastSize > 0.0
            BodyFeature.MALE -> skeleton.body.sex == Sex.MALE
            BodyFeature.FEMALE -> skeleton.body.sex == Sex.FEMALE
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
            if (!PartVisuals.isShown(id, i, skeleton)) return@forEachIndexed
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
