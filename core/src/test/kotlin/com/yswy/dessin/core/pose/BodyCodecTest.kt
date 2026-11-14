package com.yswy.dessin.core.pose

import com.yswy.dessin.core.skeleton.BodyParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** E07-02: 体型と文字列の変換 */
class BodyCodecTest {
    private val body = BodyParams(
        headSize = 1.2, shoulderWidth = 0.9, pelvisWidth = 1.1, hipInset = 0.03,
        neckLength = 1.4, neckOffset = -0.05, upperArmLength = 1.1, forearmLength = 0.9,
        thighLength = 1.2, shinLength = 0.8, height = 1.15, kneeRoll = 0.045,
    )

    @Test
    fun 書いて読むと同じ体型() {
        assertEquals(body, BodyCodec.decodeLine(BodyCodec.encodeLine(body)))
    }

    @Test
    fun 書かれていない項目は既定の値_知らない名前は読み飛ばす() {
        assertEquals(BodyParams(neckLength = 1.5), BodyCodec.decodeLine("neckLength=1.5 future=3"))
        assertEquals(BodyParams(), BodyCodec.decodeLine(""))
    }

    @Test
    fun 値は範囲に収める() {
        val b = BodyCodec.decodeLine("height=5 kneeRoll=-1 neckOffset=1")!!
        assertEquals(1.3, b.height, 0.0)
        assertEquals(0.0, b.kneeRoll, 0.0)
        assertEquals(0.02, b.neckOffset, 0.0)
    }

    @Test
    fun 形の誤りは_null() {
        assertNull(BodyCodec.decodeLine("height"))
        assertNull(BodyCodec.decodeLine("height=abc"))
        assertNull(BodyCodec.decodeLine("=1.0"))
        assertNull(BodyCodec.decodeLine("height=NaN"))
    }
}
