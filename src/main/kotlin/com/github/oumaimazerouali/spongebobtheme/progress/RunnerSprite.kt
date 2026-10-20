package com.github.oumaimazerouali.spongebobtheme.progress

import com.github.oumaimazerouali.spongebobtheme.Palette
import com.github.oumaimazerouali.spongebobtheme.settings.SpongeSettings
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.Image
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.image.ImageObserver
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The characters that run over the progress bar and visit the editor.
 * Built from the image chosen in Settings (sprite sheet, single image or animated GIF);
 * without one, a drawn jellyfish is used.
 */
object RunnerSprite {

    private var cacheKey: Any? = null
    private var cached: List<CharacterSprites> = emptyList()

    fun invalidate() {
        PixelImages.invalidate()
        cacheKey = null
        cached = emptyList()
    }

    /** Characters found in the chosen image; empty = use the built-in jellyfish. */
    fun characters(): List<CharacterSprites> {
        val settings = SpongeSettings.stateOrDefault()
        val img = PixelImages.load(settings.runnerImagePath) ?: return emptyList()
        val key = listOf(img, settings.autoSlice, settings.runnerFrames)
        if (key != cacheKey) {
            cacheKey = key
            cached = runCatching { build(img, settings.autoSlice, settings.runnerFrames) }.getOrDefault(emptyList())
        }
        return cached
    }

    /** The character for the "Character" setting: 0 = random (stable per [seed]), otherwise 1-based. */
    fun pick(seed: Int): CharacterSprites? {
        val all = characters()
        if (all.isEmpty()) return null
        val choice = SpongeSettings.stateOrDefault().sliceRow
        return if (choice <= 0) all[Math.floorMod(seed, all.size)] else all[(choice - 1).coerceIn(0, all.lastIndex)]
    }

    /** Describes the characters an image would give, for the settings dropdown. */
    fun describe(img: Image, autoSlice: Boolean, framesSetting: Int): List<String> =
        runCatching { build(img, autoSlice, framesSetting) }.getOrDefault(emptyList()).map { ch ->
            val what = if (ch.actions.isEmpty()) "${ch.run.frames} frames" else
                CharacterSprites.SHEET_ORDER.filter { it in ch.anims }.joinToString(", ") { it.name.lowercase() }
            "${ch.name} – $what"
        }

    private fun build(img: Image, autoSlice: Boolean, framesSetting: Int): List<CharacterSprites> {
        val iw = img.getWidth(null)
        val ih = img.getHeight(null)
        if (iw <= 0 || ih <= 0) return emptyList()
        if (img !is BufferedImage) {
            return listOf(CharacterSprites("Character 1", mapOf(Action.RUN to Anim(img, 1, iw, ih, animatedGif = true))))
        }
        // Auto-slice wins over a leftover grid frame count; small square strips are still read as a grid.
        if (autoSlice && !SpriteSlicer.looksLikeGrid(img)) {
            val layout = SpriteSlicer.analyse(img)
            if (layout != null) {
                val groups = layout.groups
                val order = CharacterSprites.SHEET_ORDER
                return if (layout.labelled && groups.size >= order.size) {
                    groups.chunked(order.size).filter { it.size == order.size }.mapIndexed { i, chunk ->
                        CharacterSprites("Character ${i + 1}", order.zip(chunk).toMap())
                    }
                } else {
                    groups.mapIndexed { i, g -> CharacterSprites("Row ${i + 1}", mapOf(Action.RUN to g)) }
                }
            }
        }
        val frames = when {
            framesSetting > 0 -> framesSetting
            iw % ih == 0 -> iw / ih // square frames side by side
            else -> 1
        }.coerceIn(1, iw)
        return listOf(CharacterSprites("Character 1", mapOf(Action.RUN to Anim(img, frames, iw / frames, ih))))
    }

    /** Bar height for the "Auto" setting: pixel art stays 16 px, bigger sprites get room to breathe. */
    fun autoBarHeight(): Int {
        val h = characters().firstOrNull()?.run?.frameH ?: return 16
        return when {
            h <= 16 -> 16
            h <= 24 -> 24
            else -> 32
        }
    }

    /**
     * Scale that makes the character's run frames [targetH] high. Other animations use the same scale
     * (a sitting character stays smaller), shrunk only when they would not fit in [maxH].
     */
    fun scaleFor(ch: CharacterSprites, anim: Anim, targetH: Int, maxH: Int = Int.MAX_VALUE): Double {
        val base = targetH.toDouble() / ch.run.frameH
        return if (anim.frameH * base > maxH) maxH.toDouble() / anim.frameH else base
    }

    fun drawWidth(anim: Anim, scale: Double): Int = max(1, (anim.frameW * scale).roundToInt())

    /** Draws [frame] of [anim] with its bottom edge on [bottom]. */
    fun paintFrame(
        g: Graphics2D, anim: Anim, frame: Int, x: Int, bottom: Int, scale: Double,
        facingLeft: Boolean, observer: ImageObserver,
    ) {
        val w = drawWidth(anim, scale)
        val h = max(1, (anim.frameH * scale).roundToInt())
        val g2 = g.create() as Graphics2D
        try {
            PixelImages.setScalingHint(g2, anim.frameH, h)
            val sx = Math.floorMod(frame, anim.frames) * anim.frameW
            // Swapping the destination x coordinates mirrors the frame when heading left.
            val dx1 = if (facingLeft) x + w else x
            val dx2 = if (facingLeft) x else x + w
            g2.drawImage(anim.image, dx1, bottom - h, dx2, bottom, sx, 0, sx + anim.frameW, anim.frameH, observer)
        } finally {
            g2.dispose()
        }
    }

    /** The built-in jellyfish, [s] px high, top-left at (x, y). */
    fun paintJelly(g: Graphics2D, x: Int, y: Int, s: Int, now: Long, facingLeft: Boolean) =
        paintJellyfish(g, x, y, s, now, facingLeft)

    private fun paintJellyfish(g: Graphics2D, x: Int, y: Int, s: Int, now: Long, facingLeft: Boolean) {
        val sf = s.toFloat()
        val bob = (sin(now / 110.0) * sf * 0.07).toFloat()
        val domeW = sf * 0.82f
        val domeH = sf * 0.48f
        val dx = x + (sf - domeW) / 2f
        val dy = y + sf * 0.06f + bob
        val baseY = dy + domeH
        val back = if (facingLeft) 1f else -1f // tentacles stream away from the direction of travel

        // Tentacles
        g.stroke = BasicStroke(max(1f, sf / 13f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.color = Palette.JELLY_DARK
        for (k in 0 until 4) {
            val tx = dx + domeW * (0.2f + 0.2f * k)
            val path = Path2D.Float()
            path.moveTo(tx, baseY)
            val segs = 3
            val segLen = (y + sf - 1 - baseY) / segs
            for (j in 1..segs) {
                val wave = sin(now / 95.0 + k * 0.9 + j * 1.4).toFloat() * sf * 0.05f
                val drift = back * sf * 0.07f * j
                path.lineTo(tx + wave + drift, baseY + segLen * j)
            }
            g.draw(path)
        }

        // Bell
        g.paint = GradientPaint(dx, dy, Palette.JELLY_LIGHT, dx, baseY, Palette.JELLY)
        g.fill(Arc2D.Float(dx, dy, domeW, domeH * 2f, 0f, 180f, Arc2D.CHORD))
        // Scalloped rim
        val bumps = 4
        val bw = domeW / bumps
        for (b in 0 until bumps) {
            g.fill(Ellipse2D.Float(dx + b * bw, baseY - bw * 0.35f, bw, bw * 0.7f))
        }
        // Spots
        g.color = Color(255, 255, 255, 150)
        g.fill(Ellipse2D.Float(dx + domeW * 0.18f, dy + domeH * 0.22f, domeW * 0.14f, domeH * 0.2f))
        g.fill(Ellipse2D.Float(dx + domeW * 0.62f, dy + domeH * 0.15f, domeW * 0.09f, domeH * 0.14f))

        // Face looks the way it swims
        val faceShift = if (facingLeft) -domeW * 0.08f else domeW * 0.08f
        val eyeR = max(1.2f, sf * 0.06f)
        val eyeY = dy + domeH * 0.55f
        val cx = dx + domeW / 2f + faceShift
        g.color = Palette.INK
        g.fill(Ellipse2D.Float(cx - domeW * 0.17f - eyeR, eyeY - eyeR, eyeR * 2, eyeR * 2))
        g.fill(Ellipse2D.Float(cx + domeW * 0.17f - eyeR, eyeY - eyeR, eyeR * 2, eyeR * 2))
        g.color = Color.WHITE
        val glint = eyeR * 0.6f
        g.fill(Ellipse2D.Float(cx - domeW * 0.17f - eyeR * 0.3f, eyeY - eyeR * 0.8f, glint, glint))
        g.fill(Ellipse2D.Float(cx + domeW * 0.17f - eyeR * 0.3f, eyeY - eyeR * 0.8f, glint, glint))
        // Smile
        if (s >= 12) {
            g.color = Palette.INK
            g.stroke = BasicStroke(max(1f, sf / 18f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(Arc2D.Float(cx - domeW * 0.08f, eyeY - domeH * 0.05f, domeW * 0.16f, domeH * 0.3f, 200f, 140f, Arc2D.OPEN))
        }
        // Cheeks
        g.color = Color(Palette.CORAL.red, Palette.CORAL.green, Palette.CORAL.blue, 120)
        val ch = eyeR * 1.1f
        g.fill(Ellipse2D.Float(cx - domeW * 0.32f - ch / 2, eyeY + eyeR * 0.6f, ch, ch * 0.7f))
        g.fill(Ellipse2D.Float(cx + domeW * 0.32f - ch / 2, eyeY + eyeR * 0.6f, ch, ch * 0.7f))
    }
}
