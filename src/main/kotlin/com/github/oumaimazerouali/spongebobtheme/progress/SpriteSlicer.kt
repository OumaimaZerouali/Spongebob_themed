package com.github.oumaimazerouali.spongebobtheme.progress

import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Reads a "found" sprite sheet: a solid or transparent background, sprites at uneven spacing,
 * text labels above groups ("IDLE", "WALK / RUN", …) and small emote marks (♥, ?) above sprites.
 *
 * Result: animation groups in reading order (top to bottom, left to right). Each group is a clean strip of
 * equally sized frames with a transparent background.
 *
 * How it works:
 *  1. Mark every pixel that differs from the background (top-left pixel).
 *  2. Glue nearby pixels into blobs (connected components, bridging gaps of a few px).
 *  3. Tall blobs are sprites. Short blobs are text or marks: wide lines of text are labels,
 *     small isolated ones directly above a sprite are glued onto that sprite.
 *  4. Sprites are grouped into rows; each label above a row starts a new group.
 *     Without labels, a big horizontal gap starts a new group.
 */
object SpriteSlicer {

    class Box(var x0: Int, var x1: Int, var y0: Int, var y1: Int) {
        val w get() = x1 - x0 + 1
        val h get() = y1 - y0 + 1
        val cx get() = (x0 + x1) / 2.0
    }

    class Layout(val groups: List<Anim>, val labelled: Boolean)

    private const val ALPHA_MIN = 16
    private const val COLOR_TOLERANCE = 40
    private const val BRIDGE = 4 // px gap still counted as the same blob
    private const val MIN_AREA = 20

    /**
     * Small sheets whose width is an exact multiple of their height are treated as a plain grid
     * of square pixel-art frames (they often touch each other, so blob detection would merge them).
     */
    fun looksLikeGrid(img: BufferedImage): Boolean =
        img.height <= 64 && img.width % img.height == 0 && img.width / img.height >= 2

    fun analyse(img: BufferedImage): Layout? {
        val w = img.width
        val h = img.height
        if (w < 2 || h < 2) return null
        val px = img.getRGB(0, 0, w, h, null, 0, w)
        val solid = solidMask(px)
        val blobs = blobs(solid, w, h)
        if (blobs.isEmpty()) return null

        // 3. Sprites vs text: compare with the tall end of the height distribution.
        val heights = blobs.map { it.h }.sorted()
        val tall = heights[((heights.size - 1) * 0.9).toInt()]
        val sprites = blobs.filter { it.h * 2 >= tall * 1.2 }.toMutableList()
        val small = blobs.filter { it.h * 2 < tall * 1.2 }

        val (labels, marks) = splitText(small)
        for (m in marks) {
            val target = sprites.firstOrNull { s ->
                val margin = 0.15 * s.w
                m.cx >= s.x0 + margin && m.cx <= s.x1 - margin && m.w <= s.w / 2 &&
                    s.y0 - m.y1 in 0..(s.h / 4)
            } ?: continue
            target.x0 = min(target.x0, m.x0); target.x1 = max(target.x1, m.x1); target.y0 = min(target.y0, m.y0)
        }

        // 4. Rows of sprites, then groups inside each row.
        val rows = mutableListOf<MutableList<Box>>()
        for (b in sprites.sortedBy { it.y1 }) {
            val row = rows.firstOrNull { r ->
                val top = r.minOf { it.y0 }
                val bottom = r.maxOf { it.y1 }
                min(bottom, b.y1) - max(top, b.y0) >= 0.3 * min(b.h, bottom - top + 1)
            }
            if (row != null) row += b else rows += mutableListOf(b)
        }
        val groups = mutableListOf<List<Box>>()
        var labelled = false
        for (row in rows.filter { it.size >= 2 }.sortedBy { r -> r.minOf { it.y0 } }) {
            row.sortBy { it.x0 }
            val medianTop = row.map { it.y0 }.sorted()[row.size / 2]
            val starts = labels.filter { it.y1 < medianTop && it.y1 >= medianTop - 70 }.map { it.x0 }.sorted()
            if (starts.isNotEmpty()) {
                labelled = true
                val byLabel = starts.map { mutableListOf<Box>() }
                for (b in row) byLabel[starts.indexOfLast { b.cx >= it - 10 }.coerceAtLeast(0)] += b
                groups += byLabel.filter { it.isNotEmpty() }
            } else {
                val gaps = row.zipWithNext { a, b -> b.x0 - a.x1 }
                val medianGap = gaps.sorted().getOrElse(gaps.size / 2) { 0 }.coerceAtLeast(1)
                var current = mutableListOf(row.first())
                groups += current
                for ((i, gap) in gaps.withIndex()) {
                    if (gap > 1.8 * medianGap) {
                        current = mutableListOf()
                        groups += current
                    }
                    current += row[i + 1]
                }
            }
        }
        if (groups.isEmpty()) return null
        return Layout(groups.map { strip(it, px, solid, w) }, labelled)
    }

    private fun solidMask(px: IntArray): BooleanArray {
        val bg = px[0]
        val keyed = (bg ushr 24) >= ALPHA_MIN
        return BooleanArray(px.size) { i ->
            val p = px[i]
            when {
                (p ushr 24) < ALPHA_MIN -> false
                !keyed -> true
                else -> abs(((p shr 16) and 0xFF) - ((bg shr 16) and 0xFF)) +
                    abs(((p shr 8) and 0xFF) - ((bg shr 8) and 0xFF)) +
                    abs((p and 0xFF) - (bg and 0xFF)) > COLOR_TOLERANCE
            }
        }
    }

    /** Connected components where pixels up to [BRIDGE] px apart belong together. */
    private fun blobs(solid: BooleanArray, w: Int, h: Int): List<Box> {
        val seen = BooleanArray(solid.size)
        val stack = IntArray(solid.size)
        val out = mutableListOf<Box>()
        for (start in solid.indices) {
            if (!solid[start] || seen[start]) continue
            var sp = 0
            stack[sp++] = start
            seen[start] = true
            var area = 0
            var x0 = w; var x1 = -1; var y0 = h; var y1 = -1
            while (sp > 0) {
                val i = stack[--sp]
                val x = i % w
                val y = i / w
                area++
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y
                for (ny in max(0, y - BRIDGE)..min(h - 1, y + BRIDGE)) {
                    val row = ny * w
                    for (nx in max(0, x - BRIDGE)..min(w - 1, x + BRIDGE)) {
                        val j = row + nx
                        if (solid[j] && !seen[j]) {
                            seen[j] = true
                            stack[sp++] = j
                        }
                    }
                }
            }
            if (area >= MIN_AREA) out += Box(x0, x1, y0, y1)
        }
        return out
    }

    /** Splits short blobs into label phrases (wide lines of text) and isolated marks. */
    private fun splitText(small: List<Box>): Pair<List<Box>, List<Box>> {
        val lines = mutableListOf<MutableList<Box>>()
        for (b in small.sortedWith(compareBy({ it.y0 }, { it.x0 }))) {
            val line = lines.firstOrNull { l ->
                val top = l.minOf { it.y0 }
                val bottom = l.maxOf { it.y1 }
                min(bottom, b.y1) - max(top, b.y0) >= 0.4 * min(b.h, bottom - top + 1)
            }
            if (line != null) line += b else lines += mutableListOf(b)
        }
        val labels = mutableListOf<Box>()
        val marks = mutableListOf<Box>()
        for (line in lines) {
            val phrases = mutableListOf<Box>()
            for (b in line.sortedBy { it.x0 }) {
                val last = phrases.lastOrNull()
                if (last != null && b.x0 - last.x1 <= 25) {
                    last.x1 = max(last.x1, b.x1); last.y0 = min(last.y0, b.y0); last.y1 = max(last.y1, b.y1)
                } else {
                    phrases += Box(b.x0, b.x1, b.y0, b.y1)
                }
            }
            phrases.forEach { if (it.w >= 1.8 * it.h) labels += it else marks += it }
        }
        return labels to marks
    }

    /** Copies the boxes into one transparent strip; frames keep their vertical offset (bounces survive). */
    private fun strip(boxes: List<Box>, px: IntArray, solid: BooleanArray, w: Int): Anim {
        val top = boxes.minOf { it.y0 }
        val frameH = boxes.maxOf { it.y1 } - top + 1
        val frameW = boxes.maxOf { it.w }
        val img = BufferedImage(frameW * boxes.size, frameH, BufferedImage.TYPE_INT_ARGB)
        boxes.forEachIndexed { i, b ->
            val dx = i * frameW + (frameW - b.w) / 2
            for (y in b.y0..b.y1) for (x in b.x0..b.x1) {
                val idx = y * w + x
                if (solid[idx]) img.setRGB(dx + x - b.x0, y - top, px[idx])
            }
        }
        return Anim(img, boxes.size, frameW, frameH)
    }
}
