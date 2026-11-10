package com.yswy.dessin.core.pose

import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.skeleton.BodyParams
import com.yswy.dessin.core.skeleton.PartSpec
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.PartState
import com.yswy.dessin.core.skeleton.RootTransform
import com.yswy.dessin.core.skeleton.Skeleton
import com.yswy.dessin.core.skeleton.phiMax
import kotlin.math.abs
import kotlin.math.sqrt

sealed interface PoseDecodeResult {
    data class Ok(val skeleton: Skeleton) : PoseDecodeResult

    /** reason は内部ログ向けの理由。利用者には「読み込めなかった」とだけ伝える */
    data class Error(val reason: String) : PoseDecodeResult
}

/**
 * ポーズ(Skeleton)と文字列の変換。依存を増やさないため、JSON ではなく行形式のテキストにする。
 *
 * ```
 * dessin-pose 1
 * root <px> <py> <pz> <qx> <qy> <qz> <qw> <scale>
 * body <headSize> <shoulderWidth> <pelvisWidth> <hipInset>
 * part <id> <置いたか 0|1> <phi> <psi> <roll>
 * ```
 *
 * 読み込みでは、形式の誤りはエラーにし、値は範囲に収める(可動域、体型と胸の大きさの範囲、回転の正規化)。
 * 書かれていない値は初期値、知らない部位の行は無視、親が置かれていない部位は外した扱いにする。
 */
object PoseCodec {
    const val HEADER = "dessin-pose"
    /** 版2: 脇腹(flank)を足し、骨盤の親を脇腹にした(E05-01)。版1も読める */
    const val VERSION = 2

    fun encode(skeleton: Skeleton): String = buildString {
        appendLine("$HEADER $VERSION")
        val r = skeleton.root
        appendLine(listOf("root", r.position.x, r.position.y, r.position.z, r.rotation.x, r.rotation.y, r.rotation.z, r.rotation.w, r.scale).joinToString(" "))
        val b = skeleton.body
        appendLine(listOf("body", b.headSize, b.shoulderWidth, b.pelvisWidth, b.hipInset).joinToString(" "))
        PartSpecs.all.filterNot { it.isRoot }.forEach { spec ->
            val s = skeleton.state(spec.id)
            val placed = if (skeleton.isPlaced(spec.id)) 1 else 0
            appendLine(listOf("part", spec.id, placed, s.phi, s.psi, s.roll).joinToString(" "))
        }
    }

    fun decode(text: String): PoseDecodeResult = try {
        PoseDecodeResult.Ok(parse(text))
    } catch (e: PoseFormatException) {
        PoseDecodeResult.Error(e.message ?: "形式の誤り")
    }

    private class PoseFormatException(message: String) : Exception(message)

    private fun parse(text: String): Skeleton {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val head = lines.firstOrNull()?.split(WS) ?: throw PoseFormatException("空のデータ")
        if (head.size != 2 || head[0] != HEADER) throw PoseFormatException("見出しが違う: ${lines.first()}")
        val version = head[1].toIntOrNull()
        if (version != 1 && version != VERSION) throw PoseFormatException("知らない版: ${head[1]}")

        var root = RootTransform()
        var body = BodyParams()
        val flagged = mutableSetOf<String>()
        val states = mutableMapOf<String, PartState>()
        for (line in lines.drop(1)) {
            val t = line.split(WS)
            when (t[0]) {
                "root" -> {
                    val n = numbers(line, t, 8)
                    root = RootTransform(Vec3(n[0], n[1], n[2]), normalized(Quat(n[3], n[4], n[5], n[6])), n[7].coerceIn(RootTransform.SCALE_RANGE))
                }
                "body" -> {
                    val n = numbers(line, t, 4)
                    body = BodyParams(
                        n[0].coerceIn(BodyParams.HEAD_SIZE_RANGE),
                        n[1].coerceIn(BodyParams.SHOULDER_WIDTH_RANGE),
                        n[2].coerceIn(BodyParams.PELVIS_WIDTH_RANGE),
                        n[3].coerceIn(BodyParams.HIP_INSET_RANGE),
                    )
                }
                "part" -> {
                    if (t.size != 6) throw PoseFormatException("項目の数が違う: $line")
                    // t = part, id, 置いたか, phi, psi, roll
                    val flag = when (t[2]) {
                        "0" -> false
                        "1" -> true
                        else -> throw PoseFormatException("置いたかは 0 か 1: $line")
                    }
                    val n = numbers(line, t.drop(2), 3)
                    val spec = PartSpecs.all.firstOrNull { it.id == t[1] } ?: continue
                    if (spec.isRoot) continue
                    if (flag) flagged += spec.id
                    states[spec.id] = clamp(spec, PartState(n[0], n[1], n[2]))
                }
                else -> throw PoseFormatException("知らない行: $line")
            }
        }

        // 版1には脇腹が無い(骨盤は肋骨の子だった)。骨盤が置かれていれば脇腹を休止状態で置く
        if (version == 1 && "pelvis" in flagged) flagged += "flank"
        // 親から順に、親が置かれている部位だけを置く。置かない部位は休止状態にする
        var skeleton = Skeleton.initial().withRoot(root).withBody(body)
        PartSpecs.all.filterNot { it.isRoot }.forEach { spec ->
            if (spec.id in flagged && skeleton.isAvailable(spec.id)) {
                skeleton = skeleton.place(spec.id).withState(spec.id, states[spec.id] ?: PartState.rest(spec))
            }
        }
        return skeleton
    }

    /** 見出しの後ろに count 個の有限な数が並んでいること */
    private fun numbers(line: String, t: List<String>, count: Int): List<Double> {
        if (t.size != count + 1) throw PoseFormatException("項目の数が違う: $line")
        return t.drop(1).map { s ->
            val v = s.toDoubleOrNull() ?: throw PoseFormatException("数値ではない: $s($line)")
            if (!v.isFinite()) throw PoseFormatException("有限でない数値: $s($line)")
            v
        }
    }

    /** 可動域に収める。ヒンジは曲げる向きを固定する */
    private fun clamp(spec: PartSpec, s: PartState): PartState {
        val roll = s.roll.coerceIn(spec.roll.min, spec.roll.max)
        val hinge = spec.hinge
        if (hinge != null) return PartState(s.phi.coerceIn(hinge.min, hinge.max), hinge.psi, roll)
        return PartState(s.phi.coerceIn(0.0, phiMax(spec, s.psi)), s.psi, roll)
    }

    /** 長さ1から外れていれば正規化する。長さ0なら回さない */
    private fun normalized(q: Quat): Quat {
        val lenSq = q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w
        return when {
            lenSq == 0.0 -> Quat.IDENTITY
            abs(lenSq - 1) > 1e-12 -> q.normalized()
            else -> q
        }
    }

    private val WS = Regex("\\s+")
}
