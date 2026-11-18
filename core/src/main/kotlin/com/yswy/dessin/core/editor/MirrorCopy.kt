package com.yswy.dessin.core.editor

import com.yswy.dessin.core.skeleton.PartSpec
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.PartState
import com.yswy.dessin.core.skeleton.Skeleton
import com.yswy.dessin.core.skeleton.isHand
import com.yswy.dessin.core.skeleton.isStick

/** 左右の部位の反対側の id(upperArmL ↔ upperArmR) */
private fun oppositeOf(id: String): String = id.dropLast(1) + if (id.endsWith("L")) "R" else "L"

/** 部位とその先(子孫)。親から順 */
private fun subtreeOf(id: String): List<String> = listOf(id) + PartSpecs.children(id).flatMap { subtreeOf(it.id) }

/**
 * 反対側の部位での、鏡写しの状態(E15-02)。右の部位の定義は左の鏡像(休止方向の x を反転、基準の z は同じ)なので、
 * 基準フレームでは x 成分だけが反転する: 振れ角 phi は同じ、振る向き psi とひねり roll は符号が反転する。
 * ヒンジは曲げる向きが左右で同じなので、反対側の定義の向きにする
 */
private fun mirroredState(target: PartSpec, s: PartState): PartState =
    PartState(s.phi, target.hinge?.psi ?: -s.psi, -s.roll)

/** 選択中の部位を反対側へ写せるか(左右のある部位で、置いている) */
val EditorState.canMirror: Boolean get() = PartSpecs[selected].side != null && skeleton.isPlaced(selected)

/**
 * 選択中の部位とその先を、反対側へ鏡写しにする(E15-02)。選択は変えない。
 * - 反対側の親が置かれていなければ、親も自然な姿勢(E09)で置く(選んだ部位より根の側は写さない)
 * - 写す元で置いていない部位は、反対側でも外す
 * - 手の形と棒の形(E18-01)も写す
 */
fun EditorState.mirrorSelected(): EditorState {
    if (!canMirror) return this
    var sk: Skeleton = skeleton
    val target = oppositeOf(selected)
    // 反対側の親のうち置いていないものを、根の側から自然な姿勢で置く
    generateSequence(PartSpecs[target].parent) { PartSpecs[it].parent }.toList().asReversed().forEach { id ->
        if (!sk.isPlaced(id)) sk = sk.place(id).withState(id, NaturalPose.state(PartSpecs[id]))
    }
    for (src in subtreeOf(selected)) {
        val dst = oppositeOf(src)
        if (!skeleton.isPlaced(src)) {
            sk = sk.remove(dst)
            continue
        }
        sk = sk.place(dst).withState(dst, mirroredState(PartSpecs[dst], skeleton.state(src)))
        if (isHand(dst)) sk = sk.withHandShape(dst, skeleton.handShape(src))
        if (isStick(dst)) sk = sk.withStickShape(dst, skeleton.stickShape(src))
    }
    return copy(skeleton = sk)
}
