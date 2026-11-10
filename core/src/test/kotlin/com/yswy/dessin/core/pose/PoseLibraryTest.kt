package com.yswy.dessin.core.pose

import com.yswy.dessin.core.math.Vec3
import com.yswy.dessin.core.skeleton.PartSpecs
import com.yswy.dessin.core.skeleton.Skeleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** 名前を付けて複数のポーズを保存する(E03-02) */
class PoseLibraryTest {
    /** メモリ上の置き場所。failWrite・failRead で失敗を起こせる */
    private class MemoryStore : PoseStore {
        val files = LinkedHashMap<String, String>()
        var failWrite = false
        var failRead = false
        override fun list(): List<String> = if (failRead) throw IOException("読めない") else files.keys.toList()
        override fun read(id: String): String = if (failRead) throw IOException("読めない") else files[id] ?: throw IOException("無い: $id")
        override fun write(id: String, text: String) { if (failWrite) throw IOException("書けない"); files[id] = text }
        override fun delete(id: String) { files.remove(id) ?: throw IOException("無い: $id") }
    }

    private var now = 1_000L
    private var nextId = 0
    private val store = MemoryStore()
    private val library = PoseLibrary(store, clock = { now }, newId = { "id${nextId++}" })

    private val posed = PartSpecs.all.fold(Skeleton.initial()) { s, p -> s.place(p.id) }
        .aim("upperArmL", Vec3(0.3, 0.2, 0.9).normalized())

    private fun <T> ok(r: LibraryResult<T>): T {
        assertTrue("成功するはず: $r", r is LibraryResult.Ok)
        return (r as LibraryResult.Ok).value
    }

    @Test
    fun 保存して読み込むと同じポーズ() {
        val saved = ok(library.save("走る", posed))
        assertEquals("走る", saved.name)
        assertEquals(posed, ok(library.load(saved.id)))
    }

    @Test
    fun 一覧は新しい順() {
        ok(library.save("A", posed))
        now = 3_000
        ok(library.save("B", posed))
        now = 2_000
        ok(library.save("C", posed))
        assertEquals(listOf("B", "C", "A"), ok(library.list()).map { it.name })
        assertEquals(listOf(3_000L, 2_000L, 1_000L), ok(library.list()).map { it.savedAt })
    }

    @Test
    fun 同じ名前でも別に保存する() {
        ok(library.save("A", posed))
        ok(library.save("A", Skeleton.initial()))
        assertEquals(2, ok(library.list()).size)
    }

    @Test
    fun 削除する() {
        val a = ok(library.save("A", posed))
        ok(library.save("B", posed))
        ok(library.delete(a.id))
        assertEquals(listOf("B"), ok(library.list()).map { it.name })
    }

    @Test
    fun 名前は前後の空白を取り_改行は空白にし_40文字までにする() {
        assertEquals("立つ ポーズ", ok(library.save("  立つ\nポーズ  ", posed)).name)
        assertEquals("あ".repeat(40), ok(library.save("あ".repeat(50), posed)).name)
    }

    @Test
    fun 名前が空なら番号を付ける() {
        assertEquals("ポーズ 1", library.suggestName())
        assertEquals("ポーズ 1", ok(library.save("   ", posed)).name)
        assertEquals("ポーズ 2", library.suggestName())
        ok(library.save("ポーズ 5", posed))
        assertEquals("ポーズ 6", library.suggestName())
    }

    @Test
    fun 壊れたファイルは一覧から外す() {
        ok(library.save("A", posed))
        store.files["broken"] = "hello"
        assertEquals(listOf("A"), ok(library.list()).map { it.name })
        assertEquals(listOf("broken"), library.lastBroken)
    }

    @Test
    fun 壊れたポーズを読み込もうとするとエラー() {
        store.files["broken"] = "dessin-saved 1\nname X\nsaved 1\npose\nhello"
        assertTrue(library.load("broken") is LibraryResult.Error)
    }

    @Test
    fun 書けない_読めないときはエラーで返す() {
        store.failWrite = true
        assertTrue(library.save("A", posed) is LibraryResult.Error)
        store.failWrite = false
        val a = ok(library.save("A", posed))
        store.failRead = true
        assertTrue(library.list() is LibraryResult.Error)
        assertTrue(library.load(a.id) is LibraryResult.Error)
        assertTrue(library.delete("nothing") is LibraryResult.Error)
    }

    @Test
    fun ファイルの形式() {
        val a = ok(library.save("走る ポーズ", posed))
        val text = store.files.getValue(a.id)
        val lines = text.lines()
        assertEquals("dessin-saved 1", lines[0])
        assertEquals("name 走る ポーズ", lines[1])
        assertEquals("saved 1000", lines[2])
        assertEquals("pose", lines[3])
        assertEquals(PoseCodec.encode(posed), lines.drop(4).joinToString("\n"))
    }
}
