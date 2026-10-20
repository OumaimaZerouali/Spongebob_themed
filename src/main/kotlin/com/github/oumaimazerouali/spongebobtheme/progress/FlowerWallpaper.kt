package com.github.oumaimazerouali.spongebobtheme.progress

import com.github.oumaimazerouali.spongebobtheme.settings.SpongeSettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.PathManager
import java.awt.RenderingHints
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random

/**
 * Turns the decoration (e.g. a flower) into a seamless wallpaper and sets it as the IDE background image,
 * the same setting as *View → Appearance → Set Background Image*. Only touches that setting while it points
 * at our own file, so a background image you chose yourself is never overwritten or removed.
 */
object FlowerWallpaper {
    private const val EDITOR_PROP = "idea.background.editor"
    private const val FRAME_PROP = "idea.background.frame"
    private const val TILE = 640
    private const val FLOWERS = 9

    private val file: File get() = File(PathManager.getConfigPath(), "spongebob-theme/flower-wallpaper.png")

    /** Applies the current settings: (re)generates and sets the wallpaper, or removes ours. */
    fun apply() {
        val s = SpongeSettings.stateOrDefault()
        val flowers = FlowerField.variants()
        val props = PropertiesComponent.getInstance()
        if (!s.wallpaperEnabled || flowers.isEmpty()) {
            clear(props, EDITOR_PROP)
            clear(props, FRAME_PROP)
        } else {
            render(flowers, s.decorationPath.hashCode())
            val spec = "${file.absolutePath},${s.wallpaperOpacity.coerceIn(1, 100)},tile,center"
            setIfOurs(props, EDITOR_PROP, spec)
            if (s.wallpaperPanels) setIfOurs(props, FRAME_PROP, spec) else clear(props, FRAME_PROP)
        }
        Window.getWindows().forEach { it.repaint() }
    }

    private fun isOurs(value: String?) = value != null && value.substringBefore(',') == file.absolutePath

    private fun setIfOurs(props: PropertiesComponent, key: String, spec: String) {
        val current = props.getValue(key)
        if (current.isNullOrBlank() || isOurs(current)) props.setValue(key, spec)
    }

    private fun clear(props: PropertiesComponent, key: String) {
        if (isOurs(props.getValue(key))) props.unsetValue(key)
    }

    /** Scatters flowers over a square tile; anything sticking out wraps to the other side, so tiling is seamless. */
    private fun render(flowers: List<BufferedImage>, seed: Int) {
        val img = BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            val rnd = Random(seed)
            val cells = 3
            val cell = TILE / cells
            var n = 0
            for (cy in 0 until cells) for (cx in 0 until cells) {
                if (n++ >= FLOWERS) break
                val flower = flowers[rnd.nextInt(flowers.size)]
                val size = rnd.nextInt(cell / 3, cell * 2 / 3)
                val w = size * flower.width / flower.height
                // Jittered grid: evenly spread but not in rows.
                val x = cx * cell + rnd.nextInt(cell)
                val y = cy * cell + rnd.nextInt(cell)
                val angle = rnd.nextDouble(0.0, Math.PI * 2)
                for (dx in intArrayOf(-TILE, 0, TILE)) for (dy in intArrayOf(-TILE, 0, TILE)) {
                    val g2 = g.create() as java.awt.Graphics2D
                    g2.translate(x + dx, y + dy)
                    g2.rotate(angle)
                    g2.drawImage(flower, -w / 2, -size / 2, w, size, null)
                    g2.dispose()
                }
            }
        } finally {
            g.dispose()
        }
        file.parentFile.mkdirs()
        ImageIO.write(img, "png", file)
    }
}
