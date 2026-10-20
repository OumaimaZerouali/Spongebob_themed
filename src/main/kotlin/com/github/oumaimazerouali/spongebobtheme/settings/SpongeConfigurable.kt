package com.github.oumaimazerouali.spongebobtheme.settings

import com.github.oumaimazerouali.spongebobtheme.progress.FlowerWallpaper
import com.github.oumaimazerouali.spongebobtheme.progress.PixelImages
import com.github.oumaimazerouali.spongebobtheme.progress.RunnerSprite
import com.github.oumaimazerouali.spongebobtheme.progress.SpongeProgressBarInstaller
import com.github.oumaimazerouali.spongebobtheme.progress.SpongeProgressBarUI
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.Timer
import javax.swing.event.DocumentEvent

class SpongeConfigurable : Configurable {
    /** A character of the sprite sheet as shown in the dropdown; 0 = random. */
    private data class CharacterOption(val index: Int, val label: String) {
        override fun toString() = label
    }

    /** Editor visit frequency; 0 = off. */
    private data class VisitOption(val level: Int) {
        override fun toString() = listOf("Off", "Rarely (~6 min)", "Sometimes (~3 min)", "Often (~1 min)")[level]
    }

    /** Bar height choice; 0 = auto. */
    private data class HeightOption(val px: Int) {
        override fun toString() = if (px == 0) "Auto (fits the sprite)" else "$px px"
    }

    private val enabledBox = JBCheckBox("Use the underwater progress bar with a runner")
    private val runnerField = imageField("Choose Runner Image", "Sprite sheet (PNG), single image, or animated GIF.")
    private val autoSliceBox = JBCheckBox("Auto-slice: remove the background and find the frames (works for ripped sheets)")
    private val rowBox = ComboBox<CharacterOption>()
    private val breaksBox = JBCheckBox("Random breaks: idle, sit, jump, wave and emotes between running")
    private val visitsBox = ComboBox(arrayOf(0, 1, 2, 3).map(::VisitOption).toTypedArray())
    private val decorationField = imageField("Choose Decoration", "A small image (e.g. a flower) that drifts through the empty bar.")
    private val recolorBox = JBCheckBox("Give every copy its own theme colour")
    private val wallpaperBox = JBCheckBox("Also use it as wallpaper behind the code")
    private val wallpaperPanelsBox = JBCheckBox("…and behind panels (project tree, tool windows)")
    private val opacitySpinner = JBIntSpinner(12, 1, 60)
    private val framesSpinner = JBIntSpinner(0, 0, 256)
    private val frameMsSpinner = JBIntSpinner(100, 16, 2000, 10)
    private val barHeightBox = ComboBox(arrayOf(0, 16, 20, 24, 32).map(::HeightOption).toTypedArray())
    private val fillField = imageField("Choose Fill Tile", "Repeated over the loaded part of the bar.")
    private val trackField = imageField("Choose Track Tile", "Repeated over the empty part of the bar.")
    private var previewTimer: Timer? = null

    override fun getDisplayName(): String = "Spongebob Theme"

    private fun imageField(title: String, description: String) = TextFieldWithBrowseButton().apply {
        addActionListener {
            // Plain constructor (single file, no folders/jars): works on 2024.3 and isn't deprecated in newer IDEs.
            val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
                .withExtensionFilter("Images", "png", "gif", "jpg", "jpeg")
                .withTitle(title)
                .withDescription(description)
            FileChooser.chooseFile(descriptor, null, null)?.let { text = it.path }
        }
    }

    private fun hint(text: String) = JBLabel(text).apply { foreground = UIUtil.getContextHelpForeground() }

    /** Fills the character dropdown with what the chosen image contains, keeping [select] selected. */
    private fun refreshRows(select: Int) {
        val img = PixelImages.load(runnerField.text.trim())
        val names = img?.let { RunnerSprite.describe(it, autoSliceBox.isSelected, framesSpinner.number) }.orEmpty()
        val options = names.mapIndexed { i, n -> CharacterOption(i + 1, n) }.ifEmpty { listOf(CharacterOption(1, "Character 1")) } +
            (if (names.size > 1) listOf(CharacterOption(0, "Random (each time)")) else emptyList())
        rowBox.model = DefaultComboBoxModel(options.toTypedArray())
        rowBox.selectedItem = options.firstOrNull { it.index == select } ?: options.first()
        rowBox.isEnabled = options.size > 1
        framesSpinner.isEnabled = !autoSliceBox.isSelected
    }

    private val selectedRow get() = (rowBox.selectedItem as? CharacterOption)?.index ?: 1

    private val selectedVisits get() = (visitsBox.selectedItem as? VisitOption)?.level ?: 0

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
            .addLabeledComponent("Character:", rowBox)
            .addComponentToRightColumn(hint("Labelled sheets: per character idle, run, sit, jump, wave, emote. See templates/."))
            .addComponent(breaksBox)
            .addLabeledComponent("Editor visits:", visitsBox)
            .addComponentToRightColumn(hint("The character sits on a class in your code now and then. Try it: Tools → Spongebob Theme: Visit This Editor."))
            .addLabeledComponent("Frame duration (ms):", frameMsSpinner)
            .addLabeledComponent("Bar height:", barHeightBox)
            .addLabeledComponent("Grid frames (advanced):", framesSpinner)
            .addComponentToRightColumn(hint("Only used with auto-slice off. 0 = automatic (square frames)."))
            .addSeparator()
            .addLabeledComponent("Decoration:", decorationField)
            .addComponentToRightColumn(recolorBox)
            .addComponentToRightColumn(hint("Scattered over the empty bar, drifting and spinning now and then. Empty = none."))
            .addComponentToRightColumn(wallpaperBox)
            .addComponentToRightColumn(wallpaperPanelsBox)
            .addLabeledComponent("Wallpaper opacity (%):", opacitySpinner)
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
            breaksBox.isSelected != state.randomBreaks ||
            selectedVisits != state.editorVisits ||
            decorationField.value() != state.decorationPath ||
            recolorBox.isSelected != state.decorationRecolor ||
            wallpaperBox.isSelected != state.wallpaperEnabled ||
            wallpaperPanelsBox.isSelected != state.wallpaperPanels ||
            opacitySpinner.number != state.wallpaperOpacity ||
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
        state.randomBreaks = breaksBox.isSelected
        state.editorVisits = selectedVisits
        state.decorationPath = decorationField.value()
        state.decorationRecolor = recolorBox.isSelected
        state.wallpaperEnabled = wallpaperBox.isSelected
        state.wallpaperPanels = wallpaperPanelsBox.isSelected
        state.wallpaperOpacity = opacitySpinner.number
        state.runnerFrames = framesSpinner.number
        state.frameMillis = frameMsSpinner.number
        state.barHeight = selectedHeight
        state.fillTilePath = fillField.value()
        state.trackTilePath = trackField.value()
        RunnerSprite.invalidate()
        SpongeProgressBarInstaller.refreshAll()
        FlowerWallpaper.apply()
    }

    override fun reset() {
        enabledBox.isSelected = state.runnerEnabled
        runnerField.text = state.runnerImagePath ?: ""
        autoSliceBox.isSelected = state.autoSlice
        framesSpinner.number = state.runnerFrames
        frameMsSpinner.number = state.frameMillis
        barHeightBox.selectedItem = HeightOption(state.barHeight)
        breaksBox.isSelected = state.randomBreaks
        visitsBox.selectedItem = VisitOption(state.editorVisits.coerceIn(0, 3))
        decorationField.text = state.decorationPath ?: ""
        recolorBox.isSelected = state.decorationRecolor
        wallpaperBox.isSelected = state.wallpaperEnabled
        wallpaperPanelsBox.isSelected = state.wallpaperPanels
        opacitySpinner.number = state.wallpaperOpacity
        fillField.text = state.fillTilePath ?: ""
        trackField.text = state.trackTilePath ?: ""
        refreshRows(state.sliceRow)
    }

    override fun disposeUIResources() {
        previewTimer?.stop()
        previewTimer = null
    }
}
