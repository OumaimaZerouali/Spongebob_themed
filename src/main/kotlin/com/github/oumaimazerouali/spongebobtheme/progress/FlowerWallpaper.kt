package com.github.oumaimazerouali.spongebobtheme.progress

import com.github.oumaimazerouali.spongebobtheme.settings.SpongeSettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import com.intellij.ui.ClientProperty
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
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
        protectEditorArea()
        Window.getWindows().forEach { it.repaint() }
    }

    private val OUR_MARK = Key.create<Boolean>("spongebob.theme.noWallpaper")
    private val WATCHED = Key.create<Boolean>("spongebob.theme.watched")

    /** IntelliJ's own opt-out for background images, looked up by name so we stay on public API. */
    @Suppress("UNCHECKED_CAST")
    private fun suppressKey(): Key<Boolean>? = Key.findKeyByName("SUPPRESS_BACKGROUND") as Key<Boolean>?

    private fun active(): Boolean {
        val props = PropertiesComponent.getInstance()
        return isOurs(props.getValue(EDITOR_PROP)) || isOurs(props.getValue(FRAME_PROP))
    }

    /**
     * IntelliJ paints the background image into every component that doesn't opt out, so images, SVG/Markdown
     * previews and other viewers would get flowers drawn over them. While our wallpaper is on, every component of a
     * non-text editor (and the editor area behind it) is opted out; text editors keep the wallpaper.
     */
    fun protectEditorArea() {
        val key = suppressKey() ?: return // no background painting loaded yet
        val on = active()
        for (window in Window.getWindows()) {
            UIUtil.uiTraverser(window).filter(JComponent::class.java).filter(::isEditorArea).forEach { mark(it, key, on) }
        }
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            for (editor in FileEditorManager.getInstance(project).allEditors) {
                for (root in viewerRoots(editor)) {
                    UIUtil.uiTraverser(root).filter(JComponent::class.java).forEach { mark(it, key, on) }
                    // The containers between the editor area and the viewer (tab, editor composite) paint too.
                    var parent = root.parent
                    while (parent is JComponent && !isEditorArea(parent)) {
                        mark(parent, key, on) // text editors don't need these: they get the wallpaper themselves
                        parent = parent.parent
                    }
                    if (on) watch(root, key)
                }
            }
        }
    }

    /** The parts of an editor that show something other than code. */
    private fun viewerRoots(editor: FileEditor): List<JComponent> = when (editor) {
        is TextEditorWithPreview -> listOf(editor.previewEditor.component)
        is TextEditor -> emptyList()
        else -> listOf(editor.component)
    }

    private fun mark(c: JComponent, key: Key<Boolean>, on: Boolean) {
        if (on) {
            ClientProperty.put(c, key, true)
            ClientProperty.put(c, OUR_MARK, true)
        } else if (ClientProperty.isTrue(c, OUR_MARK)) {
            ClientProperty.remove(c, key)
            ClientProperty.remove(c, OUR_MARK)
        }
        c.repaint()
    }

    /** Viewers build parts of their UI later (zoom, toolbars, previews): mark those children as they appear. */
    private fun watch(root: JComponent, key: Key<Boolean>) {
        UIUtil.uiTraverser(root).filter(JComponent::class.java).forEach { c ->
            if (ClientProperty.isTrue(c, WATCHED)) return@forEach
            ClientProperty.put(c, WATCHED, true)
            c.addContainerListener(object : ContainerAdapter() {
                override fun componentAdded(e: ContainerEvent) {
                    val child = e.child as? JComponent ?: return
                    if (!active()) return
                    UIUtil.uiTraverser(child).filter(JComponent::class.java).forEach { mark(it, key, true) }
                    watch(child, key)
                }
            })
        }
    }

    private fun isEditorArea(c: JComponent): Boolean {
        var cls: Class<*>? = c.javaClass
        while (cls != null && cls != JComponent::class.java) {
            if (cls.simpleName == "EditorsSplitters") return true
            cls = cls.superclass
        }
        return false
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
