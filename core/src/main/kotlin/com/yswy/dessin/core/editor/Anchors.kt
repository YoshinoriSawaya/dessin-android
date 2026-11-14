package com.yswy.dessin.core.editor

import com.yswy.dessin.core.skeleton.Anchor

/** 脇腹を選んでいて骨盤を置いているときの、今の根。それ以外は null(パネルに根の行を出さない)。E10-01 */
val EditorState.anchorChoice: Anchor?
    get() = if (selected == "flank" && skeleton.isPlaced("pelvis")) skeleton.anchor else null

/** 根を切り替える。根の行を出していなければ何もしない */
fun EditorState.setAnchor(anchor: Anchor): EditorState =
    if (anchorChoice != null) copy(skeleton = skeleton.withAnchor(anchor)) else this
