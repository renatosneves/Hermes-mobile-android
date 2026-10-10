package com.m57.hermescontrol.ui.bots

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarCutoutTest {
    private val black = 0xFF000000.toInt()
    private val orange = 0xFFFF8800.toInt()

    /** A [size]² black square with an orange block in the middle and one black pixel inside it. */
    private fun picture(size: Int = 20): IntArray {
        val px = IntArray(size * size) { black }
        for (y in 5 until 15) for (x in 5 until 15) px[y * size + x] = orange
        px[10 * size + 10] = black
        return px
    }

    @Test
    fun `a plain background is removed and the figure kept`() {
        val px = picture()
        assertTrue(AvatarCutout.cut(px, 20, 20))
        assertEquals(0, px[0])
        assertEquals(orange, px[7 * 20 + 7])
        // Black inside the figure does not touch the edge, so it stays.
        assertEquals(black, px[10 * 20 + 10])
        assertTrue(AvatarCutout.hasClearCorners(px, 20, 20))
    }

    @Test
    fun `a busy background is left alone`() {
        val px = picture()
        px[19] = orange
        assertFalse(AvatarCutout.cut(px, 20, 20))
        assertEquals(black, px[0])
    }

    @Test
    fun `a picture that is all background is left alone`() {
        val px = IntArray(400) { black }
        assertFalse(AvatarCutout.cut(px, 20, 20))
    }
}
