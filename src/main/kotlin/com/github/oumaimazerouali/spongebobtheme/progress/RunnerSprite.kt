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
 * The thing that runs over the progress bar.
 * Uses the image chosen in Settings (PNG/JPG/animated GIF) and falls back to a drawn jellyfish.
 */
object RunnerSprite {

    fun invalidate() = PixelImages.invalidate()

    private class Sheet(val image: Image, val frames: Int, val frameW: Int, val frameH: Int, val animatedGif: Boolean)

    private fun sheet(): Sheet? {
        val settings = SpongeSettings.stateOrDefault()
        val img = PixelImages.load(settings.runnerImagePath) ?: return null
        val iw = img.getWidth(null)
        val ih = img.getHeight(null)
        if (iw <= 0 || ih <= 0) return null
        if (img !is BufferedImage) return Sheet(img, 1, iw, ih, animatedGif = true)
        val frames = when {
            settings.runnerFrames > 0 -> settings.runnerFrames
            iw % ih == 0 -> iw / ih // square frames side by side
            else -> 1
        }.coerceIn(1, iw)
        return Sheet(img, frames, iw / frames, ih, animatedGif = false)
    }

    /** Width the runner needs at the given height (keeps the frame's aspect ratio). */
    fun width(height: Int): Int {
        val s = sheet() ?: return height
        return max(1, (height * s.frameW.toDouble() / s.frameH).roundToInt())
    }

    fun paint(g: Graphics2D, x: Int, y: Int, w: Int, h: Int, now: Long, facingLeft: Boolean, observer: ImageObserver) {
        val s = sheet()
        if (s == null) {
            paintJellyfish(g, x, y, h, now, facingLeft)
            return
        }
        val g2 = g.create() as Graphics2D
        try {
            PixelImages.setScalingHint(g2, s.frameH, h)
            val millis = max(16, SpongeSettings.stateOrDefault().frameMillis)
            val frame = if (s.frames > 1) ((now / millis) % s.frames).toInt() else 0
            val sx = frame * s.frameW
            // Swapping the destination x coordinates mirrors the frame when heading back left.
            val dx1 = if (facingLeft) x + w else x
            val dx2 = if (facingLeft) x else x + w
            g2.drawImage(s.image, dx1, y, dx2, y + h, sx, 0, sx + s.frameW, s.frameH, observer)
        } finally {
            g2.dispose()
        }
    }

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
