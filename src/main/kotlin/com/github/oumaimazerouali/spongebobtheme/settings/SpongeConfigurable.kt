package com.github.oumaimazerouali.spongebobtheme.settings

import com.github.oumaimazerouali.spongebobtheme.progress.RunnerSprite
import com.github.oumaimazerouali.spongebobtheme.progress.SpongeProgressBarInstaller
import com.github.oumaimazerouali.spongebobtheme.progress.SpongeProgressBarUI
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.Timer

class SpongeConfigurable : Configurable {
    private val enabledBox = JBCheckBox("Use the underwater progress bar with a runner")
    private val pathField = TextFieldWithBrowseButton()
    private var previewTimer: Timer? = null

    override fun getDisplayName(): String = "Spongebob Theme"

    override fun createComponent(): JComponent {
        pathField.addActionListener {
            val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
                .withFileFilter { it.extension?.lowercase() in setOf("png", "gif", "jpg", "jpeg") }
                .withTitle("Choose Runner Image")
                .withDescription("PNG, JPG or animated GIF. It is scaled to the height of the progress bar.")
            FileChooser.chooseFile(descriptor, null, null)?.let { pathField.text = it.path }
        }
        val resetButton = JButton("Use Default Runner").apply { addActionListener { pathField.text = "" } }

        val determinate = JProgressBar(0, 100).apply { value = 0; setUI(SpongeProgressBarUI()) }
        val indeterminate = JProgressBar().apply { isIndeterminate = true; setUI(SpongeProgressBarUI()) }
        previewTimer = Timer(60) { determinate.value = (determinate.value + 1) % 101 }.also { it.start() }

        return FormBuilder.createFormBuilder()
            .addComponent(enabledBox)
            .addLabeledComponent("Runner image:", pathField)
            .addComponentToRightColumn(resetButton)
            .addComponentToRightColumn(
                JBLabel("Leave empty for the built-in jellyfish. Animated GIFs keep animating.").apply {
                    foreground = UIUtil.getContextHelpForeground()
                },
            )
            .addSeparator()
            .addLabeledComponent("Preview (press Apply to update):", determinate)
            .addComponentToRightColumn(indeterminate)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private val state get() = SpongeSettings.getInstance().state

    override fun isModified(): Boolean =
        enabledBox.isSelected != state.runnerEnabled || pathField.text.trim() != (state.runnerImagePath ?: "")

    override fun apply() {
        state.runnerEnabled = enabledBox.isSelected
        state.runnerImagePath = pathField.text.trim().ifEmpty { null }
        RunnerSprite.invalidate()
        SpongeProgressBarInstaller.refreshAll()
    }

    override fun reset() {
        enabledBox.isSelected = state.runnerEnabled
        pathField.text = state.runnerImagePath ?: ""
    }

    override fun disposeUIResources() {
        previewTimer?.stop()
        previewTimer = null
    }
}
