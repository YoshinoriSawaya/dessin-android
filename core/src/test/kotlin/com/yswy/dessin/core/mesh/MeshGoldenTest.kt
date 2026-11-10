package com.yswy.dessin.core.mesh

import com.yswy.dessin.core.skeleton.BodyParams
import com.yswy.dessin.core.skeleton.PartSpecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

/**
 * 部位の形・置き方・可動域の表示が Web版(three.js r128)と一致することを確かめる(E02-02)。
 * データは tools/golden の generate.mjs が作る。線(正中線の管)と輪郭は Web版と作り方が違うので対象外。
 */
class MeshGoldenTest {
    private val tol = 1e-6

    @Test
    fun Web版と同じ形と置き方になる() {
        val text = javaClass.getResource("/golden/mesh-golden.txt")?.readText()
            ?: error("golden/mesh-golden.txt が無い。tools/golden で npm run generate を実行する")
        val errors = mutableListOf<String>()
        var where = ""
        var body = BodyParams()
        var mesh: PartMesh? = null
        var guide: GuideMesh? = null
        var meshes = 0
        var guides = 0
        val seen = mutableMapOf<String, Int>()

        fun check(label: String, expected: Double, actual: Double) {
            if (abs(expected - actual) > tol && errors.size < 20) errors += "$where: $label 期待 $expected 実際 $actual"
        }

        fun checkArray(label: String, t: List<String>, actual: FloatArray) {
            val n = t[1].toInt()
            if (n != actual.size) {
                if (errors.size < 20) errors += "$where: $label の数 期待 $n 実際 ${actual.size}"
                return
            }
            for (i in 0 until n) check("$label[$i]", t[i + 2].toDouble(), actual[i].toDouble())
        }

        fun checkIndices(label: String, t: List<String>, actual: IntArray) {
            val expected = t.drop(2).map { it.toInt() }
            if (expected != actual.toList() && errors.size < 20) errors += "$where: $label が違う(期待 ${expected.size} 個、実際 ${actual.size} 個)"
        }

        fun checkPlacement(t: List<String>) {
            val id = t[1]
            val i = t[2].toInt()
            where = "${t[0]} $id $i"
            val p = PartVisuals.placement(id, i, body)
            check("position.x", t[3].toDouble(), p.position.x)
            check("position.y", t[4].toDouble(), p.position.y)
            check("position.z", t[5].toDouble(), p.position.z)
            val q = p.rotation
            val sign = if (t[6].toDouble() * q.x + t[7].toDouble() * q.y + t[8].toDouble() * q.z + t[9].toDouble() * q.w < 0) -1.0 else 1.0
            check("rotation.x", t[6].toDouble(), sign * q.x)
            check("rotation.y", t[7].toDouble(), sign * q.y)
            check("rotation.z", t[8].toDouble(), sign * q.z)
            check("rotation.w", t[9].toDouble(), sign * q.w)
            check("scale.x", t[10].toDouble(), p.scale.x)
            check("scale.y", t[11].toDouble(), p.scale.y)
            check("scale.z", t[12].toDouble(), p.scale.z)
        }

        text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val t = line.split(' ')
            when (t[0]) {
                "mesh" -> {
                    meshes++
                    val id = t[1]
                    val i = t[2].toInt()
                    seen[id] = (seen[id] ?: 0) + 1
                    mesh = PartVisuals.meshes(id).getOrNull(i)
                    if (mesh == null) errors += "mesh $id $i が無い" else checkPlacement(t)
                }
                "kind" -> mesh?.let { if (it.kind.name.lowercase() != t[1] && errors.size < 20) errors += "$where: 種類 期待 ${t[1]} 実際 ${it.kind}" }
                "positions" -> mesh?.let { checkArray("positions", t, it.mesh.positions) }
                "normals" -> mesh?.let { checkArray("normals", t, it.mesh.normals) }
                "indices" -> mesh?.let { checkIndices("indices", t, it.mesh.indices) }
                "body" -> body = BodyParams(shoulderWidth = t[1].toDouble(), pelvisWidth = t[2].toDouble(), hipInset = t[3].toDouble())
                "scaled" -> checkPlacement(t)
                "guide" -> {
                    guides++
                    where = line
                    guide = guideMesh(PartSpecs[t[1]])
                }
                "area" -> guide?.let { checkArray("area", t, it.area.positions) }
                "areaIndices" -> guide?.let { checkIndices("areaIndices", t, it.area.indices) }
                "outline" -> guide?.let { checkArray("outline", t, it.outline) }
                else -> error("知らない行: $line")
            }
        }

        if (errors.isNotEmpty()) fail("Web版と一致しない(最初の${errors.size}件):\n" + errors.joinToString("\n"))
        // Web版のメッシュの数と、こちらのメッシュの数が同じ
        PartSpecs.all.forEach { assertEquals(it.id, seen[it.id] ?: 0, PartVisuals.meshes(it.id).size) }
        assertTrue("メッシュ $meshes 可動域 $guides", meshes >= 30 && guides == 19)
    }
}
