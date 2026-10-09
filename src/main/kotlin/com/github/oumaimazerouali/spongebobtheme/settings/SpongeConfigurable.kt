package com.github.oumaimazerouali.spongebobtheme.settings

import com.github.oumaimazerouali.spongebobtheme.progress.RunnerSprite
import com.github.oumaimazerouali.spongebobtheme.progress.SpongeProgressBarInstaller
import com.github.oumaimazerouali.spongebobtheme.progress.SpongeProgressBarUI
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.Timer

class SpongeConfigurable : Configurable {
    private val enabledBox = JBCheckBox("Use the underwater progress bar with a runner")
    private val runnerField = imageField("Choose Runner Image", "PNG sprite sheet (frames left to right), single PNG/JPG, or animated GIF.")
    private val framesSpinner = JBIntSpinner(0, 0, 256)
    private val frameMsSpinner = JBIntSpinner(100, 16, 2000, 10)
    private val autoSliceBox = JBCheckBox("Auto-slice: make the background colour transparent and find the frames")
    private val rowSpinner = JBIntSpinner(0, 0, 50)
    private val barHeightBox = ComboBox(arrayOf(16, 20, 24, 32))
    private val fillField = imageField("Choose Fill Tile", "Repeated over the loaded part of the bar.")
    private val trackField = imageField("Choose Track Tile", "Repeated over the empty part of the bar.")
    private var previewTimer: Timer? = null

    override fun getDisplayName(): String = "Spongebob Theme"

    private fun imageField(title: String, description: String) = TextFieldWithBrowseButton().apply {
        addActionListener {
            val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
                .withFileFilter { it.extension?.lowercase() in setOf("png", "gif", "jpg", "jpeg") }
                .withTitle(title)
                .withDescription(description)
            FileChooser.chooseFile(descriptor, null, null)?.let { text = it.path }
        }
    }

    private fun hint(text: String) = JBLabel(text).apply { foreground = UIUtil.getContextHelpForeground() }

    override fun createComponent(): JComponent {
        val determinate = JProgressBar(0, 100).apply { value = 0; setUI(SpongeProgressBarUI()) }
        val indeterminate = JProgressBar().apply { isIndeterminate = true; setUI(SpongeProgressBarUI()) }
        previewTimer = Timer(60) { determinate.value = (determinate.value + 1) % 101 }.also { it.start() }

        return FormBuilder.createFormBuilder()
            .addComponent(enabledBox)
            .addSeparator()
            .addLabeledComponent("Runner image:", runnerField)
            .addComponentToRightColumn(hint("Empty = built-in jellyfish. Pixel art: 16 px high frames, facing right."))
            .addLabeledComponent("Frames in sheet:", framesSpinner)
            .addComponentToRightColumn(hint("0 = auto (image width ÷ height, so square frames). 1 = single image."))
            .addLabeledComponent("Frame duration (ms):", frameMsSpinner)
            .addComponent(autoSliceBox)
            .addLabeledComponent("Row in sheet:", rowSpinner)
            .addComponentToRightColumn(hint("For ripped sheets with a solid background. 1 = top row, 0 = whole image."))
            .addLabeledComponent("Bar height (px):", barHeightBox)
            .addSeparator()
            .addLabeledComponent("Fill tile:", fillField)
            .addLabeledComponent("Track tile:", trackField)
            .addComponentToRightColumn(hint("Optional, bar height high (16 px by default), repeated sideways. Empty = drawn water. Clear a field to reset it."))
            .addSeparator()
            .addLabeledComponent("Preview (press Apply to update):", determinate)
            .addComponentToRightColumn(indeterminate)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private val state get() = SpongeSettings.getInstance().state

    private fun TextFieldWithBrowseButton.value(): String? = text.trim().ifEmpty { null }

    override fun isModified(): Boolean =
        enabledBox.isSelected != state.runnerEnabled ||
            runnerField.value() != state.runnerImagePath ||
            framesSpinner.number != state.runnerFrames ||
            frameMsSpinner.number != state.frameMillis ||
            autoSliceBox.isSelected != state.autoSlice ||
            rowSpinner.number != state.sliceRow ||
            (barHeightBox.selectedItem as Int) != state.barHeight ||
            fillField.value() != state.fillTilePath ||
            trackField.value() != state.trackTilePath

    override fun apply() {
        state.runnerEnabled = enabledBox.isSelected
        state.runnerImagePath = runnerField.value()
        state.runnerFrames = framesSpinner.number
        state.frameMillis = frameMsSpinner.number
        state.autoSlice = autoSliceBox.isSelected
        state.sliceRow = rowSpinner.number
        state.barHeight = barHeightBox.selectedItem as Int
        state.fillTilePath = fillField.value()
        state.trackTilePath = trackField.value()
        RunnerSprite.invalidate()
        SpongeProgressBarInstaller.refreshAll()
    }

    override fun reset() {
        enabledBox.isSelected = state.runnerEnabled
        runnerField.text = state.runnerImagePath ?: ""
        framesSpinner.number = state.runnerFrames
        frameMsSpinner.number = state.frameMillis
        autoSliceBox.isSelected = state.autoSlice
        rowSpinner.number = state.sliceRow
        barHeightBox.selectedItem = state.barHeight
        fillField.text = state.fillTilePath ?: ""
        trackField.text = state.trackTilePath ?: ""
    }

    override fun disposeUIResources() {
        previewTimer?.stop()
        previewTimer = null
    }
}
