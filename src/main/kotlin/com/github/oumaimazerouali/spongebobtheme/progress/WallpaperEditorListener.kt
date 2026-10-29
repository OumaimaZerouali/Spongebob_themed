package com.github.oumaimazerouali.spongebobtheme.progress

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile

/** New editor windows/splits create new editor areas: keep them opted out of the panel wallpaper. */
class WallpaperEditorListener : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) = refresh()
    override fun selectionChanged(event: FileEditorManagerEvent) = refresh()

    private fun refresh() {
        ApplicationManager.getApplication().invokeLater { FlowerWallpaper.protectEditorArea() }
    }
}
