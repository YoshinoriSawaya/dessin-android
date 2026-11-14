package com.yswy.dessin.core.camera

import com.yswy.dessin.core.math.Vec3
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

/**
 * Web版(three.js r128 の PerspectiveCamera・Raycaster)と同じ視点・行列・レイになることを確かめる(E02-01)。
 * データは固定(v1 のときに tools/golden の generate.mjs で作った。道具は E04-04 で消し、Git の履歴に残る)。
 */
class CameraGoldenTest {
    private val tol = 1e-6

    @Test
    fun Web版と同じ視点と行列とレイになる() {
        val text = javaClass.getResource("/golden/camera-golden.txt")?.readText()
            ?: error("golden/camera-golden.txt が無い")
        val errors = mutableListOf<String>()
        var cam = OrbitCamera()
        var width = 0.0
        var height = 0.0
        var where = ""
        var cameras = 0
        var rays = 0

        fun check(label: String, expected: Double, actual: Double) {
            if (abs(expected - actual) > tol && errors.size < 20) errors += "$where: $label 期待 $expected 実際 $actual"
        }

        text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val t = line.split(' ')
            fun d(i: Int) = t[i].toDouble()
            when (t[0]) {
                "camera" -> {
                    cameras++
                    where = line
                    cam = OrbitCamera(target = Vec3(d(1), d(2), d(3)), yaw = d(4), pitch = d(5), distance = d(6))
                    width = d(7)
                    height = d(8)
                }
                "position" -> {
                    check("position.x", d(1), cam.position.x)
                    check("position.y", d(2), cam.position.y)
                    check("position.z", d(3), cam.position.z)
                }
                "rotation" -> {
                    val q = cam.rotation
                    val sign = if (d(1) * q.x + d(2) * q.y + d(3) * q.z + d(4) * q.w < 0) -1.0 else 1.0
                    check("rotation.x", d(1), sign * q.x)
                    check("rotation.y", d(2), sign * q.y)
                    check("rotation.z", d(3), sign * q.z)
                    check("rotation.w", d(4), sign * q.w)
                }
                "projection" -> {
                    val m = cam.projectionMatrix(width / height).elements
                    for (i in 0 until 16) check("projection[$i]", d(i + 1), m[i])
                }
                "view" -> {
                    val m = cam.viewMatrix().elements
                    for (i in 0 until 16) check("view[$i]", d(i + 1), m[i])
                }
                "ray" -> {
                    rays++
                    val ray = cam.ray(d(1), d(2), width, height)
                    check("ray.origin.x", d(3), ray.origin.x)
                    check("ray.origin.y", d(4), ray.origin.y)
                    check("ray.origin.z", d(5), ray.origin.z)
                    check("ray.direction.x", d(6), ray.direction.x)
                    check("ray.direction.y", d(7), ray.direction.y)
                    check("ray.direction.z", d(8), ray.direction.z)
                }
                "delta" -> {
                    val out = cam.screenDelta(Vec3(d(1), d(2), d(3)), d(4), d(5), height)
                    check("delta.x", d(6), out.x)
                    check("delta.y", d(7), out.y)
                    check("delta.z", d(8), out.z)
                }
                else -> error("知らない行: $line")
            }
        }

        if (errors.isNotEmpty()) fail("Web版と一致しない(最初の${errors.size}件):\n" + errors.joinToString("\n"))
        assertTrue("視点 $cameras レイ $rays", cameras >= 30 && rays >= 120)
    }
}
