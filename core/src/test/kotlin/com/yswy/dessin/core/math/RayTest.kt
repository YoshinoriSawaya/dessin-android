package com.yswy.dessin.core.math

import org.junit.Test

class RayTest {
    @Test
    fun レイ上の点() {
        val ray = Ray(Vec3(1.0, 2.0, 3.0), Vec3(0.0, 0.0, -1.0))
        assertVec(Vec3(1.0, 2.0, 3.0), ray.at(0.0))
        assertVec(Vec3(1.0, 2.0, 0.5), ray.at(2.5))
    }
}
