package com.github.oumaimazerouali.spongebobtheme.progress

import java.awt.image.BufferedImage
import kotlin.math.abs

/**
 * Turns a "ripped" sprite sheet (solid or transparent background, frames at uneven spacing, several rows)
 * into a clean strip of equally sized, transparent frames.
 */
object SpriteSlicer {
    class Result(val strip: BufferedImage, val frames: Int, val frameW: Int, val frameH: Int)

    private const val ALPHA_MIN = 16
    private const val COLOR_TOLERANCE = 30

    private class Box(val x0: Int, val x1: Int, val top: Int, val bottom: Int)

    private class Analysis(val w: Int, val px: IntArray, val solid: BooleanArray, val rows: List<List<Box>>)

    /**
     * Small sheets whose width is an exact multiple of their height are treated as a plain grid
     * of square pixel-art frames (they often touch each other, so gap detection would merge them).
     */
    fun looksLikeGrid(img: BufferedImage): Boolean =
        img.height <= 64 && img.width % img.height == 0 && img.width / img.height >= 2

    /** Number of frames found in each row of the sheet, top to bottom. */
    fun rowFrameCounts(img: BufferedImage): List<Int> = analyse(img)?.rows?.map { it.size } ?: emptyList()

    /** @param row 1-based row of the sheet. */
    fun slice(img: BufferedImage, row: Int): Result? {
        val a = analyse(img) ?: return null
        if (a.rows.isEmpty()) return null
        val boxes = a.rows[(row - 1).coerceIn(0, a.rows.lastIndex)]
        if (boxes.isEmpty()) return null

        // Keep each frame's vertical offset from the sheet, so the bounce of the run cycle survives.
        val minTop = boxes.minOf { it.top }
        val frameH = boxes.maxOf { it.bottom } - minTop + 1
        val frameW = boxes.maxOf { it.x1 - it.x0 + 1 }
        val strip = BufferedImage(frameW * boxes.size, frameH, BufferedImage.TYPE_INT_ARGB)
        boxes.forEachIndexed { i, b ->
            val dx = i * frameW + (frameW - (b.x1 - b.x0 + 1)) / 2
            for (y in b.top..b.bottom) for (x in b.x0..b.x1) {
                val idx = y * a.w + x
                if (a.solid[idx]) strip.setRGB(dx + x - b.x0, y - minTop, a.px[idx])
            }
        }
        return Result(strip, boxes.size, frameW, frameH)
    }

    private fun analyse(src: BufferedImage): Analysis? {
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

        val rows = runs(h) { y -> (0 until w).any { solid[y * w + it] } }
            .map { (y0, y1) -> framesInBand(solid, w, y0, y1) }
            .filter { it.isNotEmpty() }
        // Specks or a lone label line can form a "row" of their own; keep rows with real sprites.
        val tallest = rows.maxOfOrNull { r -> r.maxOf { it.bottom - it.top + 1 } } ?: 0
        return Analysis(w, px, solid, rows.filter { r -> r.maxOf { it.bottom - it.top + 1 } * 3 > tallest })
    }

    private fun framesInBand(solid: BooleanArray, w: Int, y0: Int, y1: Int): List<Box> {
        var cols = runs(w) { x -> (y0..y1).any { solid[it * w + x] } }
        if (cols.isEmpty()) return emptyList()
        // Drop labels and specks: anything much narrower than a typical frame.
        val median = cols.map { it.second - it.first + 1 }.sorted()[cols.size / 2]
        cols = cols.filter { it.second - it.first + 1 >= median / 2 }
        val candidates = cols.mapNotNull { (x0, x1) ->
            val ys = (y0..y1).filter { y -> (x0..x1).any { solid[y * w + it] } }
            if (ys.isEmpty()) null else Box(x0, x1, ys.first(), ys.last())
        }
        if (candidates.isEmpty()) return emptyList()
        // Labels like "Running" are much lower than the sprites next to them.
        val tallest = candidates.maxOf { it.bottom - it.top + 1 }
        return candidates.filter { (it.bottom - it.top + 1) * 2 > tallest }
    }

    /** Index ranges where [filled] is true, bridging gaps of up to 2 px. */
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
