package com.github.oumaimazerouali.spongebobtheme.buddy

import com.github.oumaimazerouali.spongebobtheme.progress.RunnerSprite
import com.github.oumaimazerouali.spongebobtheme.settings.SpongeSettings
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.random.Random

/** Now and then sends a character to sit on a class in the editor you're looking at. */
@Service(Service.Level.APP)
class EditorVisits : Disposable {
    companion object {
        private const val TICK_MS = 5_000
        private const val QUIET_AFTER_TYPING_MS = 8_000L

        fun getInstance(): EditorVisits = ApplicationManager.getApplication().getService(EditorVisits::class.java)
    }

    private var started = false
    private var lastTyping = 0L
    private var visit: EditorVisit? = null
    private var visitEditor: Editor? = null
    private val timer = Timer(TICK_MS) { tick() }

    fun ensureStarted() {
        if (started) return
        started = true
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                lastTyping = System.currentTimeMillis()
                val ed = visitEditor
                if (ed != null && ed.document == event.document) {
                    ApplicationManager.getApplication().invokeLater { visit?.onTyping() }
                }
            }
        }, this)
        timer.start()
        // -Dspongebob.demoVisit=true: start a visit a few seconds after startup (for screenshots/testing).
        if (System.getProperty("spongebob.demoVisit") != null) {
            Timer(12_000) {
                ProjectManager.getInstance().openProjects.firstNotNullOfOrNull {
                    FileEditorManager.getInstance(it).selectedTextEditor
                }?.let { visitNow(it) }
            }.apply { isRepeats = true; start() }
        }
    }

    /** Average time between visits for the setting: 1 rarely, 2 sometimes, 3 often. */
    private fun averageGapMs(level: Int): Long = when (level) {
        1 -> 6 * 60_000L
        2 -> 3 * 60_000L
        else -> 60_000L
    }

    private fun tick() {
        val level = SpongeSettings.stateOrDefault().editorVisits
        if (level <= 0 || visit != null) return
        if (System.currentTimeMillis() - lastTyping < QUIET_AFTER_TYPING_MS) return
        if (Random.nextDouble() > TICK_MS.toDouble() / averageGapMs(level)) return
        activeEditor()?.let { visitNow(it) }
    }

    /** The selected editor of the project window that currently has focus. */
    private fun activeEditor(): Editor? =
        ProjectManager.getInstance().openProjects.asSequence()
            .filterNot { it.isDisposed }
            .mapNotNull { FileEditorManager.getInstance(it).selectedTextEditor }
            .firstOrNull { ed ->
                ed.contentComponent.isShowing && SwingUtilities.getWindowAncestor(ed.contentComponent)?.isActive == true
            }

    /** Starts a visit right away. Returns false when there's no class in view. */
    fun visitNow(editor: Editor): Boolean {
        visit?.end()
        val ch = RunnerSprite.pick(Random.nextInt())
        val v = EditorVisit.start(editor, ch) {
            visit = null
            visitEditor = null
        } ?: return false
        visit = v
        visitEditor = editor
        return true
    }

    override fun dispose() {
        timer.stop()
        visit?.end()
    }
}

/** Tools → "Spongebob Theme: Visit This Editor" — handy to try it out without waiting. */
class VisitEditorAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        EditorVisits.getInstance().ensureStarted()
        if (!EditorVisits.getInstance().visitNow(editor)) {
            HintManager.getInstance().showInformationHint(editor, "No class declaration in view to sit on")
        }
    }
}
