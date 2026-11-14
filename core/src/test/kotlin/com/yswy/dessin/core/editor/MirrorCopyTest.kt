package com.yswy.dessin.core.editor

import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.skeleton.HandShape
import com.yswy.dessin.core.skeleton.PartState
import com.yswy.dessin.core.skeleton.RootTransform
import com.yswy.dessin.core.skeleton.Skeleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** E15-02: 選んだ部位とその先を、反対側へ鏡写しにする */
class MirrorCopyTest {
    private val leftArm = listOf("clavL", "upperArmL", "forearmL", "handL")
    private val leftLeg = listOf("hipL", "thighL", "shinL", "footL", "toeL")

    /** 左の腕と脚を置き、それぞれ適当な向きにした状態 */
    private fun posedLeft(root: RootTransform = RootTransform()): EditorState {
        var s = EditorState(skeleton = Skeleton.initial().withRoot(root)).select("flank").select("pelvis")
        (leftArm + leftLeg).forEach { s = s.select(it) }
        var sk = s.skeleton
        sk = sk.withState("clavL", PartState(0.2, 0.7, 0.0))
        sk = sk.withState("upperArmL", PartState(0.9, 1.1, 0.4))
        sk = sk.withState("forearmL", sk.state("forearmL").copy(phi = 0.8, roll = -0.3))
        sk = sk.withState("handL", PartState(0.3, -0.5, 0.0))
        sk = sk.withState("hipL", PartState(0.1, 2.0, 0.0))
        sk = sk.withState("thighL", PartState(0.7, -0.4, 0.5))
        sk = sk.withState("shinL", sk.state("shinL").copy(phi = 1.0))
        sk = sk.withState("footL", PartState(0.3, 0.6, 0.0))
        sk = sk.withState("toeL", sk.state("toeL").copy(phi = 0.4))
        sk = sk.withHandShape("handL", HandShape.FIST)
        return s.copy(skeleton = sk)
    }

    private fun mirrorId(id: String) = id.dropLast(1) + "R"

    /** 胸の空間での位置と、胸の空間での回転 */
    private fun local(sk: Skeleton, id: String): Pair<Vec3, Quat> {
        val root = sk.worldTransforms().getValue("rib")
        val j = sk.worldTransforms().getValue(id)
        val inv = root.rotation.inverse()
        return inv.rotate(j.position - root.position) to (inv * j.rotation)
    }

    /** 体の中心の面(胸の空間の x = 0)で鏡写しにした位置と回転が一致する */
    private fun assertMirrored(sk: Skeleton, leftId: String) {
        val (pl, ql) = local(sk, leftId)
        val (pr, qr) = local(sk, mirrorId(leftId))
        assertEquals("$leftId x", -pl.x, pr.x, 1e-9)
        assertEquals("$leftId y", pl.y, pr.y, 1e-9)
        assertEquals("$leftId z", pl.z, pr.z, 1e-9)
        // 回転の鏡写し: どの向きのベクトルも、鏡写しにして回すと、回して鏡写しにしたものと同じ
        for (v in listOf(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0))) {
            val a = qr.rotate(Vec3(-v.x, v.y, v.z))
            val b = ql.rotate(v).let { Vec3(-it.x, it.y, it.z) }
            assertEquals("$leftId rot x", b.x, a.x, 1e-9)
            assertEquals("$leftId rot y", b.y, a.y, 1e-9)
            assertEquals("$leftId rot z", b.z, a.z, 1e-9)
        }
    }

    @Test
    fun 肩を選ぶと腕全体が鏡写しになる() {
        val m = posedLeft().select("clavL").mirrorSelected()
        leftArm.forEach { assertTrue(m.skeleton.isPlaced(mirrorId(it))) }
        leftArm.forEach { assertMirrored(m.skeleton, it) }
        assertEquals(HandShape.FIST, m.skeleton.handShape("handR"))
        assertEquals("clavL", m.selected)
    }

    @Test
    fun 付け根を選ぶと脚全体が鏡写しになる_ひざの転がりのずれも() {
        val m = posedLeft().select("hipL").mirrorSelected()
        leftLeg.forEach { assertMirrored(m.skeleton, it) }
    }

    @Test
    fun 胸が回っていても体の空間で鏡写しになる() {
        val root = RootTransform(Vec3(0.3, 1.0, -0.2), Quat.fromEulerYXZ(Vec3(0.3, 0.8, -0.2)), 1.2)
        val m = posedLeft(root).select("clavL").mirrorSelected()
        leftArm.forEach { assertMirrored(m.skeleton, it) }
    }

    @Test
    fun 選んだ部位より根の側は写さない_反対側の親が無ければ自然な姿勢で置く() {
        val s = posedLeft().select("upperArmL")
        assertFalse(s.skeleton.isPlaced("clavR"))
        val m = s.mirrorSelected()
        assertTrue(m.skeleton.isPlaced("clavR"))
        assertEquals(NaturalPose.state(com.yswy.dessin.core.skeleton.PartSpecs["clavR"]), m.skeleton.state("clavR"))
        assertEquals(s.skeleton.state("clavL"), m.skeleton.state("clavL"))
        listOf("upperArmL", "forearmL", "handL").forEach { assertTrue(m.skeleton.isPlaced(mirrorId(it))) }
    }

    @Test
    fun 写す元で置いていない部位は反対側でも外す() {
        var s = EditorState()
        listOf("clavL", "upperArmL", "clavR", "upperArmR", "forearmR", "handR").forEach { s = s.select(it) }
        val m = s.select("upperArmL").mirrorSelected()
        assertTrue(m.skeleton.isPlaced("upperArmR"))
        assertFalse(m.skeleton.isPlaced("forearmR"))
        assertFalse(m.skeleton.isPlaced("handR"))
    }

    @Test
    fun 右から左へも写せる() {
        var s = EditorState()
        listOf("clavR", "upperArmR").forEach { s = s.select(it) }
        s = s.copy(skeleton = s.skeleton.withState("upperArmR", PartState(0.6, -0.9, 0.2)))
        val m = s.mirrorSelected()
        assertTrue(m.skeleton.isPlaced("upperArmL"))
        val (pl, _) = local(m.skeleton, "upperArmL")
        val (pr, _) = local(m.skeleton, "upperArmR")
        assertEquals(-pr.x, pl.x, 1e-9)
    }

    @Test
    fun 左右の無い部位では写せない() {
        val s = EditorState().select("neck")
        assertFalse(s.canMirror)
        assertSame(s, s.mirrorSelected())
        assertTrue(posedLeft().select("upperArmL").canMirror)
    }
}
