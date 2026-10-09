package com.github.oumaimazerouali.spongebobtheme.progress

import com.github.oumaimazerouali.spongebobtheme.settings.SpongeSettings
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.awt.Window
import javax.swing.SwingUtilities
import javax.swing.UIManager

/** Swaps the IDE-wide ProgressBarUI for ours (and back). */
object SpongeProgressBarInstaller {
    private const val KEY = "ProgressBarUI"
    private val ourName: String = SpongeProgressBarLaf::class.java.name
    private var original: Any? = null

    private val enabled get() = SpongeSettings.getInstance().state.runnerEnabled

    /** Puts our UI in the defaults table. Cheap and idempotent, safe to call often. */
    fun install() {
        if (!enabled) return
        val current = UIManager.get(KEY)
        if (current == ourName) return
        original = current
        UIManager.put(KEY, ourName)
        // Point Swing straight at our class so it is loaded with the plugin's class loader.
        UIManager.getDefaults()[ourName] = SpongeProgressBarLaf::class.java
    }

    /** Called after settings change: install or restore, then refresh every open window. */
    fun refreshAll() {
        if (enabled) {
            install()
        } else if (UIManager.get(KEY) == ourName) {
            UIManager.put(KEY, original ?: "javax.swing.plaf.basic.BasicProgressBarUI")
        }
        Window.getWindows().forEach { SwingUtilities.updateComponentTreeUI(it) }
    }
}

/** Theme switches reset the UI defaults, so re-apply after every look-and-feel change. */
class SpongeLafListener : LafManagerListener {
    override fun lookAndFeelChanged(source: LafManager) = SpongeProgressBarInstaller.install()
}

class SpongeAppListener : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: MutableList<String>) = SpongeProgressBarInstaller.install()
}

/** Safety net: also covers installing the plugin without restarting the IDE. */
class SpongeStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().invokeLater { SpongeProgressBarInstaller.install() }
    }
}
