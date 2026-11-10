package com.yswy.dessin.ui

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.yswy.dessin.core.pose.LibraryResult
import com.yswy.dessin.core.pose.PoseLibrary
import com.yswy.dessin.core.pose.SavedPoseSummary
import com.yswy.dessin.core.skeleton.Skeleton
import com.yswy.dessin.storage.FilePoseStore
import java.io.File

/** 表示中のポーズのダイアログ */
sealed interface PoseDialog {
    data class Save(val suggestedName: String) : PoseDialog
    data class List(val items: kotlin.collections.List<SavedPoseSummary>) : PoseDialog
}

/**
 * 画面から使う保存したポーズの一覧。:core の PoseLibrary をアプリ専用のフォルダ(files/poses)につなぐ。
 * 失敗したら理由をログに残し、利用者には短い文言だけを出す(CLAUDE.md の方針)。
 * ファイルは小さいので、読み書きは UI のスレッドで行う。
 */
class PoseShelf(private val context: Context) {
    private val library = PoseLibrary(FilePoseStore(File(context.filesDir, "poses")))

    fun suggestName(): String = library.suggestName()

    /** 一覧。読めなければ null(文言を出す) */
    fun list(): kotlin.collections.List<SavedPoseSummary>? = when (val r = library.list()) {
        is LibraryResult.Ok -> {
            if (library.lastBroken.isNotEmpty()) Log.w(TAG, "読めない保存ファイルを一覧から外した: ${library.lastBroken}")
            r.value
        }
        is LibraryResult.Error -> fail("保存したポーズの一覧を読めませんでした", r.reason)
    }

    /** 保存できたら true */
    fun save(name: String, skeleton: Skeleton): Boolean = when (val r = library.save(name, skeleton)) {
        is LibraryResult.Ok -> {
            toast("「${r.value.name}」を保存しました")
            true
        }
        is LibraryResult.Error -> fail("保存できませんでした", r.reason) ?: false
    }

    fun load(id: String): Skeleton? = when (val r = library.load(id)) {
        is LibraryResult.Ok -> r.value
        is LibraryResult.Error -> fail("読み込めませんでした", r.reason)
    }

    fun delete(id: String) {
        val r = library.delete(id)
        if (r is LibraryResult.Error) fail<Unit>("削除できませんでした", r.reason)
    }

    private fun <T> fail(message: String, reason: String): T? {
        Log.w(TAG, "$message: $reason")
        toast(message)
        return null
    }

    private fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    private companion object {
        const val TAG = "PoseShelf"
    }
}
