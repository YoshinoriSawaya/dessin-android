package com.yswy.dessin.core.editor

import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.math.assertVec
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.PartState
import com.yswy.dessin.core.skeleton.Skeleton
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

private const val D2R = PI / 180

/** E09: 部位を置いたときと「向きを戻す」で入る、自然な姿勢(前傾とハの字) */
class NaturalPoseTest {
    private val placed = listOf("neck", "head", "flank", "pelvis", "hipL", "thighL", "hipR", "thighR", "clavL", "upperArmL")
        .fold(EditorState()) { s, id -> s.select(id) }

    /** 部位の向き(関節から玉への向き)。ワールド */
    private fun dir(s: EditorState, id: String) = (s.skeleton.dotWorld(id) - s.skeleton.guideCenter(id)).normalized()

    @Test
    fun 首は前へ15度_頭は傾きなし() {
        assertVec(Vec3(0.0, cos(15 * D2R), sin(15 * D2R)), dir(placed, "neck"))
        assertVec(Vec3(0.0, 1.0, 0.0), dir(placed, "head"))
    }

    @Test
    fun 脇腹は前へ10度_骨盤は傾きなし_骨盤は胸より少し後ろ() {
        // 脇腹は胸から下へ伸びるので、上の端(胸)が前に出る傾きは、下へ向かって後ろへ傾く向き
        assertVec(Vec3(0.0, -cos(10 * D2R), -sin(10 * D2R)), dir(placed, "flank"))
        assertVec(Vec3(0.0, -1.0, 0.0), dir(placed, "pelvis"))
        val w = placed.skeleton.worldTransforms()
        assert(w.getValue("pelvis").position.z < w.getValue("flank").position.z)
    }

    @Test
    fun 付け根は下へ20度_太ももは外向き5度のハの字_左右は鏡像() {
        assertVec(Vec3(cos(20 * D2R), -sin(20 * D2R), 0.0), dir(placed, "hipL"))
        assertVec(Vec3(-cos(20 * D2R), -sin(20 * D2R), 0.0), dir(placed, "hipR"))
        assertVec(Vec3(sin(5 * D2R), -cos(5 * D2R), 0.0), dir(placed, "thighL"))
        assertVec(Vec3(-sin(5 * D2R), -cos(5 * D2R), 0.0), dir(placed, "thighR"))
        // 付け根(骨盤へ戻る向き)と太ももの角は鈍角(約115°)
        val angle = acos(-dir(placed, "hipL").dot(dir(placed, "thighL"))) / D2R
        assertEquals(115.0, angle, 0.5)
    }

    @Test
    fun ほかの部位はまっすぐ() {
        assertEquals(PartState.rest(PartSpecs["upperArmL"]), placed.skeleton.state("upperArmL"))
        assertEquals(PartState.rest(PartSpecs["clavL"]), placed.skeleton.state("clavL"))
    }

    @Test
    fun 向きを戻すと自然な姿勢に戻る() {
        val moved = placed.select("neck").let { it.copy(skeleton = it.skeleton.withState("neck", PartState(0.4, 1.0, 0.2))) }
        assertEquals(placed.skeleton.state("neck"), moved.resetSelected().skeleton.state("neck"))
    }

    @Test
    fun 置いてある部位を選び直しても向きは変えない() {
        val moved = placed.select("neck").let { it.copy(skeleton = it.skeleton.withState("neck", PartState(0.4, 1.0, 0.2))) }
        assertEquals(PartState(0.4, 1.0, 0.2), moved.select("rib").select("neck").skeleton.state("neck"))
    }

    @Test
    fun 骨格の計算の休止状態はまっすぐのまま() {
        assertEquals(PartState.rest(PartSpecs["neck"]), Skeleton.initial().place("neck").state("neck"))
    }
}
