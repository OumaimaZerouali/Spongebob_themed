package com.github.oumaimazerouali.spongebobtheme.progress

import java.awt.image.BufferedImage
import kotlin.math.abs

/**
 * Turns a "ripped" sprite sheet (solid background colour, frames at uneven spacing, several rows)
 * into a clean strip of equally sized, transparent frames.
 */
object SpriteSlicer {
    class Result(val strip: BufferedImage, val frames: Int, val frameW: Int, val frameH: Int)

    private const val ALPHA_MIN = 16
    private const val COLOR_TOLERANCE = 30

    /** @param row 1-based row of the sheet to use; 0 = treat the whole image as one row. */
    fun slice(src: BufferedImage, row: Int): Result? {
        val w = src.width
        val h = src.height
        if (w < 2 || h < 2) return null
        val px = src.getRGB(0, 0, w, h, null, 0, w)

        // Background = top-left pixel, unless that one is already transparent.
        val bg = px[0]
        val keyed = (bg ushr 24) >= ALPHA_MIN
        fun isBg(p: Int): Boolean {
            if ((p ushr 24) < ALPHA_MIN) return true
            if (!keyed) return false
            val d = abs(((p shr 16) and 0xFF) - ((bg shr 16) and 0xFF)) +
                abs(((p shr 8) and 0xFF) - ((bg shr 8) and 0xFF)) +
                abs((p and 0xFF) - (bg and 0xFF))
            return d <= COLOR_TOLERANCE
        }
        val solid = BooleanArray(w * h) { !isBg(px[it]) }

        // Rows of the sheet = horizontal bands separated by empty lines.
        val rowBands = runs(h) { y -> (0 until w).any { solid[y * w + it] } }
        val (y0, y1) = when {
            row <= 0 || rowBands.isEmpty() -> 0 to h - 1
            else -> rowBands[(row - 1).coerceAtMost(rowBands.lastIndex)]
        }

        // Frames = vertical runs of filled columns inside that band.
        var cols = runs(w) { x -> (y0..y1).any { solid[it * w + x] } }
        if (cols.isEmpty()) return null
        // Drop labels and specks: anything much narrower than a typical frame.
        val median = cols.map { it.second - it.first + 1 }.sorted()[cols.size / 2]
        cols = cols.filter { it.second - it.first + 1 >= median / 2 }

        class Box(val x0: Int, val x1: Int, val top: Int, val bottom: Int)
        val candidates = cols.mapNotNull { (x0, x1) ->
            val ys = (y0..y1).filter { y -> (x0..x1).any { solid[y * w + it] } }
            if (ys.isEmpty()) null else Box(x0, x1, ys.first(), ys.last())
        }
        if (candidates.isEmpty()) return null
        // Labels like "Running" are much lower than the sprites next to them.
        val tallest = candidates.maxOf { it.bottom - it.top + 1 }
        val boxes = candidates.filter { (it.bottom - it.top + 1) * 2 > tallest }

        // Keep each frame's vertical offset from the sheet, so the bounce of the run cycle survives.
        val minTop = boxes.minOf { it.top }
        val frameH = boxes.maxOf { it.bottom } - minTop + 1
        val frameW = boxes.maxOf { it.x1 - it.x0 + 1 }
        val strip = BufferedImage(frameW * boxes.size, frameH, BufferedImage.TYPE_INT_ARGB)
        boxes.forEachIndexed { i, b ->
            val dx = i * frameW + (frameW - (b.x1 - b.x0 + 1)) / 2
            for (y in b.top..b.bottom) for (x in b.x0..b.x1) {
                if (solid[y * w + x]) strip.setRGB(dx + x - b.x0, y - minTop, px[y * w + x])
            }
        }
        return Result(strip, boxes.size, frameW, frameH)
    }

    /** Index ranges where [filled] is true, bridging single-pixel gaps. */
    private fun runs(size: Int, filled: (Int) -> Boolean): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var start = -1
        var lastFilled = -10
        for (i in 0 until size) {
            if (filled(i)) {
                if (start < 0 || i - lastFilled > 2) {
                    if (start >= 0) out += start to lastFilled
                    start = i
                }
                lastFilled = i
            }
        }
        if (start >= 0) out += start to lastFilled
        return out
    }
}
