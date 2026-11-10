package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertSameRotation
import com.yswy.dessin.core.math.assertVec
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

/** 期待値は docs/web-reference.html の applyState・resetPart から */
class PoseTest {
    private val parts = PartSpecs.all.filterNot { it.isRoot }
    private val states = listOf(
        PartState(0.4, 1.1, 0.0), PartState(1.2, -2.5, 0.6), PartState(2.0, PI, -0.9), PartState(0.0, 0.0, 0.5),
    )

    @Test
    fun 休止状態_ヒンジ以外はψも0() {
        assertEquals(PartState(0.0, 0.0, 0.0), PartState.rest(PartSpecs["upperArmL"]))
    }

    @Test
    fun 休止状態_ヒンジはψを曲げる向きにする() {
        assertEquals(PartState(0.0, PI, 0.0), PartState.rest(PartSpecs["forearmR"]))
        assertEquals(PartState(0.0, 0.0, 0.0), PartState.rest(PartSpecs["shinL"]))
    }

    @Test
    fun 休止状態は単位回転() {
        parts.forEach { assertSameRotation(Quat.IDENTITY, localRotation(it, PartState.rest(it))) }
    }

    @Test
    fun 休止方向は状態の向きへ回る() {
        parts.forEach { p ->
            val qb = baseQuat(p.restDir, p.ref)
            states.forEach { s ->
                val expected = qb.rotate(dirVec(s.phi, s.psi))
                assertVec(expected, localRotation(p, s).rotate(p.restDir.normalized()))
            }
        }
    }

    @Test
    fun ロールは向きを変えない() {
        val p = PartSpecs["thighR"]
        val a = localRotation(p, PartState(0.8, 2.0, 0.0)).rotate(p.restDir)
        val b = localRotation(p, PartState(0.8, 2.0, 0.7)).rotate(p.restDir)
        assertVec(a, b)
    }

    @Test
    fun 振らずにロールだけなら休止方向まわりの回転() {
        parts.forEach { p ->
            assertSameRotation(Quat.fromAxisAngle(p.restDir, 0.5), localRotation(p, PartState(0.0, 0.0, 0.5)))
        }
    }

    @Test
    fun ロールを先に_振りを後に適用する() {
        // 左上腕(基準フレームは単位回転)。−y まわりに90度ひねってから、前(+z)へ90度振る:
        // x はひねりで +z へ、振りで +y へ移る
        val q = localRotation(PartSpecs["upperArmL"], PartState(PI / 2, PI, PI / 2))
        assertVec(Vec3.Y, q.rotate(Vec3.X))
        assertVec(Vec3.Z, q.rotate(Vec3(0.0, -1.0, 0.0)))
    }

    @Test
    fun 肘は前へ_膝は後ろへ曲がる() {
        val elbow = localRotation(PartSpecs["forearmL"], PartState(PI / 2, PI, 0.0))
        assertVec(Vec3.Z, elbow.rotate(Vec3(0.0, -1.0, 0.0)))
        val knee = localRotation(PartSpecs["shinL"], PartState(PI / 2, 0.0, 0.0))
        assertVec(Vec3(0.0, 0.0, -1.0), knee.rotate(Vec3(0.0, -1.0, 0.0)))
    }

    @Test
    fun 玉の位置は向きの方向に可動域の半径だけ離れる() {
        val p = PartSpecs["upperArmL"]
        assertVec(Vec3(0.0, -0.25, 0.0), dotPosition(p, PartState.rest(p)))
        assertVec(Vec3(0.0, 0.0, 0.25), dotPosition(p, PartState(PI / 2, PI, 1.0)))
        val clav = PartSpecs["clavR"]
        assertVec(Vec3(-0.2, 0.0, 0.0), dotPosition(clav, PartState.rest(clav)))
    }

    @Test
    fun 玉の位置はロールに関係しない() {
        val p = PartSpecs["footL"]
        assertVec(dotPosition(p, PartState(0.3, 1.0, 0.0)), dotPosition(p, PartState(0.3, 1.0, 0.4)))
    }
}
