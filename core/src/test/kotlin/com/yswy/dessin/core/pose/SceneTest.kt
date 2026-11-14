package com.yswy.dessin.core.pose

import com.yswy.dessin.core.editor.EditorState
import com.yswy.dessin.core.editor.NaturalPose
import com.yswy.dessin.core.editor.SliderKind
import com.yswy.dessin.core.editor.applySlider
import com.yswy.dessin.core.editor.loadScene
import com.yswy.dessin.core.skeleton.HandShape
import com.yswy.dessin.core.skeleton.Skeleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** E13-04: 場面(複数の人形)の保存 */
class SceneTest {
    private class MemoryStore : PoseStore {
        val files = LinkedHashMap<String, String>()
        override fun list(): List<String> = files.keys.toList()
        override fun read(id: String): String = files[id] ?: throw IOException("無い: $id")
        override fun write(id: String, text: String) { files[id] = text }
        override fun delete(id: String) { files.remove(id) ?: throw IOException("無い: $id") }
    }

    private val scene = EditorState().select("clavL").select("upperArmL").select("forearmL").select("handL")
        .let { it.copy(skeleton = it.skeleton.withHandShape("handL", HandShape.FIST)) }
        // 身長のスライダーは胸を選んでいるときに出る(足した人形は胸を選んでいる)
        .addDoll().applySlider(SliderKind.HEIGHT, 120).select("neck")

    private fun <T> ok(r: LibraryResult<T>): T {
        assertTrue("成功するはず: $r", r is LibraryResult.Ok)
        return (r as LibraryResult.Ok).value
    }

    @Test
    fun 場面を文字列にして戻すと全員の位置_ポーズ_体型_手の形が戻る() {
        val text = SceneCodec.encode(scene.dolls)
        val back = SceneCodec.decode(text)!!
        assertEquals(scene.dolls.map { it.withAnchor(com.yswy.dessin.core.skeleton.Anchor.CHEST) }, back.map { NaturalPose.unplacedNatural(it) })
        assertEquals(1.2, back[1].body.height, 1e-9)
        assertEquals(HandShape.FIST, back[0].handShape("handL"))
    }

    @Test
    fun 壊れた場面は_null() {
        assertEquals(null, SceneCodec.decode("hello"))
        assertEquals(null, SceneCodec.decode("dessin-scene 1\n"))
        assertEquals(null, SceneCodec.decode("dessin-scene 1\ndoll\nbodytype height=abc\ndessin-pose 3\n"))
    }

    @Test
    fun 場面を保存して読み込む_名前の候補は場面N() {
        val lib = SceneLibrary(MemoryStore(), clock = { 1L }, newId = { "s1" })
        assertEquals("場面 1", lib.suggestName())
        val saved = ok(lib.save("", SavedItem.Scene(scene.dolls)))
        assertEquals("場面 1", saved.name)
        val loaded = ok(lib.load(saved.id)) as SavedItem.Scene
        assertEquals(2, loaded.dolls.size)
    }

    @Test
    fun 今までの1体のポーズのファイルも一覧に出て_ポーズとして読める() {
        val store = MemoryStore()
        val old = PoseLibrary(store, clock = { 1L }, newId = { "p1" })
        ok(old.save("走る", Skeleton.initial().place("neck")))
        val lib = SceneLibrary(store)
        assertEquals(listOf("走る"), ok(lib.list()).map { it.name })
        val item = ok(lib.load("p1"))
        assertTrue(item is SavedItem.Pose)
        assertTrue((item as SavedItem.Pose).skeleton.isPlaced("neck"))
    }

    @Test
    fun 場面を読み込むと全員を差し替え_体型も場面のもの_最初の人形を操作中にする() {
        val other = EditorState().addDoll().addDoll()
        val s = other.withCamera(com.yswy.dessin.core.camera.OrbitCamera()).loadScene(SceneCodec.decode(SceneCodec.encode(scene.dolls))!!, 1.0)
        assertEquals(2, s.dolls.size)
        assertEquals(0, s.activeDoll)
        assertEquals("rib", s.selected)
        assertEquals(1.2, s.dolls[1].body.height, 1e-9)
        assertEquals(scene.dolls[1].root.position, s.dolls[1].root.position)
        // 全体を表示し直す
        assertTrue(s.camera!!.target.x > s.dolls[0].root.position.x)
    }
}
