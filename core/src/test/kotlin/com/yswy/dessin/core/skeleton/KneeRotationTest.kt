package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.editor.EditorState
import com.yswy.dessin.core.editor.SliderKind
import com.yswy.dessin.core.editor.applySlider
import com.yswy.dessin.core.editor.sliders
import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.pose.PoseCodec
import com.yswy.dessin.core.pose.PoseDecodeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

private const val D2R = PI / 180

/** E17-01: すねのひねり(脛骨の回旋)は曲げるほど広がる。伸ばすと 0、曲げ 90° 以上で外旋 40°・内旋 30° */
class KneeRotationTest {
    private val shinL = PartSpecs["shinL"]
    private val shinR = PartSpecs["shinR"]

    @Test
    fun 伸ばしているときはひねれない() {
        listOf(shinL, shinR).forEach {
            val r = rollRange(it, 0.0)
            assertEquals(0.0, r.min, EPS)
            assertEquals(0.0, r.max, EPS)
        }
    }

    @Test
    fun 曲げ90度で外旋40度_内旋30度_それ以上は広がらない() {
        // 左は外旋(つま先が外 +x へ向く)が負
        for (phi in listOf(90 * D2R, 120 * D2R, 150 * D2R)) {
            assertEquals(-40 * D2R, rollRange(shinL, phi).min, EPS)
            assertEquals(30 * D2R, rollRange(shinL, phi).max, EPS)
            // 右は鏡像
            assertEquals(-30 * D2R, rollRange(shinR, phi).min, EPS)
            assertEquals(40 * D2R, rollRange(shinR, phi).max, EPS)
        }
    }

    @Test
    fun 曲げるほど範囲が広がる() {
        val widths = listOf(0, 20, 45, 70, 90).map { rollRange(shinL, it * D2R).let { r -> r.max - r.min } }
        widths.zipWithNext().forEach { (a, b) -> assertTrue("$a < $b", a < b) }
        assertEquals(35 * D2R, widths[2], EPS) // 45° で半分
    }

    @Test
    fun ほかの部位の範囲は曲げ角で変わらない() {
        PartSpecs.all.filterNot { it.id.startsWith("shin") }.forEach {
            assertEquals(it.id, it.roll, rollRange(it, 1.0))
        }
    }

    @Test
    fun 左の外旋はつま先を外へ向ける() {
        val q = localRotation(shinL, PartState(PI / 2, 0.0, -40 * D2R))
        // すねの前(+z)は曲げ 90° で下を向き、外旋で外(+x)へ寄る
        assertTrue(q.rotate(Vec3.Z).x > 0.5)
        val r = localRotation(shinR, PartState(PI / 2, 0.0, 40 * D2R))
        assertTrue(r.rotate(Vec3.Z).x < -0.5)
    }

    private val legs = EditorState().copy(
        skeleton = Skeleton.initial().place("flank").place("pelvis").place("hipL").place("thighL").place("shinL"),
    ).select("shinL")

    private fun bent(phi: Double, roll: Double = 0.0) =
        legs.copy(skeleton = legs.skeleton.withState("shinL", PartState(phi, 0.0, roll)))

    @Test
    fun スライダーは曲げ角の範囲_伸ばしていると使えない() {
        val straight = legs.sliders.first { it.kind == SliderKind.ROLL }
        assertFalse(straight.enabled)
        val b = bent(90 * D2R).sliders.first { it.kind == SliderKind.ROLL }
        assertEquals(-40, b.min)
        assertEquals(30, b.max)
    }

    @Test
    fun スライダーの値は範囲に収める() {
        val s = bent(45 * D2R).applySlider(SliderKind.ROLL, -40)
        assertEquals(-20 * D2R, s.skeleton.state("shinL").roll, EPS)
    }

    @Test
    fun 曲げ角を戻すとひねりも範囲に収まる() {
        val s = bent(90 * D2R, -40 * D2R).skeleton
        // 太ももの真下(伸ばす向き)へ向ける
        val straight = s.aim("shinL", Vec3(0.0, -1.0, 0.0))
        assertEquals(0.0, straight.state("shinL").phi, EPS)
        assertEquals(0.0, straight.state("shinL").roll, EPS)
        // 45° まで戻すと半分に
        val half = aimPart(shinL, PartState(PI / 2, 0.0, -40 * D2R), Vec3(0.0, -1.0, -1.0))
        assertEquals(45 * D2R, half.phi, EPS)
        assertEquals(-20 * D2R, half.roll, EPS)
    }

    @Test
    fun 回転オーブのひねりも範囲に収める() {
        // すねの軸(−y)まわりに大きく回す
        val s = orbRotate(shinL, PartState(PI / 2, 0.0, 0.0), Quat.fromAxisAngle(Vec3(0.0, 0.0, 1.0), 1.2))
        val r = rollRange(shinL, s.phi)
        assertTrue(s.roll >= r.min - EPS && s.roll <= r.max + EPS)
        val straight = orbRotate(shinL, PartState(0.0, 0.0, 0.0), Quat.fromAxisAngle(Vec3(0.0, -1.0, 0.0), 0.5))
        assertEquals(0.0, straight.roll, EPS)
    }

    @Test
    fun 読み込みでも範囲に収める() {
        val text = PoseCodec.encode(bent(90 * D2R, -40 * D2R).skeleton)
            .replace(Regex("part shinL 1 \\S+ \\S+ \\S+"), "part shinL 1 ${30 * D2R} 0.0 ${-40 * D2R}")
        val sk = (PoseCodec.decode(text) as PoseDecodeResult.Ok).skeleton
        assertEquals(-40.0 / 3 * D2R, sk.state("shinL").roll, 1e-9)
    }

    @Test
    fun ひねっても玉は曲げる面の上_可動域の表示はもとの扇() {
        // E17-04(扇形の表示)は取り下げた。玉はすねの先で、ひねりでは動かない
        assertEquals(dotPosition(shinL, PartState(PI / 2, 0.0, 0.0)), dotPosition(shinL, PartState(PI / 2, 0.0, -40 * D2R)))
        val g = com.yswy.dessin.core.mesh.guideMesh(shinL)
        (0 until g.area.vertexCount).forEach { assertEquals(0.0, g.area.vertex(it).x, 1e-6) }
    }
}
