package com.yswy.dessin.core.editor

import com.yswy.dessin.core.skeleton.Anchor

/**
 * 骨盤を置いているときの、今の根。置いていなければ null(根のボタンを出さない)。
 * E10-01 では脇腹を選んでいるときだけだったが、E16-02 で右上のボタンにし、選んでいる部位によらず出す
 */
val EditorState.anchorChoice: Anchor?
    get() = if (skeleton.isPlaced("pelvis")) skeleton.anchor else null

/** 根を切り替える。根のボタンを出していなければ何もしない */
fun EditorState.setAnchor(anchor: Anchor): EditorState =
    if (anchorChoice != null) copy(skeleton = skeleton.withAnchor(anchor)) else this

/** 根を胸と骨盤で入れ替える(右上の根のボタン。E16-02) */
fun EditorState.toggleAnchor(): EditorState =
    when (anchorChoice) {
        null -> this
        Anchor.CHEST -> setAnchor(Anchor.PELVIS)
        Anchor.PELVIS -> setAnchor(Anchor.CHEST)
    }
