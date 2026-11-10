package com.yswy.dessin.core.pose

import com.yswy.dessin.core.skeleton.Skeleton
import java.io.IOException

/** 保存したポーズの置き場所。1件を1つの文字列として id で読み書きする。失敗したら IOException */
interface PoseStore {
    fun list(): List<String>
    fun read(id: String): String
    fun write(id: String, text: String)
    fun delete(id: String)
}

sealed interface LibraryResult<out T> {
    data class Ok<T>(val value: T) : LibraryResult<T>

    /** reason は内部ログ向けの理由。利用者には短い文言だけを出す */
    data class Error(val reason: String) : LibraryResult<Nothing>
}

/** 一覧に出す1件。savedAt は保存した時刻(エポックからのミリ秒) */
data class SavedPoseSummary(val id: String, val name: String, val savedAt: Long)

/**
 * 名前を付けて保存したポーズの一覧(E03-02)。1件のファイルの形式:
 * ```
 * dessin-saved 1
 * name <名前>
 * saved <保存した時刻(ミリ秒)>
 * pose
 * <PoseCodec の文字列>
 * ```
 * 保存するのはポーズ(骨格・肋骨の置き方・体型)だけで、視点や選択中の部位は含めない。
 */
class PoseLibrary(
    private val store: PoseStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    /** 直前の list で読めなかった(壊れていた)ファイルの id。ログに残すため */
    var lastBroken: List<String> = emptyList()
        private set

    /** 新しい順の一覧。壊れたファイルは外す */
    fun list(): LibraryResult<List<SavedPoseSummary>> = io {
        val broken = ArrayList<String>()
        val items = store.list().mapNotNull { id ->
            val parsed = parse(store.read(id))
            if (parsed == null) broken += id
            parsed?.let { SavedPoseSummary(id, it.name, it.savedAt) }
        }
        lastBroken = broken
        items.sortedByDescending { it.savedAt }
    }

    fun save(name: String, skeleton: Skeleton): LibraryResult<SavedPoseSummary> = io {
        val clean = normalizeName(name).ifEmpty { suggestName() }
        val summary = SavedPoseSummary(newId(), clean, clock())
        store.write(summary.id, encode(summary, skeleton))
        summary
    }

    fun load(id: String): LibraryResult<Skeleton> {
        val text = when (val r = io { store.read(id) }) {
            is LibraryResult.Ok -> r.value
            is LibraryResult.Error -> return r
        }
        val parsed = parse(text) ?: return LibraryResult.Error("形式が違う: $id")
        return LibraryResult.Ok(parsed.skeleton)
    }

    fun delete(id: String): LibraryResult<Unit> = io { store.delete(id) }

    /** 名前を空にしたときの名前「ポーズ N」。N は保存済みの「ポーズ 数字」の最大 + 1 */
    fun suggestName(): String {
        val names = (list() as? LibraryResult.Ok)?.value?.map { it.name }.orEmpty()
        val max = names.mapNotNull { Regex("^ポーズ (\\d+)$").find(it)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        return "ポーズ ${max + 1}"
    }

    private class Parsed(val name: String, val savedAt: Long, val skeleton: Skeleton)

    private fun encode(s: SavedPoseSummary, skeleton: Skeleton) = buildString {
        appendLine(HEADER)
        appendLine("name ${s.name}")
        appendLine("saved ${s.savedAt}")
        appendLine("pose")
        append(PoseCodec.encode(skeleton))
    }

    private fun parse(text: String): Parsed? {
        val lines = text.lines()
        if (lines.size < 4 || lines[0] != HEADER || lines[3] != "pose") return null
        val name = lines[1].removePrefix("name ").takeIf { lines[1].startsWith("name ") } ?: return null
        val savedAt = lines[2].removePrefix("saved ").toLongOrNull()?.takeIf { lines[2].startsWith("saved ") } ?: return null
        val skeleton = (PoseCodec.decode(lines.drop(4).joinToString("\n")) as? PoseDecodeResult.Ok)?.skeleton ?: return null
        return Parsed(name, savedAt, skeleton)
    }

    private inline fun <T> io(block: () -> T): LibraryResult<T> = try {
        LibraryResult.Ok(block())
    } catch (e: IOException) {
        LibraryResult.Error(e.message ?: e.toString())
    }

    companion object {
        private const val HEADER = "dessin-saved 1"
        const val MAX_NAME_LENGTH = 40

        /** 前後の空白を取り、改行は空白にし、40文字までにする */
        fun normalizeName(name: String): String =
            name.replace(Regex("[\\r\\n]+"), " ").trim().take(MAX_NAME_LENGTH)
    }
}
