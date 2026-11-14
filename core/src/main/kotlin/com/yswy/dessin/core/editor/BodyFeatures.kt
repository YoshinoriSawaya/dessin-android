package com.yswy.dessin.core.editor

import com.yswy.dessin.core.skeleton.Sex

/** 選択中が骨盤なら今の性別。ほかの部位なら null(パネルに性別の行を出さない)。E08-04 */
val EditorState.sexChoice: Sex?
    get() = if (selected == "pelvis") skeleton.body.sex else null

/** 骨盤を選んでいるときに性別を変える(体型の項目)。ほかの部位を選んでいれば何もしない */
fun EditorState.setSex(sex: Sex): EditorState =
    if (selected == "pelvis") copy(skeleton = skeleton.withBody(skeleton.body.copy(sex = sex))) else this
