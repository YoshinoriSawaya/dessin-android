package com.yswy.dessin.core.pose

import com.yswy.dessin.core.skeleton.BodyParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** E07-04: 保存した体型から既定の体型を選ぶ */
class DefaultBodyTest {
    private class MemoryStore : PoseStore {
        val files = LinkedHashMap<String, String>()
        var failRead = false
        override fun list(): List<String> = files.keys.toList()
        override fun read(id: String): String = if (failRead) throw IOException("読めない") else files[id] ?: throw IOException("無い: $id")
        override fun write(id: String, text: String) { files[id] = text }
        override fun delete(id: String) { files.remove(id) ?: throw IOException("無い: $id") }
    }

    private var nextId = 0
    private val bodyStore = MemoryStore()
    private val settings = MemoryStore()
    private val library = BodyLibrary(bodyStore, clock = { 1L }, newId = { "id${nextId++}" })
    private val defaults = DefaultBody(settings, library)

    private val slim = BodyParams(shoulderWidth = 0.8, height = 1.1)

    private fun <T> ok(r: LibraryResult<T>): T {
        assertTrue("成功するはず: $r", r is LibraryResult.Ok)
        return (r as LibraryResult.Ok).value
    }

    @Test
    fun 選んでいなければ既定は無く_体型は固定の値() {
        assertNull(ok(defaults.id()))
        assertEquals(BodyParams(), ok(defaults.body()))
    }

    @Test
    fun 既定にした体型を返す() {
        val saved = ok(library.save("細身", slim))
        ok(defaults.set(saved.id))
        assertEquals(saved.id, ok(defaults.id()))
        assertEquals(slim, ok(defaults.body()))
    }

    @Test
    fun 既定を選び直せる() {
        val a = ok(library.save("A", slim))
        val b = ok(library.save("B", BodyParams(headSize = 1.2)))
        ok(defaults.set(a.id))
        ok(defaults.set(b.id))
        assertEquals(BodyParams(headSize = 1.2), ok(defaults.body()))
    }

    @Test
    fun 既定を外すと固定の値に戻る() {
        val saved = ok(library.save("細身", slim))
        ok(defaults.set(saved.id))
        ok(defaults.clear())
        assertNull(ok(defaults.id()))
        assertEquals(BodyParams(), ok(defaults.body()))
        // 選んでいないときに外しても失敗しない
        ok(defaults.clear())
    }

    @Test
    fun 既定にした体型を消すと既定も外れる() {
        val saved = ok(library.save("細身", slim))
        val other = ok(library.save("ほか", BodyParams()))
        ok(defaults.set(saved.id))
        ok(defaults.forget(other.id))
        assertEquals(saved.id, ok(defaults.id()))
        ok(library.delete(saved.id))
        ok(defaults.forget(saved.id))
        assertNull(ok(defaults.id()))
    }

    @Test
    fun 既定の体型のファイルが無ければエラー() {
        // 既定を覚えたまま、体型のファイルだけが無くなった
        ok(defaults.set("gone"))
        assertTrue(defaults.body() is LibraryResult.Error)
    }

    @Test
    fun 既定を読めなければエラー() {
        ok(defaults.set(ok(library.save("細身", slim)).id))
        settings.failRead = true
        assertTrue(defaults.id() is LibraryResult.Error)
        assertTrue(defaults.body() is LibraryResult.Error)
    }
}
