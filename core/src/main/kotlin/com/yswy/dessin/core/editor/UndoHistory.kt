package com.yswy.dessin.core.editor

import com.yswy.dessin.core.skeleton.Skeleton

/** Undo で戻す単位(E15-01)。場面の全員と、どれが操作中か */
data class SceneSnapshot(val dolls: List<Skeleton>, val activeDoll: Int)

/** 今の人形の状態 */
val EditorState.snapshot: SceneSnapshot get() = SceneSnapshot(dolls, activeDoll)

/**
 * 人形の状態だけを snapshot に戻す。視点・表示・モードは今のまま。
 * 選んでいた部位が戻した人形に置かれていなければ胸を選ぶ
 */
fun EditorState.restore(s: SceneSnapshot): EditorState {
    val active = s.dolls[s.activeDoll]
    return copy(
        skeleton = active,
        others = s.dolls.filterIndexed { i, _ -> i != s.activeDoll },
        activeDoll = s.activeDoll,
        selected = if (active.isPlaced(selected)) selected else "rib",
    )
}

/**
 * Undo・Redo の履歴(E15-01)。不変で、操作は新しい UndoHistory を返す。
 * 人形の変更(ポーズ・体型・人形の追加と削除・読み込み)だけを記録し、視点・表示・選択・モードの変更は記録しない。
 *
 * ドラッグやスライダーのように続けて変わる操作は begin で始め commit で終えると、1段にまとまる(その間の change は記録しない)。
 * @property past 戻せる状態(古い順)
 * @property future やり直せる状態(次にやり直すものが末尾)
 * @property pending 続けて変わる操作の始めの状態。操作の途中でなければ null
 */
data class UndoHistory(
    val past: List<SceneSnapshot> = emptyList(),
    val future: List<SceneSnapshot> = emptyList(),
    val pending: SceneSnapshot? = null,
) {
    val canUndo: Boolean get() = past.isNotEmpty() || pending != null
    val canRedo: Boolean get() = future.isNotEmpty()

    /** 1回の変更を記録する。操作の途中(begin のあと)なら、commit でまとめて記録するので何もしない */
    fun change(before: EditorState, after: EditorState): UndoHistory =
        if (pending != null) this else record(before.snapshot, after)

    /** 続けて変わる操作を始める。すでに始めていれば何もしない */
    fun begin(state: EditorState): UndoHistory = if (pending != null) this else copy(pending = state.snapshot)

    /** 続けて変わる操作を終え、始めから変わっていれば1段として記録する */
    fun commit(state: EditorState): UndoHistory {
        val before = pending ?: return this
        return copy(pending = null).record(before, state)
    }

    /** 戻す。操作の途中なら、その操作を確定してから戻す。戻せなければ state をそのまま返す */
    fun undo(state: EditorState): Pair<UndoHistory, EditorState> {
        val h = commit(state)
        val target = h.past.lastOrNull() ?: return h to state
        return h.copy(past = h.past.dropLast(1), future = h.future + state.snapshot) to state.restore(target)
    }

    /** やり直す。やり直せなければ state をそのまま返す */
    fun redo(state: EditorState): Pair<UndoHistory, EditorState> {
        val h = commit(state)
        val target = h.future.lastOrNull() ?: return h to state
        return h.copy(future = h.future.dropLast(1), past = h.past + state.snapshot) to state.restore(target)
    }

    private fun record(before: SceneSnapshot, after: EditorState): UndoHistory {
        if (before == after.snapshot) return this
        return copy(past = (past + before).takeLast(LIMIT), future = emptyList())
    }

    companion object {
        /** 戻せる段の数 */
        const val LIMIT = 100
    }
}
