package com.yswy.dessin.core.editor

import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.skeleton.PartSpec
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.PartState
import com.yswy.dessin.core.skeleton.Skeleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 自然な姿勢(E09)。部位を初めて置いたときと「向きを戻す」で入る、各部位の状態。
 * 骨格の計算の休止状態(可動域の基準 0°)はまっすぐのままで、ここは画面の操作で使う初期値だけを決める。
 *
 * - 首は前へ 15°、頭は後ろへ 15° 戻して傾きなし
 * - 脇腹は前へ 10°(上の端の胸が前に出る傾き。下へ向かって後ろへ傾く)、骨盤は戻して傾きなし
 * - 付け根は下へ 20°、太ももは外向き 5°(ハの字)。付け根と太ももの角は約 115°
 * - ほかの部位はまっすぐ
 *
 * 角度は、胸がまっすぐのときのワールドの向きで決め、可動域の計算(aim)で各部位の状態に直す。
 */
object NaturalPose {
    private const val D2R = PI / 180

    private fun deg(d: Double) = d * D2R

    /** 胸がまっすぐのときの、部位の向き */
    private val directions: Map<String, Vec3> = buildMap {
        put("neck", Vec3(0.0, cos(deg(15.0)), sin(deg(15.0))))
        put("head", Vec3(0.0, 1.0, 0.0))
        put("flank", Vec3(0.0, -cos(deg(10.0)), -sin(deg(10.0))))
        put("pelvis", Vec3(0.0, -1.0, 0.0))
        for ((s, sg) in listOf("L" to 1.0, "R" to -1.0)) {
            put("hip$s", Vec3(sg * cos(deg(20.0)), -sin(deg(20.0)), 0.0))
            put("thigh$s", Vec3(sg * sin(deg(5.0)), -cos(deg(5.0)), 0.0))
        }
    }

    /** 親から順に置いて向けた結果の状態(親の向きを打ち消した、親に対する状態) */
    private val states: Map<String, PartState> by lazy {
        var s = Skeleton.initial()
        PartSpecs.all.filterNot { it.isRoot }.forEach { spec ->
            s = s.place(spec.id)
            directions[spec.id]?.let { s = s.aim(spec.id, it) }
        }
        directions.keys.associateWith { s.state(it) }
    }

    fun state(spec: PartSpec): PartState = states[spec.id] ?: PartState.rest(spec)

    /**
     * 置いていない部位を自然な姿勢にする。置く前の半透明の影を、置いたときと同じ角度で描くため
     * (2026-11-14 の不具合: 付け根を操作中の太ももの影がずれていた)
     */
    fun unplacedNatural(skeleton: Skeleton): Skeleton =
        PartSpecs.all.filterNot { it.isRoot || skeleton.isPlaced(it.id) }
            .fold(skeleton) { s, spec -> s.withState(spec.id, state(spec)) }

    /** 胸だけを置き、ほかを自然な姿勢にした初めの状態 */
    val initial: Skeleton by lazy { unplacedNatural(Skeleton.initial()) }
}
