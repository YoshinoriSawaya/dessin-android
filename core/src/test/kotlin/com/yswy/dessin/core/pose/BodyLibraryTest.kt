package com.yswy.dessin.core.pose

import com.yswy.dessin.core.skeleton.BodyParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** E07-03: 名前を付けて複数の体型を保存する(ポーズとは別) */
class BodyLibraryTest {
    private class MemoryStore : PoseStore {
        val files = LinkedHashMap<String, String>()
        var failWrite = false
        override fun list(): List<String> = files.keys.toList()
        override fun read(id: String): String = files[id] ?: throw IOException("無い: $id")
        override fun write(id: String, text: String) { if (failWrite) throw IOException("書けない"); files[id] = text }
        override fun delete(id: String) { files.remove(id) ?: throw IOException("無い: $id") }
    }

    private var now = 1_000L
    private var nextId = 0
    private val store = MemoryStore()
    private val library = BodyLibrary(store, clock = { now }, newId = { "id${nextId++}" })

    private val body = BodyParams(headSize = 1.2, neckLength = 1.4, upperArmLength = 1.1, height = 0.9, kneeRoll = 0.04)

    private fun <T> ok(r: LibraryResult<T>): T {
        assertTrue("成功するはず: $r", r is LibraryResult.Ok)
        return (r as LibraryResult.Ok).value
    }

    @Test
    fun 保存して読み込むと同じ体型() {
        val saved = ok(library.save("細身", body))
        assertEquals("細身", saved.name)
        assertEquals(body, ok(library.load(saved.id)))
    }

    @Test
    fun 一覧は新しい順() {
        ok(library.save("A", body))
        now = 2_000L
        ok(library.save("B", BodyParams()))
        assertEquals(listOf("B", "A"), ok(library.list()).map { it.name })
    }

    @Test
    fun 名前が空なら体型_N() {
        assertEquals("体型 1", ok(library.save("  ", body)).name)
        assertEquals("体型 2", library.suggestName())
    }

    @Test
    fun ポーズのファイルは体型として読めない() {
        // 置き場所は分けるが、取り違えても体型としては読まない
        store.files["p"] = "dessin-saved 1\nname x\nsaved 1\npose\n${PoseCodec.encode(com.yswy.dessin.core.skeleton.Skeleton.initial())}"
        assertTrue(library.load("p") is LibraryResult.Error)
        assertEquals(emptyList<SavedPoseSummary>(), ok(library.list()))
        assertEquals(listOf("p"), library.lastBroken)
    }

    @Test
    fun 書き込めなければエラー() {
        store.failWrite = true
        assertTrue(library.save("A", body) is LibraryResult.Error)
    }
}
