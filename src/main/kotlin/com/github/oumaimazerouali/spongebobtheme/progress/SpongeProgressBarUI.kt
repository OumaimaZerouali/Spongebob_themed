package com.github.oumaimazerouali.spongebobtheme.progress

import com.github.oumaimazerouali.spongebobtheme.Palette
import com.github.oumaimazerouali.spongebobtheme.settings.SpongeSettings
import com.intellij.util.ui.JBUI
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.Timer
import javax.swing.plaf.basic.BasicProgressBarUI
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Underwater progress bar: a water fill with drifting light and bubbles,
 * and a runner (built-in jellyfish or your own image) that swims along the front.
 */
open class SpongeProgressBarUI : BasicProgressBarUI() {

    private companion object {
        const val FRAME_MS = 40
        const val LAP_MS = 2600.0
    }

    private var ticker: Timer? = null

    override fun installUI(c: JComponent) {
        super.installUI(c)
        ticker = Timer(FRAME_MS) { if (c.isShowing) c.repaint() }.also { it.start() }
    }

    override fun uninstallUI(c: JComponent) {
        ticker?.stop()
        ticker = null
        super.uninstallUI(c)
    }

    override fun getPreferredSize(c: JComponent): Dimension =
        Dimension(super.getPreferredSize(c).width, JBUI.scale(barHeight() + 2))

    override fun paintDeterminate(g: Graphics, c: JComponent) = paintBar(g, indeterminate = false)

    override fun paintIndeterminate(g: Graphics, c: JComponent) = paintBar(g, indeterminate = true)

    private fun paintBar(g0: Graphics, indeterminate: Boolean) {
        val bar = progressBar ?: return
        val g = g0.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

            val ins = bar.insets
            val w = bar.width - ins.left - ins.right
            val availH = bar.height - ins.top - ins.bottom
            if (w <= 0 || availH <= 0) return
            val h = min(availH, JBUI.scale(barHeight()))
            val x = ins.left
            val y = ins.top + (availH - h) / 2
            val now = System.currentTimeMillis()

            val settings = SpongeSettings.stateOrDefault()
            val trackTile = PixelImages.load(settings.trackTilePath)
            val fillTile = PixelImages.load(settings.fillTilePath)
            // Pixel-art tiles get square corners; the drawn version is a rounded capsule.
            val arc = if (trackTile != null || fillTile != null) 0f else h.toFloat()
            val track = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), arc, arc)
            if (trackTile != null) {
                PixelImages.tile(g, trackTile, x, y, w, h)
            } else {
                g.paint = GradientPaint(0f, y.toFloat(), Palette.DEEP, 0f, (y + h).toFloat(), Palette.ABYSS)
                g.fill(track)
            }

            val oldClip = g.clip
            g.clip(track)

            val runnerH = h
            val runnerW = min(RunnerSprite.width(runnerH), w)
            val runnerX: Int
            val facingLeft: Boolean
            var amountFull = 0

            if (!indeterminate) {
                amountFull = (w * bar.percentComplete).toInt().coerceIn(0, w)
                if (fillTile != null) {
                    PixelImages.tile(g, fillTile, x, y, amountFull, h, offset = -(now / 60).toInt())
                } else {
                    paintWater(g, x, y, amountFull, h, now)
                    paintRisingBubbles(g, x, y, amountFull, h, now)
                }
                runnerX = (x + amountFull - runnerW).coerceIn(x, x + w - runnerW)
                facingLeft = false
            } else {
                val t = (now % LAP_MS.toLong()) / LAP_MS
                val forward = t < 0.5
                val p = easeInOut(if (forward) t * 2 else 2 - t * 2)
                runnerX = x + ((w - runnerW) * p).toInt()
                facingLeft = !forward
                if (trackTile == null) paintShimmer(g, x, y, w, h, now)
                paintTrail(g, runnerX, runnerW, y, h, facingLeft, now)
            }

            g.clip = oldClip
            if (trackTile == null) {
                g.color = Palette.REEF
                g.stroke = BasicStroke(JBUI.scale(1).toFloat())
                g.draw(track)
            }

            // Runner is drawn last and unclipped, so the bar's rounded ends never cut off its pixels.
            RunnerSprite.paint(g, runnerX, y, runnerW, runnerH, now, facingLeft, bar)

            if (bar.isStringPainted) paintString(g, x, y, w, h, amountFull, ins)
        } finally {
            g.dispose()
        }
    }

    private fun paintWater(g: Graphics2D, x: Int, y: Int, fill: Int, h: Int, now: Long) {
        if (fill <= 0) return
        g.paint = GradientPaint(0f, y.toFloat(), Palette.LAGOON, 0f, (y + h).toFloat(), Palette.LAGOON_DEEP)
        g.fillRect(x, y, fill, h)

        // Slanted light rays drifting through the water
        val old = g.clip
        g.clipRect(x, y, fill, h)
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f)
        g.color = Color.WHITE
        val spacing = JBUI.scale(14)
        val stripe = JBUI.scale(5)
        val offset = ((now / 45) % spacing).toInt()
        var sx = x - h + offset
        while (sx < x + fill) {
            val ray = Path2D.Float()
            ray.moveTo(sx.toFloat(), (y + h).toFloat())
            ray.lineTo((sx + h).toFloat(), y.toFloat())
            ray.lineTo((sx + h + stripe).toFloat(), y.toFloat())
            ray.lineTo((sx + stripe).toFloat(), (y + h).toFloat())
            ray.closePath()
            g.fill(ray)
            sx += spacing
        }
        g.composite = AlphaComposite.SrcOver

        // Little wave crest along the top of the water
        g.color = Color(255, 255, 255, 90)
        g.stroke = BasicStroke(JBUI.scale(1).toFloat())
        val crest = Path2D.Float()
        crest.moveTo(x.toFloat(), y + 2f)
        var cx = x
        while (cx <= x + fill) {
            crest.lineTo(cx.toFloat(), (y + 2 + sin(cx / 4.0 + now / 160.0)).toFloat())
            cx += 2
        }
        g.draw(crest)
        g.clip = old
    }

    private fun paintShimmer(g: Graphics2D, x: Int, y: Int, w: Int, h: Int, now: Long) {
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f)
        g.paint = GradientPaint(0f, y.toFloat(), Palette.LAGOON_DEEP, 0f, (y + h).toFloat(), Palette.DEEP)
        g.fillRect(x, y, w, h)
        g.composite = AlphaComposite.SrcOver
        // sandy floor dots
        g.color = Color(Palette.SPONGE.red, Palette.SPONGE.green, Palette.SPONGE.blue, 40)
        val d = max(1, JBUI.scale(2))
        var sx = x + 3
        var k = 0
        while (sx < x + w) {
            g.fillOval(sx, y + h - d - 1 - (k % 2), d, d)
            sx += JBUI.scale(9) + (k * 7) % 5
            k++
        }
    }

    private fun paintRisingBubbles(g: Graphics2D, x: Int, y: Int, fill: Int, h: Int, now: Long) {
        if (fill < JBUI.scale(8)) return
        g.stroke = BasicStroke(max(1f, JBUI.scale(1).toFloat()))
        val count = max(3, fill / JBUI.scale(18))
        for (n in 0 until count) {
            val speed = 18 + (n * 7) % 23
            val travel = h + 8
            val rise = ((now / speed + n * 37) % travel).toInt()
            val bx = x + ((n * 53 + 11) % max(1, fill)) + (sin(now / 300.0 + n) * 2).toInt()
            val by = y + h - rise + 4
            val r = JBUI.scale(1 + (n % 3))
            g.color = Color(255, 255, 255, 170)
            g.draw(Ellipse2D.Float((bx - r).toFloat(), (by - r).toFloat(), (2 * r).toFloat(), (2 * r).toFloat()))
        }
    }

    private fun paintTrail(g: Graphics2D, runnerX: Int, runnerW: Int, y: Int, h: Int, facingLeft: Boolean, now: Long) {
        val dir = if (facingLeft) 1 else -1
        val startX = if (facingLeft) runnerX + runnerW else runnerX
        g.stroke = BasicStroke(max(1f, JBUI.scale(1).toFloat()))
        for (k in 1..6) {
            val bx = startX + dir * k * JBUI.scale(7)
            val by = y + h / 2 + (sin(now / 120.0 + k * 1.3) * h / 4).toInt()
            val r = JBUI.scale(max(1, 3 - k / 2))
            val alpha = (200 - k * 28).coerceAtLeast(30)
            g.color = Color(255, 255, 255, alpha)
            g.draw(Ellipse2D.Float((bx - r).toFloat(), (by - r).toFloat(), (2 * r).toFloat(), (2 * r).toFloat()))
        }
    }

    private fun barHeight(): Int = SpongeSettings.stateOrDefault().barHeight.coerceIn(8, 64)

    private fun easeInOut(t: Double): Double = 0.5 - cos(t * PI) / 2
}
