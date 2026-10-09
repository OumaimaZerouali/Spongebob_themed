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

    /** Optional path to a PNG/JPG/animated GIF used as the runner. Empty = built-in jellyfish. */
    var runnerImagePath by string()
}

@Service(Service.Level.APP)
@State(name = "SpongebobThemeSettings", storages = [Storage("spongebobTheme.xml")])
class SpongeSettings : SimplePersistentStateComponent<SpongeSettingsState>(SpongeSettingsState()) {
    companion object {
        fun getInstance(): SpongeSettings =
            ApplicationManager.getApplication().getService(SpongeSettings::class.java)
    }
}
