package com.yswy.dessin.ui

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.yswy.dessin.core.pose.BodyLibrary
import com.yswy.dessin.core.pose.LibraryResult
import com.yswy.dessin.core.pose.NamedLibrary
import com.yswy.dessin.core.pose.PoseLibrary
import com.yswy.dessin.core.pose.SavedPoseSummary
import com.yswy.dessin.core.skeleton.BodyParams
import com.yswy.dessin.core.skeleton.Skeleton
import com.yswy.dessin.storage.FilePoseStore
import java.io.File

/** 保存するものの種類。label はダイアログや文言に使う */
enum class SaveKind(val label: String) { POSE("ポーズ"), BODY("体型") }

/** 表示中の保存のダイアログ */
sealed interface PoseDialog {
    val kind: SaveKind
    data class Save(override val kind: SaveKind, val suggestedName: String) : PoseDialog
    data class List(override val kind: SaveKind, val items: kotlin.collections.List<SavedPoseSummary>) : PoseDialog
}

/**
 * 画面から使う、名前を付けて保存したものの一覧。:core の NamedLibrary をアプリ専用のフォルダにつなぐ。
 * 失敗したら理由をログに残し、利用者には短い文言だけを出す(CLAUDE.md の方針)。
 * ファイルは小さいので、読み書きは UI のスレッドで行う。
 */
class Shelf<T>(private val context: Context, private val library: NamedLibrary<T>, private val label: String) {
    fun suggestName(): String = library.suggestName()

    /** 一覧。読めなければ null(文言を出す) */
    fun list(): kotlin.collections.List<SavedPoseSummary>? = when (val r = library.list()) {
        is LibraryResult.Ok -> {
            if (library.lastBroken.isNotEmpty()) Log.w(TAG, "読めない保存ファイルを一覧から外した($label): ${library.lastBroken}")
            r.value
        }
        is LibraryResult.Error -> fail("保存した${label}の一覧を読めませんでした", r.reason)
    }

    /** 保存できたら true */
    fun save(name: String, item: T): Boolean = when (val r = library.save(name, item)) {
        is LibraryResult.Ok -> {
            toast("「${r.value.name}」を保存しました")
            true
        }
        is LibraryResult.Error -> fail("保存できませんでした", r.reason) ?: false
    }

    fun load(id: String): T? = when (val r = library.load(id)) {
        is LibraryResult.Ok -> r.value
        is LibraryResult.Error -> fail("読み込めませんでした", r.reason)
    }

    fun delete(id: String) {
        val r = library.delete(id)
        if (r is LibraryResult.Error) fail<Unit>("削除できませんでした", r.reason)
    }

    private fun <R> fail(message: String, reason: String): R? {
        Log.w(TAG, "$message($label): $reason")
        toast(message)
        return null
    }

    private fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val TAG = "Shelf"

        /** ポーズは files/poses、体型は files/bodies(E07-03) */
        fun poses(context: Context): Shelf<Skeleton> =
            Shelf(context, PoseLibrary(FilePoseStore(File(context.filesDir, "poses"))), SaveKind.POSE.label)

        fun bodies(context: Context): Shelf<BodyParams> =
            Shelf(context, BodyLibrary(FilePoseStore(File(context.filesDir, "bodies"), ".body")), SaveKind.BODY.label)
    }
}
