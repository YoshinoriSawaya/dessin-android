package com.yswy.dessin.core.pose

import com.yswy.dessin.core.skeleton.BodyParams
import com.yswy.dessin.core.skeleton.Skeleton
import java.io.IOException

/** 保存したポーズ・体型の置き場所。1件を1つの文字列として id で読み書きする。失敗したら IOException */
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

/** 一覧に出す1件(ポーズ・体型で共通)。savedAt は保存した時刻(エポックからのミリ秒) */
data class SavedPoseSummary(val id: String, val name: String, val savedAt: Long)

/**
 * 名前を付けて保存したものの一覧(E03-02 のポーズ、E07-03 の体型で共通)。1件のファイルの形式:
 * ```
 * dessin-saved 1
 * name <名前>
 * saved <保存した時刻(ミリ秒)>
 * <kind>          ← pose か body
 * <中身の文字列>
 * ```
 * 中身が kind と違うファイル(ポーズの置き場所に体型など)は、壊れたファイルと同じく読めないものとして扱う。
 */
open class NamedLibrary<T>(
    private val store: PoseStore,
    private val kind: String,
    private val namePrefix: String,
    private val encodeItem: (T) -> String,
    private val decodeItem: (String) -> T?,
    private val clock: () -> Long,
    private val newId: () -> String,
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

    fun save(name: String, item: T): LibraryResult<SavedPoseSummary> = io {
        val clean = normalizeName(name).ifEmpty { suggestName() }
        val summary = SavedPoseSummary(newId(), clean, clock())
        store.write(summary.id, encode(summary, item))
        summary
    }

    fun load(id: String): LibraryResult<T> {
        val text = when (val r = io { store.read(id) }) {
            is LibraryResult.Ok -> r.value
            is LibraryResult.Error -> return r
        }
        val parsed = parse(text) ?: return LibraryResult.Error("形式が違う: $id")
        return LibraryResult.Ok(parsed.item)
    }

    fun delete(id: String): LibraryResult<Unit> = io { store.delete(id) }

    /** 名前を空にしたときの名前「<namePrefix> N」。N は保存済みの「<namePrefix> 数字」の最大 + 1 */
    fun suggestName(): String {
        val names = (list() as? LibraryResult.Ok)?.value?.map { it.name }.orEmpty()
        val pattern = Regex("^${Regex.escape(namePrefix)} (\\d+)$")
        val max = names.mapNotNull { pattern.find(it)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        return "$namePrefix ${max + 1}"
    }

    private inner class Parsed(val name: String, val savedAt: Long, val item: T)

    private fun encode(s: SavedPoseSummary, item: T) = buildString {
        appendLine(HEADER)
        appendLine("name ${s.name}")
        appendLine("saved ${s.savedAt}")
        appendLine(kind)
        append(encodeItem(item))
    }

    private fun parse(text: String): Parsed? {
        val lines = text.lines()
        if (lines.size < 4 || lines[0] != HEADER || lines[3] != kind) return null
        val name = lines[1].removePrefix("name ").takeIf { lines[1].startsWith("name ") } ?: return null
        val savedAt = lines[2].removePrefix("saved ").toLongOrNull()?.takeIf { lines[2].startsWith("saved ") } ?: return null
        val item = decodeItem(lines.drop(4).joinToString("\n")) ?: return null
        return Parsed(name, savedAt, item)
    }

    private inline fun <R> io(block: () -> R): LibraryResult<R> = try {
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

/**
 * 名前を付けて保存したポーズの一覧(E03-02)。中身は PoseCodec の文字列。
 * 体型はポーズに含めない(E07-03。古いファイルの体型は読み込む側で使わない)。視点や選択中の部位も含めない。
 */
class PoseLibrary(
    store: PoseStore,
    clock: () -> Long = System::currentTimeMillis,
    newId: () -> String = { java.util.UUID.randomUUID().toString() },
) : NamedLibrary<Skeleton>(
    store, "pose", "ポーズ", PoseCodec::encode,
    { (PoseCodec.decode(it) as? PoseDecodeResult.Ok)?.skeleton },
    clock, newId,
) {
    companion object {
        const val MAX_NAME_LENGTH = NamedLibrary.MAX_NAME_LENGTH
        fun normalizeName(name: String): String = NamedLibrary.normalizeName(name)
    }
}

/** 名前を付けて保存した体型の一覧(E07-03)。中身は BodyCodec の1行 */
class BodyLibrary(
    store: PoseStore,
    clock: () -> Long = System::currentTimeMillis,
    newId: () -> String = { java.util.UUID.randomUUID().toString() },
) : NamedLibrary<BodyParams>(
    store, "body", "体型", { BodyCodec.encodeLine(it) + "\n" },
    { BodyCodec.decodeLine(it) },
    clock, newId,
)
