package com.yswy.dessin.core.pick

import com.yswy.dessin.core.golden.GoldenState
import com.yswy.dessin.core.golden.goldenText
import com.yswy.dessin.core.math.Ray
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.render.DisplayOptions
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

/**
 * Web版の pickPart・romPoint・pointerdown の規則と同じ結果になることを確かめる(E02-05)。
 * データは tools/golden の generate.mjs が作る。
 */
class PickGoldenTest {
    private val tol = 1e-6

    @Test
    fun Web版と同じ部位と緑の範囲の点を選ぶ() {
        val errors = mutableListOf<String>()
        var state = GoldenState()
        var where = ""
        var picks = 0
        var dotHits = 0
        var romHits = 0
        var parentSkipped = 0

        goldenText("pick-golden.txt").lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val t = line.split(' ')
            when (t[0]) {
                "scenario" -> {
                    state = GoldenState()
                    where = line
                }
                "op" -> state.apply(t)
                "pick" -> {
                    picks++
                    if (t[1] != state.selected && errors.size < 20) errors += "$where: 選択中 期待 ${t[1]} 実際 ${state.selected}"
                    val ray = Ray(Vec3(t[2].toDouble(), t[3].toDouble(), t[4].toDouble()), Vec3(t[5].toDouble(), t[6].toDouble(), t[7].toDouble()))
                    val expectedPart = t[8].takeIf { it != "-" }
                    val expectedRom = if (t[9] == "-") null else Vec3(t[9].toDouble(), t[10].toDouble(), t[11].toDouble())
                    val expectedPress = t[12].takeIf { it != "-" }
                    val options = DisplayOptions()
                    val part = pickPart(state.skeleton, state.selected, options, ray)
                    val rom = romPoint(state.skeleton, state.selected, options, ray)
                    val press = pickAtPress(state.skeleton, state.selected, options, ray)
                    if (expectedPart == state.selected && dotHit(state, ray)) dotHits++
                    if (expectedRom != null) romHits++
                    if (expectedPart != null && expectedPress == null) parentSkipped++
                    val label = "$where / ${t.subList(1, 8).joinToString(" ")}"
                    if (part != expectedPart && errors.size < 20) errors += "$label: 部位 期待 $expectedPart 実際 $part"
                    if ((rom == null) != (expectedRom == null)) {
                        if (errors.size < 20) errors += "$label: 緑の範囲 期待 $expectedRom 実際 $rom"
                    } else if (rom != null && expectedRom != null && rom.distanceTo(expectedRom) > tol) {
                        if (errors.size < 20) errors += "$label: 緑の範囲の点 期待 $expectedRom 実際 $rom"
                    }
                    if ((press.part != expectedPress || (press.romPoint == null) != (expectedRom == null)) && errors.size < 20) {
                        errors += "$label: 押した先 期待 $expectedPress 実際 ${press.part}"
                    }
                }
                else -> error("知らない行: $line")
            }
        }

        if (errors.isNotEmpty()) fail("Web版と一致しない(最初の${errors.size}件):\n" + errors.joinToString("\n"))
        // 玉・緑の範囲・親を選ばない規則のそれぞれを、データが実際に含んでいること
        assertTrue("ピッキング $picks 玉 $dotHits 緑の範囲 $romHits 親を選ばない $parentSkipped", picks >= 700 && dotHits >= 50 && romHits >= 100 && parentSkipped >= 5)
    }

    private fun dotHit(state: GoldenState, ray: Ray): Boolean =
        pickDot(state.skeleton, state.selected, DisplayOptions(), ray) != null
}
