package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertVec
import com.yswy.dessin.core.pose.PoseCodec
import com.yswy.dessin.core.pose.PoseDecodeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

private const val D2R = PI / 180

/** E05-01: 肋骨を胸(根)と脇腹に分ける。高さの比は 6.5:3.5 */
class ChestFlankTest {
    private val ribPos = Vec3(0.0, 1.10, 0.0)

    @Test
    fun 胸と脇腹と骨盤の親子() {
        assertEquals(25, PartSpecs.all.size) // E08-03 でつま先(左右)を、E18-01 で棒(左右)を足した
        assertEquals(null, PartSpecs["rib"].parent)
        assertEquals("rib", PartSpecs["flank"].parent)
        assertEquals("flank", PartSpecs["pelvis"].parent)
        assertEquals("胸", PartSpecs["rib"].label)
        assertEquals("脇腹", PartSpecs["flank"].label)
    }

    @Test
    fun 脇腹の関節は分かれ目の背骨の線() {
        // 肋骨の高さ 0.30 を上から 6.5 割(0.195)で分ける
        assertVec(Vec3(0.0, 0.15 - 0.195, -0.035), PartSpecs["flank"].attach)
        assertVec(Vec3(0.0, -1.0, 0.0), PartSpecs["flank"].restDir)
        assertEquals(0.105, PartSpecs["flank"].length, EPS)
    }

    @Test
    fun 脇腹の可動域() {
        val f = PartSpecs["flank"]
        assertEquals(Cone(25 * D2R, 20 * D2R, 20 * D2R, 20 * D2R), f.cone)
        assertEquals(RollRange(-30 * D2R, 30 * D2R), f.roll)
    }

    @Test
    fun 休止状態では骨盤の位置は分ける前と同じ() {
        val s = Skeleton.initial().place("flank").place("pelvis")
        assertVec(ribPos + Vec3(0.0, -0.15, -0.035), s.guideCenter("pelvis"))
    }

    @Test
    fun 脇腹を曲げると骨盤以下が追従する() {
        val s = Skeleton.initial().place("flank").place("pelvis").place("hipL").place("thighL")
        val bent = s.withState("flank", PartState(20 * D2R, PI, 0.0))
        assertTrue(bent.guideCenter("thighL").distanceTo(s.guideCenter("thighL")) > 0.01)
        // 前へ曲げると(脇腹の先が前へ)、骨盤は前へ出る
        assertTrue(bent.guideCenter("pelvis").z > s.guideCenter("pelvis").z)
    }

    @Test
    fun 古い保存データは脇腹を休止状態で置いて読み込む() {
        val v1 = "dessin-pose 1\npart pelvis 1 0.2 1.0 0.1\npart hipL 1 0 0 0\n"
        val r = PoseCodec.decode(v1)
        assertTrue(r is PoseDecodeResult.Ok)
        val s = (r as PoseDecodeResult.Ok).skeleton
        assertTrue(s.isPlaced("flank"))
        assertEquals(PartState.rest(PartSpecs["flank"]), s.state("flank"))
        assertTrue(s.isPlaced("pelvis"))
        assertTrue(s.isPlaced("hipL"))
    }

    @Test
    fun 新しい保存データは版2以降() {
        // E07-03 で版3(体型の行を外した)に上げた。脇腹を含めて保存して戻せることは変わらない
        assertTrue(PoseCodec.VERSION >= 2)
        val s = Skeleton.initial().place("flank").place("pelvis")
        assertEquals(s, (PoseCodec.decode(PoseCodec.encode(s)) as PoseDecodeResult.Ok).skeleton)
    }
}
