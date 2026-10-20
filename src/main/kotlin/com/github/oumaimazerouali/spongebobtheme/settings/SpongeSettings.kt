package com.github.oumaimazerouali.spongebobtheme.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

class SpongeSettingsState : BaseState() {
    /** Replace every progress bar in the IDE with the underwater one. */
    var runnerEnabled by property(true)

    /** PNG sprite sheet, single image or animated GIF used as the runner. Empty = built-in jellyfish. */
    var runnerImagePath by string()

    /** Frames in a grid sheet (laid out left to right). 0 = auto. Only used when auto-slice is off. */
    var runnerFrames by property(0)

    /** How long each sprite sheet frame is shown. */
    var frameMillis by property(100)

    /** Remove the sheet's background and detect frames automatically (ripped sheets). Small square grids still work. */
    var autoSlice by property(true)

    /** Which character of the sheet to use, 1 = first. 0 = a random one each time. */
    var sliceRow by property(1)

    /** Let the runner take short breaks (idle, sit, jump, wave, emote) when the sheet has those animations. */
    var randomBreaks by property(true)

    /** How often a character visits the editor and sits on a class: 0 off, 1 rarely, 2 sometimes, 3 often. */
    var editorVisits by property(2)

    /** Optional small image (e.g. a flower) drifting through the empty part of the bar. */
    var decorationPath by string()

    /** Recolour each copy of the decoration in a theme colour. */
    var decorationRecolor by property(true)

    /** Use the decoration as a scattered wallpaper behind the editor (IDE background image). */
    var wallpaperEnabled by property(false)

    /** Wallpaper opacity in percent; low keeps code readable. */
    var wallpaperOpacity by property(12)

    /** Also show the wallpaper behind panels like the project tree and tool windows. */
    var wallpaperPanels by property(false)

    /** Height of the bar in px (the runner is drawn this high). 0 = auto: 16 for pixel art, bigger for big sprites. */
    var barHeight by property(0)

    /** Optional tile repeated over the filled part of the bar. Empty = drawn water. */
    var fillTilePath by string()

    /** Optional tile repeated over the empty part of the bar. Empty = drawn deep sea. */
    var trackTilePath by string()
}

@Service(Service.Level.APP)
@State(name = "SpongebobThemeSettings", storages = [Storage("spongebobTheme.xml")])
class SpongeSettings : SimplePersistentStateComponent<SpongeSettingsState>(SpongeSettingsState()) {
    companion object {
        fun getInstance(): SpongeSettings =
            ApplicationManager.getApplication().getService(SpongeSettings::class.java)

        private val fallback = SpongeSettingsState()

        /** Settings, or a shared default instance when there is no application (e.g. headless rendering). */
        fun stateOrDefault(): SpongeSettingsState =
            runCatching { getInstance().state }.getOrNull() ?: fallback
    }
}
