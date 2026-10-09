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
import java.io.File
import javax.swing.ImageIcon
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The thing that runs over the progress bar.
 * Uses the image chosen in Settings (PNG/JPG/animated GIF) and falls back to a drawn jellyfish.
 */
object RunnerSprite {
    private var loadedPath: String? = null
    private var loadedImage: Image? = null

    fun invalidate() {
        loadedPath = null
        loadedImage = null
    }

    private fun customImage(): Image? {
        val path = runCatching { SpongeSettings.getInstance().state.runnerImagePath }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: return null
        if (path != loadedPath) {
            loadedPath = path
            loadedImage = runCatching {
                val file = File(path)
                if (file.isFile) ImageIcon(file.absolutePath).image.takeIf { it.getWidth(null) > 0 } else null
            }.getOrNull()
        }
        return loadedImage
    }

    /** Width the runner needs at the given height (custom images keep their aspect ratio). */
    fun width(height: Int): Int {
        val img = customImage() ?: return height
        val iw = img.getWidth(null)
        val ih = img.getHeight(null)
        if (iw <= 0 || ih <= 0) return height
        return max(1, (height * iw.toDouble() / ih).roundToInt())
    }

    fun paint(g: Graphics2D, x: Int, y: Int, w: Int, h: Int, now: Long, facingLeft: Boolean, observer: ImageObserver) {
        val img = customImage()
        if (img != null) {
            val bob = (sin(now / 90.0) * h * 0.06).roundToInt()
            // Negative width mirrors the image when the runner heads back left.
            if (facingLeft) g.drawImage(img, x + w, y + bob, -w, h, observer)
            else g.drawImage(img, x, y + bob, w, h, observer)
        } else {
            paintJellyfish(g, x, y, h, now, facingLeft)
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
