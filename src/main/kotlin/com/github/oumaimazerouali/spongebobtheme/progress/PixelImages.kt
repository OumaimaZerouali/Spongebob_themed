package com.github.oumaimazerouali.spongebobtheme.progress

import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.ImageIcon

/** Loads user images once per path + modification time, so editing the file and pressing Apply picks it up. */
object PixelImages {
    private data class Key(val path: String, val modified: Long)

    private val cache = HashMap<Key, Image?>()

    fun invalidate() = cache.clear()

    fun load(path: String?): Image? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.isFile) return null
        val key = Key(file.absolutePath, file.lastModified())
        return cache.getOrPut(key) {
            runCatching {
                if (file.extension.equals("gif", ignoreCase = true)) {
                    // Toolkit images keep animated GIFs animating when drawn with an ImageObserver.
                    ImageIcon(file.absolutePath).image.takeIf { it.getWidth(null) > 0 }
                } else {
                    ImageIO.read(file) as BufferedImage?
                }
            }.getOrNull()
        }
    }

    /**
     * Pixel art stays crisp when it is scaled up (nearest neighbour);
     * anything that has to shrink uses smooth scaling instead of dropping pixels.
     */
    fun setScalingHint(g: Graphics2D, sourceHeight: Int, targetHeight: Int) {
        val deviceHeight = targetHeight * g.transform.scaleY
        val hint = if (deviceHeight >= sourceHeight) {
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
        } else {
            RenderingHints.VALUE_INTERPOLATION_BILINEAR
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint)
    }

    /** Repeats [img] horizontally over (x, y, w, h), scaled to height h. [offset] scrolls the pattern. */
    fun tile(g: Graphics2D, img: Image, x: Int, y: Int, w: Int, h: Int, offset: Int = 0) {
        val iw = img.getWidth(null)
        val ih = img.getHeight(null)
        if (iw <= 0 || ih <= 0 || w <= 0) return
        val tw = maxOf(1, Math.round(iw * h.toDouble() / ih).toInt())
        val g2 = g.create() as Graphics2D
        try {
            g2.clipRect(x, y, w, h)
            setScalingHint(g2, ih, h)
            var tx = x - Math.floorMod(offset, tw)
            while (tx < x + w) {
                g2.drawImage(img, tx, y, tw, h, null)
                tx += tw
            }
        } finally {
            g2.dispose()
        }
    }
}
