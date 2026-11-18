package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertVec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

private const val D2R = PI / 180

/** 期待値は docs/web-reference.html の spec(...) から写した */
class PartSpecsTest {
    private val expectedParents = mapOf(
        "rib" to null,
        "neck" to "rib", "head" to "neck", "flank" to "rib", "pelvis" to "flank",
        "clavL" to "rib", "upperArmL" to "clavL", "forearmL" to "upperArmL", "handL" to "forearmL",
        "hipL" to "pelvis", "thighL" to "hipL", "shinL" to "thighL", "footL" to "shinL", "toeL" to "footL",
        "clavR" to "rib", "upperArmR" to "clavR", "forearmR" to "upperArmR", "handR" to "forearmR",
        "hipR" to "pelvis", "thighR" to "hipR", "shinR" to "thighR", "footR" to "shinR", "toeR" to "footR",
        "stickL" to "handL", "stickR" to "handR", // E18-01
    )

    @Test
    fun 部位は20個で親子関係がWeb版と一致する() {
        assertEquals(25, PartSpecs.all.size) // E08-03 でつま先(左右)を、E18-01 で棒(左右)を足した
        assertEquals(expectedParents, PartSpecs.all.associate { it.id to it.parent })
    }

    @Test
    fun 親は子より前に並ぶ() {
        val seen = mutableSetOf<String>()
        PartSpecs.all.forEach { p ->
            p.parent?.let { assertTrue("${p.id} の親 $it が先に無い", it in seen) }
            seen += p.id
        }
    }

    @Test
    fun 配置の段はarchitectureの部位の順序と一致する() {
        fun depth(id: String): Int = PartSpecs[id].parent?.let { depth(it) + 1 } ?: 0
        val expected = mapOf(
            0 to setOf("rib"),
            1 to setOf("clavL", "clavR", "neck", "flank"),
            2 to setOf("upperArmL", "upperArmR", "head", "pelvis"),
            3 to setOf("forearmL", "forearmR", "hipL", "hipR"),
            4 to setOf("handL", "handR", "thighL", "thighR"),
            5 to setOf("shinL", "shinR", "stickL", "stickR"), // E18-01
            6 to setOf("footL", "footR"),
            7 to setOf("toeL", "toeR"), // E08-03
        )
        assertEquals(expected, PartSpecs.all.groupBy({ depth(it.id) }, { it.id }).mapValues { it.value.toSet() })
    }

    @Test
    fun 子の一覧() {
        assertEquals(listOf("neck", "flank", "clavL", "clavR"), PartSpecs.children("rib").map { it.id })
        assertEquals(listOf("hipL", "hipR"), PartSpecs.children("pelvis").map { it.id })
        assertEquals(listOf("toeL"), PartSpecs.children("footL").map { it.id }) // E08-03
        assertEquals(emptyList<PartSpec>(), PartSpecs.children("toeL"))
    }

    @Test
    fun 肋骨は根で可動域を持たない() {
        val rib = PartSpecs["rib"]
        assertTrue(rib.isRoot)
        assertNull(rib.cone)
        assertNull(rib.hinge)
    }

    @Test
    fun 根以外はコーンかヒンジのどちらか一方だけを持つ() {
        PartSpecs.all.filterNot { it.isRoot }.forEach {
            assertTrue(it.id, (it.cone == null) != (it.hinge == null))
        }
    }

    @Test
    fun 左側の値はWeb版の定義そのまま() {
        val u = PartSpecs["upperArmL"]
        assertEquals(Side.LEFT, u.side)
        assertVec(Vec3(0.12, 0.0, 0.0), u.attach)
        assertVec(Vec3(0.0, -1.0, 0.0), u.restDir)
        assertEquals(0.25, u.length, EPS)
        assertEquals(0.25, u.guideRadius, EPS)
        assertCone(Cone(180 * D2R, 60 * D2R, 180 * D2R, 30 * D2R), u.cone!!)
        assertRoll(RollRange(-90 * D2R, 90 * D2R), u.roll)

        val pelvis = PartSpecs["pelvis"]
        assertNull(pelvis.side)
        assertVec(Vec3(0.0, -0.105, 0.0), pelvis.attach)
        assertCone(Cone(70 * D2R, 35 * D2R, 35 * D2R, 35 * D2R), pelvis.cone!!)
    }

    @Test
    fun 右側は取り付け位置と休止方向のxを反転する() {
        listOf("clav", "upperArm", "forearm", "hand", "hip", "thigh", "shin", "foot").forEach { n ->
            val l = PartSpecs[n + "L"]
            val r = PartSpecs[n + "R"]
            assertEquals(Side.RIGHT, r.side)
            assertVec(Vec3(-l.attach.x, l.attach.y, l.attach.z), r.attach)
            assertVec(Vec3(-l.restDir.x, l.restDir.y, l.restDir.z), r.restDir)
            assertEquals(l.length, r.length, EPS)
            assertEquals(l.guideRadius, r.guideRadius, EPS)
        }
        assertVec(Vec3(-1.0, 0.0, 0.0), PartSpecs["clavR"].restDir)
        assertVec(Vec3(-0.025, -0.11, 0.035), PartSpecs["hipR"].attach)
    }

    @Test
    fun 右側はコーンの正負xを入れ替える() {
        assertCone(Cone(180 * D2R, 60 * D2R, 30 * D2R, 180 * D2R), PartSpecs["upperArmR"].cone!!)
        assertCone(Cone(20 * D2R, 20 * D2R, 15 * D2R, 35 * D2R), PartSpecs["clavR"].cone!!)
        assertCone(Cone(25 * D2R, 25 * D2R, 20 * D2R, 25 * D2R), PartSpecs["hipR"].cone!!)
    }

    @Test
    fun ロールの鏡像は符号を反転して上下を入れ替える() {
        // Web版の定義はすべて対称だが、非対称のときの扱いを確かめる
        assertRoll(RollRange(-0.5, 0.2), RollRange(-0.2, 0.5).mirrored())
        assertRoll(RollRange(-90 * D2R, 90 * D2R), PartSpecs["forearmR"].roll)
    }

    @Test
    fun ヒンジは左右で同じ() {
        listOf("L", "R").forEach { s ->
            assertHinge(Hinge(PI, -15 * D2R, 150 * D2R), PartSpecs["forearm$s"].hinge!!)
            assertHinge(Hinge(0.0, 0.0, 150 * D2R), PartSpecs["shin$s"].hinge!!)
        }
    }

    @Test
    fun 足とつま先だけ基準フレームの参照軸がy() {
        // E08-03: つま先も足と同じく前を向く部位なので参照軸は y。E18-01 の棒も前を向くので y
        PartSpecs.all.filterNot { it.isRoot }.forEach {
            val expected = if (it.id.startsWith("foot") || it.id.startsWith("toe") || it.id.startsWith("stick")) Vec3.Y else Vec3.Z
            assertVec(expected, it.ref)
        }
        assertVec(Vec3.Z, PartSpecs["footL"].restDir)
    }

    @Test
    fun 調整パラメータを持つ部位() {
        assertEquals(Adjust.HEAD_SIZE, PartSpecs["head"].adjust)
        assertEquals(Adjust.SHOULDER_WIDTH, PartSpecs["clavL"].adjust)
        assertEquals(Adjust.SHOULDER_WIDTH, PartSpecs["clavR"].adjust)
        assertEquals(Adjust.PELVIS_WIDTH, PartSpecs["pelvis"].adjust)
        assertEquals(4, PartSpecs.all.count { it.adjust != null })
    }

    @Test
    fun 表示名() {
        assertEquals("胸", PartSpecs["rib"].label)
        assertEquals("左付け根", PartSpecs["hipL"].label)
        assertEquals("右すね", PartSpecs["shinR"].label)
    }

    @Test(expected = NoSuchElementException::class)
    fun 存在しない部位() {
        PartSpecs["tail"]
    }

    private fun assertCone(expected: Cone, actual: Cone) {
        assertEquals("front", expected.front, actual.front, EPS)
        assertEquals("back", expected.back, actual.back, EPS)
        assertEquals("plusX", expected.plusX, actual.plusX, EPS)
        assertEquals("minusX", expected.minusX, actual.minusX, EPS)
    }

    private fun assertRoll(expected: RollRange, actual: RollRange) {
        assertEquals("min", expected.min, actual.min, EPS)
        assertEquals("max", expected.max, actual.max, EPS)
    }

    private fun assertHinge(expected: Hinge, actual: Hinge) {
        assertNotNull(actual)
        assertEquals("psi", expected.psi, actual.psi, EPS)
        assertEquals("min", expected.min, actual.min, EPS)
        assertEquals("max", expected.max, actual.max, EPS)
    }
}

/** E04-03: 首の付け根は背骨の線(骨盤の付け根と同じ前後 −0.035) */
class NeckSpineTest {
    @Test
    fun 首の付け根は肋骨の上面の背骨の線() {
        assertVec(Vec3(0.0, 0.15, -0.035), PartSpecs["neck"].attach)
        assertEquals(PartSpecs["flank"].attach.z, PartSpecs["neck"].attach.z, EPS)
    }
}
