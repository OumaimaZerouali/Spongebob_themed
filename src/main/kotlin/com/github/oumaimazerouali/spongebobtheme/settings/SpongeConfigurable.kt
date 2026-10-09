package com.github.oumaimazerouali.spongebobtheme.settings

import com.github.oumaimazerouali.spongebobtheme.progress.PixelImages
import com.github.oumaimazerouali.spongebobtheme.progress.RunnerSprite
import com.github.oumaimazerouali.spongebobtheme.progress.SpongeProgressBarInstaller
import com.github.oumaimazerouali.spongebobtheme.progress.SpongeProgressBarUI
import com.github.oumaimazerouali.spongebobtheme.progress.SpriteSlicer
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import java.awt.image.BufferedImage
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.Timer
import javax.swing.event.DocumentEvent

class SpongeConfigurable : Configurable {
    /** A row of the sprite sheet as shown in the dropdown. */
    private data class RowOption(val row: Int, val frames: Int?) {
        override fun toString() = if (frames == null) "Row $row" else "Row $row – $frames frames"
    }

    /** Bar height choice; 0 = auto. */
    private data class HeightOption(val px: Int) {
        override fun toString() = if (px == 0) "Auto (fits the sprite)" else "$px px"
    }

    private val enabledBox = JBCheckBox("Use the underwater progress bar with a runner")
    private val runnerField = imageField("Choose Runner Image", "Sprite sheet (PNG), single image, or animated GIF.")
    private val autoSliceBox = JBCheckBox("Auto-slice: remove the background and find the frames (works for ripped sheets)")
    private val rowBox = ComboBox<RowOption>()
    private val framesSpinner = JBIntSpinner(0, 0, 256)
    private val frameMsSpinner = JBIntSpinner(100, 16, 2000, 10)
    private val barHeightBox = ComboBox(arrayOf(0, 16, 20, 24, 32).map(::HeightOption).toTypedArray())
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

    /** Fills the row dropdown with the rows found in the chosen image, keeping [select] selected. */
    private fun refreshRows(select: Int) {
        val img = PixelImages.load(runnerField.text.trim()) as? BufferedImage
        val counts = if (img != null && !SpriteSlicer.looksLikeGrid(img)) SpriteSlicer.rowFrameCounts(img) else emptyList()
        val options = if (counts.isEmpty()) listOf(RowOption(1, null)) else counts.mapIndexed { i, n -> RowOption(i + 1, n) }
        rowBox.model = DefaultComboBoxModel(options.toTypedArray())
        rowBox.selectedItem = options.firstOrNull { it.row == select } ?: options.first()
        rowBox.isEnabled = autoSliceBox.isSelected && options.size > 1
    }

    private val selectedRow get() = (rowBox.selectedItem as? RowOption)?.row ?: 1

    override fun createComponent(): JComponent {
        val determinate = JProgressBar(0, 100).apply { value = 0; setUI(SpongeProgressBarUI()) }
        val indeterminate = JProgressBar().apply { isIndeterminate = true; setUI(SpongeProgressBarUI()) }
        previewTimer = Timer(60) { determinate.value = (determinate.value + 1) % 101 }.also { it.start() }

        runnerField.textField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = refreshRows(selectedRow)
        })
        autoSliceBox.addActionListener { refreshRows(selectedRow) }

        return FormBuilder.createFormBuilder()
            .addComponent(enabledBox)
            .addSeparator()
            .addLabeledComponent("Runner image:", runnerField)
            .addComponentToRightColumn(hint("Empty = built-in jellyfish. Keep your own images in my-sprites/ (git-ignored)."))
            .addComponent(autoSliceBox)
            .addLabeledComponent("Row / character:", rowBox)
            .addLabeledComponent("Frame duration (ms):", frameMsSpinner)
            .addLabeledComponent("Bar height:", barHeightBox)
            .addLabeledComponent("Grid frames (advanced):", framesSpinner)
            .addComponentToRightColumn(hint("0 = automatic. Only for sheets with equal-width frames that touch each other."))
            .addSeparator()
            .addLabeledComponent("Fill tile:", fillField)
            .addLabeledComponent("Track tile:", trackField)
            .addComponentToRightColumn(hint("Optional pixel-art tiles, repeated sideways. Empty = drawn water. Clear a field to reset it."))
            .addSeparator()
            .addLabeledComponent("Preview (press Apply to update):", determinate)
            .addComponentToRightColumn(indeterminate)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private val state get() = SpongeSettings.getInstance().state

    private fun TextFieldWithBrowseButton.value(): String? = text.trim().ifEmpty { null }

    private val selectedHeight get() = (barHeightBox.selectedItem as? HeightOption)?.px ?: 0

    override fun isModified(): Boolean =
        enabledBox.isSelected != state.runnerEnabled ||
            runnerField.value() != state.runnerImagePath ||
            autoSliceBox.isSelected != state.autoSlice ||
            selectedRow != state.sliceRow ||
            framesSpinner.number != state.runnerFrames ||
            frameMsSpinner.number != state.frameMillis ||
            selectedHeight != state.barHeight ||
            fillField.value() != state.fillTilePath ||
            trackField.value() != state.trackTilePath

    override fun apply() {
        state.runnerEnabled = enabledBox.isSelected
        state.runnerImagePath = runnerField.value()
        state.autoSlice = autoSliceBox.isSelected
        state.sliceRow = selectedRow
        state.runnerFrames = framesSpinner.number
        state.frameMillis = frameMsSpinner.number
        state.barHeight = selectedHeight
        state.fillTilePath = fillField.value()
        state.trackTilePath = trackField.value()
        RunnerSprite.invalidate()
        SpongeProgressBarInstaller.refreshAll()
    }

    override fun reset() {
        enabledBox.isSelected = state.runnerEnabled
        runnerField.text = state.runnerImagePath ?: ""
        autoSliceBox.isSelected = state.autoSlice
        framesSpinner.number = state.runnerFrames
        frameMsSpinner.number = state.frameMillis
        barHeightBox.selectedItem = HeightOption(state.barHeight)
        fillField.text = state.fillTilePath ?: ""
        trackField.text = state.trackTilePath ?: ""
        refreshRows(state.sliceRow)
    }

    override fun disposeUIResources() {
        previewTimer?.stop()
        previewTimer = null
    }
}
