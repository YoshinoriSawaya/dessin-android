package com.yswy.dessin.core.pose

import com.yswy.dessin.core.skeleton.BodyParams

/**
 * 体型(BodyParams)と文字列の変換(E07-02)。1行に「名前=値」を空白で区切って並べる。
 *
 * ```
 * headSize=1.0 shoulderWidth=1.0 pelvisWidth=1.0 hipInset=0.0407 neckLength=1.0 ...
 * ```
 *
 * 項目を後から足せるように、知らない名前は読み飛ばし、書かれていない項目は既定の値にする。値は範囲に収める。
 * 数値でない値や「名前=値」の形でない項目は誤りとして null を返す。
 */
object BodyCodec {
    private val fields: List<Pair<String, (BodyParams) -> Double>> = listOf(
        "headSize" to BodyParams::headSize,
        "shoulderWidth" to BodyParams::shoulderWidth,
        "pelvisWidth" to BodyParams::pelvisWidth,
        "hipInset" to BodyParams::hipInset,
        "neckLength" to BodyParams::neckLength,
        "neckOffset" to BodyParams::neckOffset,
        "upperArmLength" to BodyParams::upperArmLength,
        "forearmLength" to BodyParams::forearmLength,
        "thighLength" to BodyParams::thighLength,
        "shinLength" to BodyParams::shinLength,
        "height" to BodyParams::height,
        "kneeRoll" to BodyParams::kneeRoll,
    )

    fun encodeLine(body: BodyParams): String = fields.joinToString(" ") { (name, get) -> "$name=${get(body)}" }

    fun decodeLine(line: String): BodyParams? {
        val values = HashMap<String, Double>()
        for (token in line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val eq = token.indexOf('=')
            if (eq <= 0) return null
            val v = token.substring(eq + 1).toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
            values[token.substring(0, eq)] = v
        }
        val d = BodyParams()
        fun v(name: String, default: Double) = values[name] ?: default
        return BodyParams(
            headSize = v("headSize", d.headSize),
            shoulderWidth = v("shoulderWidth", d.shoulderWidth),
            pelvisWidth = v("pelvisWidth", d.pelvisWidth),
            hipInset = v("hipInset", d.hipInset),
            neckLength = v("neckLength", d.neckLength),
            neckOffset = v("neckOffset", d.neckOffset),
            upperArmLength = v("upperArmLength", d.upperArmLength),
            forearmLength = v("forearmLength", d.forearmLength),
            thighLength = v("thighLength", d.thighLength),
            shinLength = v("shinLength", d.shinLength),
            height = v("height", d.height),
            kneeRoll = v("kneeRoll", d.kneeRoll),
        ).clamped()
    }
}
