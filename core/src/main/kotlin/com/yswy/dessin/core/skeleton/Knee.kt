package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** すねの見た目に足す膝の部品(E17-03) */
enum class KneePiece { PATELLA, TENDON }

/** 膝蓋骨の中心の、大腿骨の丸みの中心からの距離 */
const val PATELLA_RADIUS = 0.058
/** 膝蓋骨の、中心から下端までの長さ(丸みに沿う向き) */
const val PATELLA_HALF_HEIGHT = 0.026
/** 伸ばしたときの膝蓋骨の角度(丸みの中心から前を 0、下へ回る向きを +)。少し上にある */
private const val PATELLA_REST_ANGLE = -15 * PI / 180
/** 脛骨粗面(膝蓋腱の付く所)。すねの関節の空間で、前面の上。y はすねの長さの倍率に従う */
private val TUBEROSITY = Vec3(0.0, -0.07, 0.040)

/**
 * 膝蓋骨と膝蓋腱の置き方(E17-03)。位置はすべてももの関節の空間。
 * @property angle 膝蓋骨の、丸みの中心のまわりの角度(前が 0、下へ回る向きが +)
 * @property patellaRotation 膝蓋骨の向き。メッシュの y が丸みに沿って上、z が外を向く
 * @property lowerPole 膝蓋骨の下端(腱の上端)
 * @property tuberosity 脛骨粗面(腱の下端)
 * @property tendonLength 腱の長さ。伸ばしたときの膝蓋骨の下端と脛骨粗面の距離で、曲げても変わらない
 */
data class KneeCap(
    val angle: Double,
    val patellaCenter: Vec3,
    val patellaRotation: Quat,
    val lowerPole: Vec3,
    val tuberosity: Vec3,
    val tendonLength: Double,
)

/**
 * 膝蓋骨の位置を曲げ角から求める(E17-03)。膝蓋骨は大腿骨の丸みの中心(すねの回転の中心)のまわりを PATELLA_RADIUS で回り、
 * 腱の長さが伸ばしたときと同じになる角度に置く。曲げるほど脛骨粗面が離れるので、膝蓋骨は丸みに沿って下へ回り込む
 */
fun kneeCap(skeleton: Skeleton, shinId: String): KneeCap {
    val spec = PartSpecs[shinId]
    val body = skeleton.body
    val w = skeleton.worldTransforms()
    val thigh = w.getValue(requireNotNull(spec.parent))
    val shin = w.getValue(shinId)
    val inv = thigh.rotation.inverse()
    // すねの関節の、ももの空間での位置と向き(拡大率は同じ)
    val joint = inv.rotate(shin.position - thigh.position) * (1 / thigh.scale)
    val local = inv * shin.rotation
    val attach = attachOf(spec, body)
    val center = attach + pivotOf(spec, body)
    val t0 = Vec3(TUBEROSITY.x, TUBEROSITY.y * lengthScale(spec, body), TUBEROSITY.z)

    fun patella(a: Double) = center + Vec3(0.0, -sin(a), cos(a)) * PATELLA_RADIUS
    fun pole(a: Double) = patella(a) + Vec3(0.0, -cos(a), -sin(a)) * PATELLA_HALF_HEIGHT

    // 伸ばしたとき(関節は取り付け位置、回転なし)の腱の長さ
    val length = pole(PATELLA_REST_ANGLE).distanceTo(attach + t0)
    val tuberosity = joint + local.rotate(t0)
    fun slack(a: Double) = pole(a).distanceTo(tuberosity) - length

    // 上から下へ回して、腱が張る(長さが足りる)最初の角度を探し、二分法で詰める
    val lo = PATELLA_REST_ANGLE - 30 * PI / 180
    val hi = PATELLA_REST_ANGLE + PI
    val steps = 180
    var angle = lo
    if (slack(lo) > 0) {
        var prev = lo
        var found = false
        for (i in 1..steps) {
            val a = lo + (hi - lo) * i / steps
            if (slack(a) <= 0) {
                var l = prev
                var h = a
                repeat(60) { val m = (l + h) / 2; if (slack(m) > 0) l = m else h = m }
                angle = h
                found = true
                break
            }
            prev = a
        }
        // 届かなければ最も近づく角度
        if (!found) angle = (0..steps).map { lo + (hi - lo) * it / steps }.minBy { slack(it) }
    }
    return KneeCap(
        angle = angle,
        patellaCenter = patella(angle),
        patellaRotation = Quat.fromAxisAngle(Vec3.X, angle),
        lowerPole = pole(angle),
        tuberosity = tuberosity,
        tendonLength = length,
    )
}
