package com.yswy.dessin.core.pose

import com.yswy.dessin.core.skeleton.BodyParams
import java.io.IOException

/**
 * 保存した体型から選んだ既定の体型(E07-04)。起動したとき・人形を足したとき・「既定の体型に戻す」で使う。
 * 選んだ体型の id を settings に1件("default-body")で覚える。選んでいなければ固定の値 BodyParams()。
 */
class DefaultBody(private val settings: PoseStore, private val library: BodyLibrary) {
    /** 既定にした体型の id。選んでいなければ null */
    fun id(): LibraryResult<String?> = io {
        if (KEY in settings.list()) settings.read(KEY).trim().ifEmpty { null } else null
    }

    fun set(id: String): LibraryResult<Unit> = io { settings.write(KEY, id) }

    /** 既定を外し、固定の値に戻す。選んでいなくても成功する */
    fun clear(): LibraryResult<Unit> = io { if (KEY in settings.list()) settings.delete(KEY) }

    /** 体型を消したあとに呼ぶ。消したのが既定の体型なら既定を外す */
    fun forget(deletedId: String): LibraryResult<Unit> = when (val r = id()) {
        is LibraryResult.Ok -> if (r.value == deletedId) clear() else LibraryResult.Ok(Unit)
        is LibraryResult.Error -> r
    }

    /** 既定の体型。選んでいなければ固定の値。選んだ体型を読めなければエラー(画面は固定の値で続ける) */
    fun body(): LibraryResult<BodyParams> = when (val r = id()) {
        is LibraryResult.Ok -> r.value?.let { library.load(it) } ?: LibraryResult.Ok(BodyParams())
        is LibraryResult.Error -> r
    }

    private inline fun <R> io(block: () -> R): LibraryResult<R> = try {
        LibraryResult.Ok(block())
    } catch (e: IOException) {
        LibraryResult.Error(e.message ?: e.toString())
    }

    private companion object {
        const val KEY = "default-body"
    }
}
