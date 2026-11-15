package com.yswy.dessin.core.skeleton

import com.yswy.dessin.core.editor.EditorState
import com.yswy.dessin.core.editor.EditorStateCodec
import com.yswy.dessin.core.editor.anchorChoice
import com.yswy.dessin.core.editor.loadPose
import com.yswy.dessin.core.editor.setAnchor
import com.yswy.dessin.core.math.EPS
import com.yswy.dessin.core.math.Quat
import com.yswy.dessin.core.math.Ray
import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertSameRotation
import com.yswy.dessin.core.math.assertVec
import com.yswy.dessin.core.pose.PoseCodec
import com.yswy.dessin.core.pose.PoseDecodeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

private const val D2R = PI / 180

/** E10-01: 骨盤を根にすると、脇腹を動かしても骨盤は止まり、上半身が動く */
class AnchorTest {
    private val body = listOf("flank", "pelvis", "hipL", "thighL", "neck")
        .fold(Skeleton.initial()) { s, id -> s.place(id) }
    private val anchored = body.withAnchor(Anchor.PELVIS)
    private val bent = PartState(20 * D2R, 0.0, 0.0)

    private fun w(s: Skeleton, id: String) = s.worldTransforms().getValue(id)

    @Test
    fun 既定は胸が根_脇腹を曲げると骨盤が動く() {
        assertEquals(Anchor.CHEST, Skeleton.initial().anchor)
        val moved = body.withState("flank", bent)
        assertTrue(w(moved, "pelvis").position.distanceTo(w(body, "pelvis").position) > 0.01)
        assertVec(w(body, "rib").position, w(moved, "rib").position)
    }

    @Test
    fun 骨盤が根なら脇腹を曲げても骨盤と脚は動かず_胸が動く() {
        val moved = anchored.withState("flank", bent)
        assertEquals(bent, moved.state("flank"))
        for (id in listOf("pelvis", "thighL")) {
            assertVec(w(anchored, id).position, w(moved, id).position)
            assertSameRotation(w(anchored, id).rotation, w(moved, id).rotation)
        }
        assertTrue(w(moved, "rib").position.distanceTo(w(anchored, "rib").position) > 0.01)
    }

    @Test
    fun 骨盤を置いていなければ胸が根と同じ() {
        val noPelvis = Skeleton.initial().place("flank").withAnchor(Anchor.PELVIS)
        assertVec(w(noPelvis, "rib").position, w(noPelvis.withState("flank", bent), "rib").position)
    }

    // E16-01 で、骨盤が根のときのみぞおちの操作は脇腹から胸へ移った(脇腹は骨盤の関節で動かす。WaistJointTest)。
    // 玉と範囲の置き方は同じなので、胸を選んだときとして確かめる
    @Test
    fun 骨盤が根なら胸の玉は胸の上向き_範囲は脇腹の向きに上下を反転して置く() {
        val s = anchored.withState("flank", bent)
        // 中心は脇腹の関節(みぞおち)
        assertVec(w(s, "flank").position, s.guideCenter("rib"))
        val chestUp = w(s, "rib").rotation.rotate(Vec3.Y)
        assertVec(chestUp, (s.dotWorld("rib") - s.guideCenter("rib")).normalized())
        assertEquals(s.guideRadius("rib"), s.dotWorld("rib").distanceTo(s.guideCenter("rib")), EPS)
        // 範囲の表示: 脇腹の向きで、上下(y)を反転。玉は範囲の上にある(反転した範囲で、状態の向きの点が玉)
        val m = s.guideMatrix("rib")
        val onGuide = m.transformPoint(dotPosition(PartSpecs["flank"], s.state("flank")))
        assertVec(s.dotWorld("rib"), onGuide)
        // 胸が根のときは親(胸)の向きでそのまま
        val n = body.withState("flank", bent)
        assertVec(n.dotWorld("flank"), n.guideMatrix("flank").transformPoint(dotPosition(PartSpecs["flank"], n.state("flank"))))
    }

    @Test
    fun 骨盤が根なら玉を前へドラッグすると上半身が前へ曲がる() {
        val c = anchored.guideCenter("flank")
        val r = anchored.guideRadius("flank")
        val target = c + Vec3(0.0, Math.cos(15 * D2R), Math.sin(15 * D2R)) * r
        // 玉は胸の上向き(はじめは真上)にある
        val ray = Ray(target + Vec3(3.0, 0.0, 0.0), Vec3(-1.0, 0.0, 0.0))
        val s = anchored.drag("flank", ray)
        assertTrue("胸が前へ: ${w(s, "rib").position.z}", w(s, "rib").position.z > w(anchored, "rib").position.z + 0.005)
        // 玉はドラッグした向きへ
        assertVec(Vec3(0.0, Math.cos(15 * D2R), Math.sin(15 * D2R)), (s.dotWorld("flank") - s.guideCenter("flank")).normalized())
        assertVec(w(anchored, "pelvis").position, w(s, "pelvis").position)
    }

    @Test
    fun 骨盤が根なら回転オーブで前へ回すと上半身が前へ曲がる() {
        val forward = Quat.fromAxisAngle(Vec3.X, 10 * D2R)
        val s = anchored.orb("flank", forward)
        assertTrue(w(s, "rib").position.z > w(anchored, "rib").position.z + 0.005)
        assertVec(w(anchored, "pelvis").position, w(s, "pelvis").position)
        assertSameRotation(forward * w(anchored, "rib").rotation, w(s, "rib").rotation)
        // 胸が根のときは同じ回転で骨盤(下半身)のほうが動く
        val c = body.orb("flank", forward)
        assertTrue(w(c, "pelvis").position.distanceTo(w(body, "pelvis").position) > 0.005)
        assertVec(w(body, "rib").position, w(c, "rib").position)
    }

    @Test
    fun 根はポーズに保存しない_読み込んでもいまの根を保つ_画面の状態には残る() {
        val text = PoseCodec.encode(anchored)
        assertEquals(Anchor.CHEST, (PoseCodec.decode(text) as PoseDecodeResult.Ok).skeleton.anchor)
        val e = EditorState(skeleton = com.yswy.dessin.core.editor.NaturalPose.unplacedNatural(anchored))
        assertEquals(Anchor.PELVIS, e.loadPose(body, 1.0).skeleton.anchor)
        assertEquals(e, EditorStateCodec.decode(EditorStateCodec.encode(e)))
    }

    @Test
    fun 脇腹を選んでいて骨盤を置いているときだけ根を選べる() {
        val e = EditorState(skeleton = body)
        assertNull(e.anchorChoice)
        val flank = e.select("flank")
        assertEquals(Anchor.CHEST, flank.anchorChoice)
        assertEquals(Anchor.PELVIS, flank.setAnchor(Anchor.PELVIS).skeleton.anchor)
        assertNull(EditorState().select("flank").anchorChoice)
        assertEquals(e, e.setAnchor(Anchor.PELVIS))
    }
}
