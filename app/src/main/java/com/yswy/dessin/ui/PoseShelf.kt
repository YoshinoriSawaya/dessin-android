package com.yswy.dessin.ui

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.yswy.dessin.core.pose.BodyLibrary
import com.yswy.dessin.core.pose.DefaultBody
import com.yswy.dessin.core.pose.LibraryResult
import com.yswy.dessin.core.pose.NamedLibrary
import com.yswy.dessin.core.pose.SavedItem
import com.yswy.dessin.core.pose.SceneLibrary
import com.yswy.dessin.core.pose.SavedPoseSummary
import com.yswy.dessin.core.skeleton.BodyParams
import com.yswy.dessin.storage.FilePoseStore
import java.io.File

/** 保存するものの種類。label はダイアログや文言に使う */
enum class SaveKind(val label: String) { POSE("場面"), BODY("体型") }

/** 表示中の保存のダイアログ */
sealed interface PoseDialog {
    val kind: SaveKind
    data class Save(override val kind: SaveKind, val suggestedName: String) : PoseDialog
    /** defaultId は既定にした体型の id(体型の一覧だけ。E07-04) */
    data class List(override val kind: SaveKind, val items: kotlin.collections.List<SavedPoseSummary>, val defaultId: String? = null) : PoseDialog
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

        /** 場面(E13-04。今までのポーズのファイルも読む)は files/poses、体型は files/bodies(E07-03) */
        fun poses(context: Context): Shelf<SavedItem> =
            Shelf(context, SceneLibrary(FilePoseStore(File(context.filesDir, "poses"))), SaveKind.POSE.label)

        fun bodies(context: Context): Shelf<BodyParams> =
            Shelf(context, bodyLibrary(context), SaveKind.BODY.label)

        internal fun bodyLibrary(context: Context) = BodyLibrary(FilePoseStore(File(context.filesDir, "bodies"), ".body"))
    }
}

/**
 * 既定の体型(E07-04)。選んだ体型の id は files/settings の default-body.setting に置く。
 * 失敗したら理由をログに残し、利用者には短い文言だけを出す。読めなければ固定の値で続ける。
 */
class DefaultBodyShelf(private val context: Context) {
    private val defaults = DefaultBody(FilePoseStore(File(context.filesDir, "settings"), ".setting"), Shelf.bodyLibrary(context))

    /** 既定にした体型の id。選んでいないか読めなければ null */
    fun id(): String? = when (val r = defaults.id()) {
        is LibraryResult.Ok -> r.value
        is LibraryResult.Error -> fail("既定の体型を読めませんでした", r.reason)
    }

    /** 既定の体型。選んでいないか読めなければ固定の値 */
    fun body(): BodyParams = when (val r = defaults.body()) {
        is LibraryResult.Ok -> r.value
        is LibraryResult.Error -> fail("既定の体型を読めませんでした", r.reason) ?: BodyParams()
    }

    fun set(item: SavedPoseSummary) = report(defaults.set(item.id), "「${item.name}」を既定にしました", "既定にできませんでした")

    fun clear() = report(defaults.clear(), "既定を外しました", "既定を外せませんでした")

    /** 体型を消したあとに呼ぶ。消したのが既定なら既定を外す */
    fun forget(deletedId: String) {
        val r = defaults.forget(deletedId)
        if (r is LibraryResult.Error) fail<Unit>("既定を外せませんでした", r.reason)
    }

    private fun report(r: LibraryResult<Unit>, done: String, failed: String) {
        when (r) {
            is LibraryResult.Ok -> Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
            is LibraryResult.Error -> fail<Unit>(failed, r.reason)
        }
    }

    private fun <R> fail(message: String, reason: String): R? {
        Log.w("DefaultBodyShelf", "$message: $reason")
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        return null
    }
}
