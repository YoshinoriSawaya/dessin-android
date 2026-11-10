package com.yswy.dessin.core.golden

import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Ray
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.skeleton.BodyParams
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.PartState
import com.yswy.dessin.core.skeleton.RootTransform
import com.yswy.dessin.core.skeleton.Skeleton

/**
 * 照合データ(tools/golden の generate.mjs が書く)の `op` 行を Skeleton に加える。
 * 選択中の部位も Web版と同じ規則で追う: 置く(select)・回転オーブで選び、外したら親を選ぶ。
 */
class GoldenState(var skeleton: Skeleton = Skeleton.initial(), var selected: String = "rib") {
    /** t は行を空白で区切ったもの(t[0] == "op") */
    fun apply(t: List<String>) {
        fun d(i: Int) = t[i].toDouble()
        fun v(i: Int) = Vec3(d(i), d(i + 1), d(i + 2))
        val id = t.getOrNull(2) ?: ""
        when (t[1]) {
            "place" -> select(id)
            "remove" -> {
                skeleton = skeleton.remove(id)
                PartSpecs[id].parent?.let { select(it) }
            }
            "reset" -> skeleton = skeleton.reset(id)
            "setState" -> skeleton = skeleton.withState(id, PartState(d(3), d(4), d(5)))
            "aim" -> skeleton = skeleton.aim(id, v(3))
            "aimAt" -> skeleton = skeleton.aimAtPoint(id, v(3))
            "drag" -> skeleton = skeleton.drag(id, Ray(v(3), v(6)))
            "orb" -> {
                select(id)
                skeleton = skeleton.orb(id, Quat(d(3), d(4), d(5), d(6)))
            }
            "root" -> skeleton = skeleton.withRoot(RootTransform(v(2), Quat.fromEulerYXZ(v(5)), d(8)))
            "body" -> skeleton = skeleton.withBody(BodyParams(d(2), d(3), d(4), d(5)))
            else -> error("知らない操作: ${t.joinToString(" ")}")
        }
    }

    /** Web版の select: 置ける部位なら置いて選ぶ。置けなければ何もしない */
    private fun select(id: String) {
        if (!skeleton.isAvailable(id)) return
        skeleton = skeleton.place(id)
        selected = id
    }
}

fun goldenText(name: String): String =
    GoldenState::class.java.getResource("/golden/$name")?.readText()
        ?: error("golden/$name が無い。tools/golden で npm run generate を実行する")
