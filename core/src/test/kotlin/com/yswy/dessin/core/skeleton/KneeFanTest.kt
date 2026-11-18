package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertVec
import com.yswy.dessin.core.mesh.guideMesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

private const val D2R = PI / 180

/**
 * E17-04: すねの可動域は、曲げ角を半径方向・ひねりを角度方向にした扇形。
 * 曲げる面の扇をももの軸(基準フレームの y)まわりにひねりの分だけ回した点の集まりで、玉もその上に出す
 */
class KneeFanTest {
    private val shinL = PartSpecs["shinL"]
    private val r = shinL.guideRadius

    @Test
    fun ひねり0なら玉は曲げる面の上() {
        assertVec(Vec3(0.0, 0.0, -r), dotPosition(shinL, PartState(PI / 2, 0.0, 0.0)))
    }

    @Test
    fun 外旋すると玉は外へ寄る() {
        val p = dotPosition(shinL, PartState(PI / 2, 0.0, -40 * D2R))
        assertEquals(r * sin(40 * D2R), p.x, EPS)
        assertEquals(r, p.length(), EPS)
        // 右は鏡像(外旋が +、外は −x)
        assertEquals(-r * sin(40 * D2R), dotPosition(PartSpecs["shinR"], PartState(PI / 2, 0.0, 40 * D2R)).x, EPS)
    }

    @Test
    fun ほかのヒンジの玉はひねりで動かない() {
        val fore = PartSpecs["forearmL"]
        assertVec(dotPosition(fore, PartState(1.0, fore.hinge!!.psi, 0.0)), dotPosition(fore, PartState(1.0, fore.hinge!!.psi, 0.5)))
    }

    @Test
    fun ひねっていても玉の向きへ向けると曲げ角は変わらない() {
        val s = PartState(70 * D2R, 0.0, -25 * D2R)
        val aimed = aimPart(shinL, s, dotPosition(shinL, s))
        assertEquals(s.phi, aimed.phi, EPS)
        assertEquals(s.roll, aimed.roll, EPS)
    }

    @Test
    fun 骨格の玉のドラッグでも曲げ角が飛ばない() {
        val legs = Skeleton.initial().place("flank").place("pelvis").place("hipL").place("thighL").place("shinL")
            .withState("shinL", PartState(100 * D2R, 0.0, 30 * D2R))
        val again = legs.aim("shinL", legs.dotWorld("shinL") - legs.guideCenter("shinL"))
        assertEquals(100 * D2R, again.state("shinL").phi, 1e-9)
        assertEquals(30 * D2R, again.state("shinL").roll, 1e-9)
    }

    @Test
    fun 扇は曲げるほど広がり_外へ40度_内へ30度まで() {
        val g = guideMesh(shinL)
        val vs = (0 until g.area.vertexCount).map { g.area.vertex(it) }
        // 外(+x)へは曲げ 90° で r·sin40°、内(−x)へは r·sin30° が最も遠い
        assertEquals(r * sin(40 * D2R), vs.maxOf { it.x }, 1e-4)
        assertEquals(-r * sin(30 * D2R), vs.minOf { it.x }, 1e-4)
        // 伸ばしたあたり(真下の近く)は幅がほとんど無い
        vs.filter { it.y < -r * 0.99 }.forEach { assertTrue("$it", kotlin.math.abs(it.x) < r * 0.01) }
        // どの点も可動域の半径の上か関節
        vs.forEach { assertTrue(it.length() < r + 1e-6) }
        // 縁の線は閉じている
        val o = g.outline
        assertEquals(o[0], o[o.size - 3]); assertEquals(o[1], o[o.size - 2]); assertEquals(o[2], o[o.size - 1])
    }
}
