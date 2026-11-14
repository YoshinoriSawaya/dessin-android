package com.yswy.dessin.core.pose

import com.yswy.dessin.core.editor.MAX_DOLLS
import com.yswy.dessin.core.skeleton.Skeleton

/**
 * 場面(複数の人形。E13-04)と文字列の変換。人形ごとに体型(BodyCodec の1行)とポーズ(PoseCodec の文字列)を並べる。
 *
 * ```
 * dessin-scene 1
 * doll
 * bodytype <BodyCodec の1行>
 * <PoseCodec の文字列(dessin-pose から)>
 * doll
 * ...
 * ```
 *
 * 根(胸・骨盤)はポーズと同じく保存しない。形の誤り・人形が1体も無い場面は null。11体目からは読まない
 */
object SceneCodec {
    private const val HEADER = "dessin-scene 1"
    private const val DOLL = "doll"
    private const val BODY = "bodytype "

    fun encode(dolls: List<Skeleton>): String = buildString {
        appendLine(HEADER)
        dolls.forEach { d ->
            appendLine(DOLL)
            appendLine(BODY + BodyCodec.encodeLine(d.body))
            append(PoseCodec.encode(d))
        }
    }

    fun decode(text: String): List<Skeleton>? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.firstOrNull() != HEADER) return null
        val blocks = ArrayList<List<String>>()
        for (line in lines.drop(1)) {
            if (line == DOLL) blocks += ArrayList<String>() else (blocks.lastOrNull() as? ArrayList<String>)?.add(line) ?: return null
        }
        if (blocks.isEmpty()) return null
        return blocks.take(MAX_DOLLS).map { block ->
            val bodyLine = block.firstOrNull()?.takeIf { it.startsWith(BODY) } ?: return null
            val body = BodyCodec.decodeLine(bodyLine.removePrefix(BODY)) ?: return null
            val pose = (PoseCodec.decode(block.drop(1).joinToString("\n")) as? PoseDecodeResult.Ok)?.skeleton ?: return null
            pose.withBody(body)
        }
    }
}

/** 保存の一覧から読んだもの。場面(E13-04)か、今までの1体のポーズ */
sealed interface SavedItem {
    data class Scene(val dolls: List<Skeleton>) : SavedItem
    data class Pose(val skeleton: Skeleton) : SavedItem
}
