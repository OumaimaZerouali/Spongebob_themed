package com.github.oumaimazerouali.spongebobtheme.progress

import com.github.oumaimazerouali.spongebobtheme.settings.SpongeSettings
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Image
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Decoration drifting through the empty part of the bar: copies of one small image (e.g. a flower),
 * scattered, slowly floating left, each in its own colour, now and then doing a spin.
 */
object FlowerField {
    /** Theme colours the decoration is recoloured to. */
    private val HUES = listOf(0xF58AA8, 0xFFD521, 0x45C7F5, 0xC59BFF, 0x9BE36B, 0xF47B35)
    private const val DRIFT_MS_PER_PX = 70L
    private const val SPIN_EVERY_MS = 9_000L
    private const val SPIN_MS = 1_200L

    private var cacheKey: Any? = null
    private var variants: List<BufferedImage> = emptyList()

    /** The decoration image, background removed, in every colour variant. Empty when none is set. */
    fun variants(): List<BufferedImage> {
        val settings = SpongeSettings.stateOrDefault()
        val src = PixelImages.load(settings.decorationPath)
        val key = listOf(src, settings.decorationRecolor)
        if (key != cacheKey) {
            cacheKey = key
            variants = runCatching { build(src, settings.decorationRecolor) }.getOrDefault(emptyList())
        }
        return variants
    }

    private fun build(src: Image?, recolor: Boolean): List<BufferedImage> {
        val img = src as? BufferedImage ?: return emptyList()
        val clean = trim(removeBackground(img)) ?: return emptyList()
        return if (recolor) HUES.map { recolored(clean, Color(it)) } else listOf(clean)
    }

    /** Paints the field over (x, y, w, h). [seed] keeps every bar's pattern different but stable. */
    fun paint(g: Graphics2D, x: Int, y: Int, w: Int, h: Int, now: Long, seed: Int) {
        val imgs = variants()
        if (imgs.isEmpty() || w <= 0 || h <= 0) return
        val g2 = g.create() as Graphics2D
        try {
            g2.clipRect(x, y, w, h)
            PixelImages.setScalingHint(g2, imgs[0].height, h)
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f)
            val spacing = max(h * 2, 24)
            val count = w / spacing + 2
            val period = w + spacing * 2
            for (i in 0 until count) {
                val r = hash(seed * 31 + i)
                val size = h * (0.5 + (r % 100) / 100.0 * 0.4) // 50–90% of the bar height
                val base = i * spacing + (r % spacing)
                val fx = x + Math.floorMod(base - (now / DRIFT_MS_PER_PX).toInt(), period) - spacing
                val fy = y + ((r / 100) % max(1, (h - size).toInt() + 1)).toInt()
                val img = imgs[(r / 7) % imgs.size]
                // Gentle sway all the time, a full spin every now and then (staggered per flower).
                val phase = Math.floorMod(now + r * 37L, SPIN_EVERY_MS)
                val spin = if (phase < SPIN_MS) phase.toDouble() / SPIN_MS * 2 * PI else 0.0
                val sway = Math.sin(now / 900.0 + i) * 0.15
                val g3 = g2.create() as Graphics2D
                try {
                    val half = size / 2
                    g3.translate(fx + half, fy + half)
                    g3.rotate(sway + spin + (r % 628) / 100.0)
                    val dw = size * img.width / img.height
                    g3.drawImage(img, (-dw / 2).toInt(), (-half).toInt(), dw.toInt(), size.toInt(), null)
                } finally {
                    g3.dispose()
                }
            }
        } finally {
            g2.dispose()
        }
    }

    private fun hash(n: Int): Int {
        var v = n * -0x61c88647
        v = v xor (v ushr 15)
        v *= 0x2c1b3c6d
        v = v xor (v ushr 12)
        return abs(v % 1_000_000)
    }

    /** Makes the background colour (top-left pixel) transparent. */
    private fun removeBackground(src: BufferedImage): BufferedImage {
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        val bg = src.getRGB(0, 0)
        val keyed = (bg ushr 24) >= 16
        for (y in 0 until src.height) for (x in 0 until src.width) {
            val p = src.getRGB(x, y)
            val diff = abs(((p shr 16) and 0xFF) - ((bg shr 16) and 0xFF)) +
                abs(((p shr 8) and 0xFF) - ((bg shr 8) and 0xFF)) + abs((p and 0xFF) - (bg and 0xFF))
            if ((p ushr 24) >= 16 && (!keyed || diff > 40)) out.setRGB(x, y, p)
        }
        return out
    }

    private fun trim(img: BufferedImage): BufferedImage? {
        var x0 = img.width; var y0 = img.height; var x1 = -1; var y1 = -1
        for (y in 0 until img.height) for (x in 0 until img.width) {
            if ((img.getRGB(x, y) ushr 24) >= 16) {
                x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y)
            }
        }
        if (x1 < 0) return null
        return img.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1)
    }

    /** Swaps the hue for [target]'s, keeping each pixel's light and dark so outlines stay readable. */
    private fun recolored(src: BufferedImage, target: Color): BufferedImage {
        val th = Color.RGBtoHSB(target.red, target.green, target.blue, null)
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        val hsb = FloatArray(3)
        for (y in 0 until src.height) for (x in 0 until src.width) {
            val p = src.getRGB(x, y)
            val a = p ushr 24
            if (a == 0) continue
            Color.RGBtoHSB((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF, hsb)
            val rgb = Color.HSBtoRGB(th[0], max(hsb[1], th[1] * 0.8f), hsb[2]) and 0xFFFFFF
            out.setRGB(x, y, (a shl 24) or rgb)
        }
        return out
    }
}
