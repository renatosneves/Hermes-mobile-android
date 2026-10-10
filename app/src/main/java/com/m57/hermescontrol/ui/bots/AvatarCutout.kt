package com.m57.hermescontrol.ui.bots

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/**
 * Lifts a bot picture off a plain background so it can break out of its orb, GrokBot style.
 * Works on ARGB pixels: a picture whose four corners share one colour has that colour, where it
 * touches the edge, made transparent (a flood fill, so the same colour inside the figure stays).
 * Pictures that are already see-through, or have a busy background, are left as they are.
 */
object AvatarCutout {
    /** How close (per channel sum) a pixel must be to the background to be removed. */
    private const val TOLERANCE = 42

    /** Pixels this much further away fade out rather than stop hard, to soften the edge. */
    private const val FEATHER = 42

    /** Below this alpha a corner counts as already transparent. */
    private const val CLEAR_ALPHA = 24

    /** Whether all four corners are see-through: the picture can be drawn outside the orb. */
    fun hasClearCorners(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): Boolean = corners(width, height).all { alpha(pixels[it]) < CLEAR_ALPHA }

    /**
     * Makes the background around the figure transparent, in place. Returns false, changing
     * nothing, when there is no single plain background or removing it would leave almost nothing.
     */
    fun cut(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): Boolean {
        if (width < 8 || height < 8 || pixels.size < width * height) return false
        if (hasClearCorners(pixels, width, height)) return false
        val bg = pixels[0]
        if (corners(width, height).any { distance(pixels[it], bg) > TOLERANCE }) return false

        val out = pixels.copyOf()
        val seen = BooleanArray(width * height)
        val stack = IntArray(width * height)
        var top = 0

        fun push(i: Int) {
            if (!seen[i]) {
                seen[i] = true
                stack[top++] = i
            }
        }
        for (x in 0 until width) {
            push(x)
            push((height - 1) * width + x)
        }
        for (y in 0 until height) {
            push(y * width)
            push(y * width + width - 1)
        }
        var removed = 0
        while (top > 0) {
            val i = stack[--top]
            val d = distance(pixels[i], bg)
            if (d > TOLERANCE + FEATHER) continue
            if (d > TOLERANCE) {
                // Edge of the figure: fade, and go no further.
                val keep = (d - TOLERANCE).toFloat() / FEATHER
                out[i] = withAlpha(pixels[i], (alpha(pixels[i]) * keep).toInt())
                continue
            }
            out[i] = 0
            removed++
            val x = i % width
            val y = i / width
            if (x > 0) push(i - 1)
            if (x < width - 1) push(i + 1)
            if (y > 0) push(i - width)
            if (y < height - 1) push(i + width)
        }
        val total = width * height
        if (removed < total / 20 || removed > total * 9 / 10) return false
        out.copyInto(pixels)
        return true
    }

    private fun corners(
        width: Int,
        height: Int,
    ) = intArrayOf(0, width - 1, (height - 1) * width, height * width - 1)

    private fun alpha(c: Int) = (c ushr 24) and 0xFF

    private fun withAlpha(
        c: Int,
        a: Int,
    ) = (a.coerceIn(0, 255) shl 24) or (c and 0xFFFFFF)

    private fun distance(
        a: Int,
        b: Int,
    ): Int =
        kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
            kotlin.math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
            kotlin.math.abs((a and 0xFF) - (b and 0xFF))
}

/** Coil step applying [AvatarCutout] to a decoded bot picture. */
class AvatarCutoutTransformation : Transformation() {
    override val cacheKey: String = "avatar-cutout-1"

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        val width = input.width
        val height = input.height
        val pixels = IntArray(width * height)
        val source = if (input.config == Bitmap.Config.ARGB_8888) input else input.copy(Bitmap.Config.ARGB_8888, false)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        if (!AvatarCutout.cut(pixels, width, height)) return input
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
