package com.github.oumaimazerouali.spongebobtheme.buddy

import com.github.oumaimazerouali.spongebobtheme.progress.Action
import com.github.oumaimazerouali.spongebobtheme.progress.CharacterSprites
import com.github.oumaimazerouali.spongebobtheme.progress.RunnerBrain
import com.github.oumaimazerouali.spongebobtheme.progress.RunnerSprite
import com.github.oumaimazerouali.spongebobtheme.settings.SpongeSettings
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.Timer
import kotlin.random.Random

/**
 * One visit: the character runs in from the right edge of the visible code, sits down on top of a
 * class declaration, does something (wave, emote, idle), then runs off again.
 *
 * Drawing happens through a line highlighter with a custom renderer, so nothing in the editor's
 * component tree changes and the text stays fully usable.
 */
class EditorVisit private constructor(
    private val editor: Editor,
    private val ch: CharacterSprites?,
    private val line: Int,
    private val targetX: Int,
    private val onEnd: () -> Unit,
) {
    private enum class Phase { ENTER, SIT, ACT, LEAVE }

    private val brain = RunnerBrain(minBreakGapMs = Long.MAX_VALUE / 4, maxBreakGapMs = Long.MAX_VALUE / 2)
    private var phase = Phase.ENTER
    private var phaseEnd = 0L
    private var x: Double
    private var facingLeft = true
    private var speedFactor = 1.0
    private var lastTick = System.currentTimeMillis()
    private var lastBounds: Rectangle? = null
    private val highlighter: RangeHighlighter
    private val timer: Timer

    init {
        val visible = editor.scrollingModel.visibleArea
        x = (visible.x + visible.width).toDouble()
        highlighter = editor.markupModel.addLineHighlighter(line, HighlighterLayer.LAST + 100, null)
        highlighter.customRenderer = CustomHighlighterRenderer { _, _, g -> paint(g) }
        timer = Timer(40) { tick() }
        timer.start()
    }

    private val frameMs get() = SpongeSettings.stateOrDefault().frameMillis.toLong().coerceAtLeast(16)
    private val height get() = (editor.lineHeight * 1.8).toInt()
    private val runWidth: Int
        get() = ch?.let { RunnerSprite.drawWidth(it.run, RunnerSprite.scaleFor(it, it.run, height)) } ?: height
    private val speedPxPerMs get() = editor.lineHeight / 70.0 * speedFactor

    /** Typing in this editor: hurry off so the code is free again. */
    fun onTyping() {
        if (phase != Phase.LEAVE) startLeaving()
        speedFactor = 2.5
    }

    fun end() {
        timer.stop()
        if (!editor.isDisposed) {
            editor.markupModel.removeHighlighter(highlighter)
            lastBounds?.let { editor.contentComponent.repaint(it) }
        }
        onEnd()
    }

    private fun startLeaving() {
        phase = Phase.LEAVE
        facingLeft = false
        brain.runNow(System.currentTimeMillis())
    }

    private fun tick() {
        if (editor.isDisposed || !highlighter.isValid) return end()
        val now = System.currentTimeMillis()
        val dt = (now - lastTick).coerceIn(0, 200)
        lastTick = now
        when (phase) {
            Phase.ENTER -> {
                x -= dt * speedPxPerMs
                if (x <= targetX) {
                    x = targetX.toDouble()
                    phase = Phase.SIT
                    ch?.let { brain.start(Action.SIT, now, it, frameMs, durationMs = Random.nextLong(3_500, 6_000)) }
                    phaseEnd = now + 4_500
                }
            }
            Phase.SIT -> if (brain.action == Action.RUN || now >= phaseEnd) {
                phase = Phase.ACT
                val options = ch?.actions.orEmpty().filter { it == Action.WAVE || it == Action.EMOTE || it == Action.IDLE }
                if (ch != null && options.isNotEmpty()) brain.start(options.random(), now, ch, frameMs, durationMs = 2_000)
                phaseEnd = now + 2_000
            }
            Phase.ACT -> if (now >= phaseEnd) startLeaving()
            Phase.LEAVE -> {
                x += dt * speedPxPerMs
                val visible = editor.scrollingModel.visibleArea
                if (x > visible.x + visible.width) return end()
            }
        }
        repaint()
    }

    private fun bounds(): Rectangle {
        val top = editor.logicalPositionToXY(LogicalPosition(line, 0)).y
        val bottom = top + (editor.lineHeight * 0.3).toInt() // feet on the text, like sitting on it
        val w = runWidth * 2 // room for wider poses
        return Rectangle(x.toInt() - w / 4, bottom - height * 2, w, height * 2 + 2)
    }

    private fun repaint() {
        val b = bounds()
        val dirty = lastBounds?.union(b) ?: b
        lastBounds = b
        editor.contentComponent.repaint(dirty)
    }

    private fun paint(g0: Graphics) {
        val g = g0.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val top = editor.logicalPositionToXY(LogicalPosition(line, 0)).y
            val bottom = top + (editor.lineHeight * 0.3).toInt()
            val now = System.currentTimeMillis()
            val moving = phase == Phase.ENTER || phase == Phase.LEAVE
            val left = if (moving) phase == Phase.ENTER else facingLeft
            if (ch != null) {
                val (anim, frame) = if (moving) ch.run to ((now / frameMs) % ch.run.frames).toInt() else brain.frame(now, ch, frameMs)
                val scale = RunnerSprite.scaleFor(ch, anim, height)
                val w = RunnerSprite.drawWidth(anim, scale)
                RunnerSprite.paintFrame(g, anim, frame, x.toInt() + (runWidth - w) / 2, bottom, scale, left, editor.contentComponent)
            } else {
                RunnerSprite.paintJelly(g, x.toInt(), bottom - height, height, now, left)
            }
        } finally {
            g.dispose()
        }
    }

    companion object {
        private val DECLARATION = Regex("""\b(class|interface|enum|record|object|struct|trait)\s+([A-Za-z_]\w*)""")

        /** Starts a visit to a class declaration in the visible part of [editor]; null if there is none. */
        fun start(editor: Editor, ch: CharacterSprites?, onEnd: () -> Unit): EditorVisit? {
            val visible = editor.scrollingModel.visibleArea
            if (visible.width <= 0 || visible.height <= 0) return null
            val doc = editor.document
            val headroom = (editor.lineHeight * 1.8).toInt()
            val first = editor.xyToLogicalPosition(Point(0, visible.y + headroom)).line
            val last = editor.xyToLogicalPosition(Point(0, visible.y + visible.height - editor.lineHeight)).line
            val candidates = mutableListOf<Pair<Int, Int>>() // line → x where the class name ends
            for (line in first.coerceAtLeast(0)..last.coerceAtMost(doc.lineCount - 1)) {
                val start = doc.getLineStartOffset(line)
                val text = doc.getText(com.intellij.openapi.util.TextRange(start, doc.getLineEndOffset(line)))
                val trimmed = text.trimStart()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("#") || trimmed.startsWith("/*")) continue
                val m = DECLARATION.find(text) ?: continue
                val nameEnd = editor.offsetToXY(start + m.groups[2]!!.range.last + 1).x
                val maxX = visible.x + visible.width - headroom
                if (nameEnd in visible.x..maxX) candidates += line to (nameEnd + editor.lineHeight / 3)
            }
            if (candidates.isEmpty()) return null
            val (line, x) = candidates.random()
            return EditorVisit(editor, ch, line, x, onEnd)
        }
    }
}
