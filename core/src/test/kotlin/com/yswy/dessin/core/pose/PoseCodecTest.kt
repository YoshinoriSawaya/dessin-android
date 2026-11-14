package com.yswy.dessin.core.pose

import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Ray
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertSameRotation
import com.yswy.dessin.core.math.assertVec
import com.yswy.dessin.core.skeleton.BodyParams
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.PartState
import com.yswy.dessin.core.skeleton.RootTransform
import com.yswy.dessin.core.skeleton.Skeleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

private const val D2R = PI / 180

class PoseCodecTest {
    /** いろいろな操作を加えた骨格 */
    private val posed: Skeleton = run {
        var s = Skeleton.initial()
        PartSpecs.all.forEach { s = s.place(it.id) }
        s = s.withRoot(RootTransform(Vec3(0.12, 0.95, -0.3), Quat.fromEulerYXZ(Vec3(0.3, -1.2, 0.4)), 1.3))
        s = s.aim("upperArmL", Vec3(0.3, 0.2, 0.9).normalized())
        s = s.orb("forearmL", Quat.fromAxisAngle(Vec3(1.0, 0.2, 0.0), -1.0))
        s = s.drag("thighR", Ray(Vec3(0.0, 0.5, 3.0), Vec3(0.0, 0.0, -1.0)))
        s = s.orb("neck", Quat.fromAxisAngle(Vec3(0.1, 1.0, 0.0), 0.4))
        s.remove("clavR")
    }

    private fun decodeOk(text: String): Skeleton {
        val r = PoseCodec.decode(text)
        assertTrue("読み込めるはず: $r", r is PoseDecodeResult.Ok)
        return (r as PoseDecodeResult.Ok).skeleton
    }

    private fun assertError(text: String) {
        val r = PoseCodec.decode(text)
        assertTrue("エラーになるはず: $r", r is PoseDecodeResult.Error)
        assertTrue("理由がある", (r as PoseDecodeResult.Error).reason.isNotBlank())
    }

    private fun header() = "${PoseCodec.HEADER} ${PoseCodec.VERSION}"

    // ---------- 保存して戻す ----------

    @Test
    fun 保存して戻すと同じ骨格になる() {
        assertEquals(posed, decodeOk(PoseCodec.encode(posed)))
    }

    @Test
    fun 初期状態も保存して戻せる() {
        val s = Skeleton.initial()
        assertEquals(s, decodeOk(PoseCodec.encode(s)))
    }

    @Test
    fun 先頭行は見出しと版番号() {
        assertEquals(header(), PoseCodec.encode(posed).lineSequence().first())
    }

    // ---------- 壊れたデータ ----------

    @Test
    fun 空や見出しの違うデータはエラー() {
        assertError("")
        assertError("hello\n")
        assertError("dessin-pose\n")
    }

    @Test
    fun 知らない版番号はエラー() {
        assertError("${PoseCodec.HEADER} 999\n")
        assertError("${PoseCodec.HEADER} x\n")
    }

    @Test
    fun 数値が読めない行や項目が足りない行はエラー() {
        assertError("${header()}\nroot 0 1.1 0 0 0 0 1 abc\n")
        assertError("${header()}\nbody 1 1 1 0.04\n") // 版3に体型の行は無い(E07-03)
        assertError("dessin-pose 2\nbody 1 1 1\n")
        assertError("${header()}\npart neck 1 0.1 0.2\n")
        assertError("${header()}\npart neck 2 0 0 0\n")
        assertError("${header()}\nunknown 1 2 3\n")
    }

    @Test
    fun 有限でない数値はエラー() {
        assertError("${header()}\nroot 0 NaN 0 0 0 0 1 1\n")
        assertError("${header()}\npart neck 1 Infinity 0 0\n")
    }

    // ---------- 足りない値・範囲外の値 ----------

    @Test
    fun 見出しだけなら初期状態() {
        assertEquals(Skeleton.initial(), decodeOk(header() + "\n"))
    }

    @Test
    fun 知らない部位の行は無視する() {
        assertEquals(Skeleton.initial(), decodeOk("${header()}\npart tail 1 0 0 0\n"))
    }

    @Test
    fun 状態は可動域に収める() {
        val s = decodeOk("${header()}\npart neck 1 3.0 0 2.0\n")
        // 首の後ろ(ψ=0)は50度、ロールは ±30度
        assertEquals(50 * D2R, s.state("neck").phi, EPS)
        assertEquals(30 * D2R, s.state("neck").roll, EPS)
    }

    @Test
    fun ヒンジは曲げる向きを固定して角度を範囲に収める() {
        val s = decodeOk("${header()}\npart clavL 1 0 0 0\npart upperArmL 1 0 0 0\npart forearmL 1 -1.0 0.5 0\n")
        assertEquals(PartState(-15 * D2R, PI, 0.0), s.state("forearmL"))
    }

    @Test
    fun 負のφは0にする() {
        val s = decodeOk("${header()}\npart neck 1 -0.5 1.0 0\n")
        assertEquals(0.0, s.state("neck").phi, EPS)
    }

    @Test
    fun 体型と肋骨の大きさは範囲に収める() {
        // 体型の行は版2までの古いデータにだけある(E07-03)
        val s = decodeOk("dessin-pose 2\nbody 9 0.1 2 -1\nroot 0 1 0 0 0 0 1 5\n")
        assertEquals(BodyParams(headSize = 1.4, shoulderWidth = 0.4, pelvisWidth = 1.5, hipInset = 0.0), s.body)
        assertEquals(1.6, s.root.scale, EPS)
    }

    @Test
    fun 肋骨の回転は正規化する_長さ0なら回さない() {
        val s = decodeOk("${header()}\nroot 0 1 0 0 0 2 2 1\n")
        assertSameRotation(Quat.fromAxisAngle(Vec3.Z, PI / 2), s.root.rotation)
        assertVec(Vec3(0.0, 1.0, 0.0), s.root.position)
        val zero = decodeOk("${header()}\nroot 0 1 0 0 0 0 0 1\n")
        assertEquals(Quat.IDENTITY, zero.root.rotation)
    }

    @Test
    fun 親が置かれていない部位は外して休止状態にする() {
        val s = decodeOk("${header()}\npart head 1 0.3 0.2 0.1\npart clavL 1 0.1 1.0 0\n")
        assertEquals(setOf("rib", "clavL"), s.placed)
        assertEquals(PartState.rest(PartSpecs["head"]), s.state("head"))
    }

    @Test
    fun 置いていない部位は休止状態にする() {
        val s = decodeOk("${header()}\npart neck 0 0.3 0.2 0.1\n")
        assertEquals(setOf("rib"), s.placed)
        assertEquals(PartState.rest(PartSpecs["neck"]), s.state("neck"))
    }

    @Test
    fun 肋骨は常に置かれている() {
        assertTrue("rib" in decodeOk("${header()}\npart rib 0 0 0 0\n").placed)
    }

    @Test
    fun 前後の空白と空行と改行コードの違いは許す() {
        val text = PoseCodec.encode(posed).replace("\n", "\r\n\r\n") + "   "
        assertEquals(posed, decodeOk("  $text"))
    }

    // ---------- E07-03: ポーズは体型を持たない ----------

    @Test
    fun 版3は体型の行を書かない() {
        assertEquals("dessin-pose 3", header())
        val text = PoseCodec.encode(posed.withBody(BodyParams(headSize = 1.3)))
        assertTrue(text.lines().none { it.startsWith("body") })
        // 読み込むと体型は既定の値
        assertEquals(BodyParams(), decodeOk(text).body)
    }

    @Test
    fun 版2の体型の行は読める() {
        val s = decodeOk("dessin-pose 2\nbody 1.2 0.8 1.4 0.03\n")
        assertEquals(BodyParams(headSize = 1.2, shoulderWidth = 0.8, pelvisWidth = 1.4, hipInset = 0.03), s.body)
    }
}
