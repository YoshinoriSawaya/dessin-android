package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.golden.GoldenState
import com.yswy.dessin.core.math.Quat
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test
import kotlin.math.abs

/**
 * Web版と同じ操作を Skeleton に加え、各操作のあとの全関節がWeb版と一致することを確かめる(E01-10)。
 * データは tools/golden の generate.mjs が docs/web-reference.html を動かして作る。
 */
@Ignore("Web版との照合は v1 で完了(2026-11-11)。E04 以降は仕様が Web版と異なる。docs/development.md を参照")
class WebGoldenTest {
    private val tol = 1e-6

    @Test
    fun Web版と同じ操作で同じ姿勢になる() {
        val text = javaClass.getResource("/golden/web-golden.txt")?.readText()
            ?: error("golden/web-golden.txt が無い。tools/golden で npm run generate を実行する")
        val errors = mutableListOf<String>()
        var state = GoldenState()
        var where = ""
        var scenarios = 0
        var ops = 0
        var parts = 0

        fun check(label: String, expected: Double, actual: Double) {
            if (abs(expected - actual) > tol && errors.size < 20) errors += "$where: $label 期待 $expected 実際 $actual"
        }

        text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val t = line.split(' ')
            fun d(i: Int) = t[i].toDouble()
            val skeleton = state.skeleton
            when (t[0]) {
                "scenario" -> {
                    state = GoldenState()
                    where = t.drop(1).joinToString(" ")
                    scenarios++
                }
                "op" -> {
                    ops++
                    where = "${where.substringBefore(" / ")} / $line"
                    state.apply(t)
                }
                "placed" -> {
                    val expected = t.getOrElse(1) { "" }.split(',').filter { it.isNotEmpty() }.toSet()
                    if (expected != skeleton.placed && errors.size < 20) errors += "$where: placed 期待 $expected 実際 ${skeleton.placed}"
                }
                "part" -> {
                    parts++
                    val id = t[1]
                    val jt = skeleton.worldTransforms().getValue(id)
                    check("$id.position.x", d(2), jt.position.x)
                    check("$id.position.y", d(3), jt.position.y)
                    check("$id.position.z", d(4), jt.position.z)
                    // q と −q は同じ回転
                    val q = Quat(d(5), d(6), d(7), d(8))
                    val sign = if (q.x * jt.rotation.x + q.y * jt.rotation.y + q.z * jt.rotation.z + q.w * jt.rotation.w < 0) -1.0 else 1.0
                    check("$id.rotation.x", q.x, sign * jt.rotation.x)
                    check("$id.rotation.y", q.y, sign * jt.rotation.y)
                    check("$id.rotation.z", q.z, sign * jt.rotation.z)
                    check("$id.rotation.w", q.w, sign * jt.rotation.w)
                    check("$id.scale", d(9), jt.scale)
                    if (t.size > 10) {
                        val s = skeleton.state(id)
                        check("$id.phi", d(10), s.phi)
                        check("$id.psi", d(11), s.psi)
                        check("$id.roll", d(12), s.roll)
                        val dot = skeleton.dotWorld(id)
                        check("$id.dot.x", d(13), dot.x)
                        check("$id.dot.y", d(14), dot.y)
                        check("$id.dot.z", d(15), dot.z)
                    }
                }
                else -> error("知らない行: $line")
            }
        }

        if (errors.isNotEmpty()) fail("Web版と一致しない(最初の${errors.size}件):\n" + errors.joinToString("\n"))
        // データが空で素通りしていないこと
        assertTrue("場面 $scenarios 操作 $ops 関節 $parts", scenarios >= 10 && ops >= 200 && parts >= 20 * ops)
    }
}
