package com.yswy.dessin.core.editor

import com.yswy.dessin.core.skeleton.HandShape
import com.yswy.dessin.core.skeleton.isHand

/** 選択中の手の形。手を選んでいなければ null(パネルに手の形の行を出さない)。E08-02 */
val EditorState.handShapeChoice: HandShape?
    get() = if (isHand(selected)) skeleton.handShape(selected) else null

/** 選択中の手の形を変える。手を選んでいなければ何もしない */
fun EditorState.setHandShape(shape: HandShape): EditorState =
    if (isHand(selected)) copy(skeleton = skeleton.withHandShape(selected, shape)) else this
