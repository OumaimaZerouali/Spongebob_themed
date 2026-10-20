package com.github.oumaimazerouali.spongebobtheme.progress

import kotlin.random.Random

/**
 * Decides what a character is doing: running most of the time, now and then a short break
 * (idle, sit, jump, wave or an emote), then back to running.
 */
class RunnerBrain(
    private val minBreakGapMs: Long = 4_000,
    private val maxBreakGapMs: Long = 9_000,
    private val random: Random = Random.Default,
) {
    var action: Action = Action.RUN
        private set
    private var actionStart = 0L
    private var actionEnd = 0L
    private var emoteFrame = 0
    private var nextBreakAt = -1L

    /** Characters keep moving while running and jumping; everything else happens on the spot. */
    val moving: Boolean get() = action == Action.RUN || action == Action.JUMP

    fun update(now: Long, ch: CharacterSprites?, breaksEnabled: Boolean, frameMs: Long) {
        if (nextBreakAt < 0) scheduleNext(now)
        if (action != Action.RUN && now >= actionEnd) {
            action = Action.RUN
            actionStart = now
            scheduleNext(now)
        }
        if (action == Action.RUN && now >= nextBreakAt) {
            val options = ch?.actions.orEmpty()
            if (!breaksEnabled || options.isEmpty()) {
                scheduleNext(now)
            } else {
                start(pick(options), now, ch!!, frameMs)
            }
        }
    }

    /** Forces an action right now (used by the editor visit script). */
    fun start(a: Action, now: Long, ch: CharacterSprites, frameMs: Long, durationMs: Long? = null) {
        val anim = ch.anims[a] ?: ch.run
        action = if (a in ch.anims) a else Action.RUN
        actionStart = now
        emoteFrame = random.nextInt(anim.frames)
        actionEnd = now + (durationMs ?: when (a) {
            Action.IDLE -> random.nextLong(1_500, 3_000)
            Action.SIT -> random.nextLong(2_500, 4_500)
            Action.WAVE -> maxOf(1_000L, anim.frames * frameMs * 2)
            Action.JUMP -> maxOf(600L, anim.frames * frameMs)
            Action.EMOTE -> 1_800
            Action.RUN -> 0
        })
    }

    fun runNow(now: Long) {
        action = Action.RUN
        actionStart = now
        scheduleNext(now)
    }

    /** Animation and frame to draw at [now]. */
    fun frame(now: Long, ch: CharacterSprites, frameMs: Long): Pair<Anim, Int> {
        val anim = ch.anims[action] ?: ch.run
        val step = ((now - actionStart) / frameMs.coerceAtLeast(16)).toInt()
        val frame = when (action) {
            Action.EMOTE -> emoteFrame // emotes are separate poses: hold one
            Action.JUMP -> step.coerceAtMost(anim.frames - 1) // play once
            Action.IDLE, Action.SIT -> (step / 3) % anim.frames // calm poses play slower
            Action.WAVE -> (step / 2) % anim.frames
            Action.RUN -> step % anim.frames
        }
        return anim to frame
    }

    private fun scheduleNext(now: Long) {
        nextBreakAt = now + random.nextLong(minBreakGapMs, maxBreakGapMs)
    }

    private fun pick(options: List<Action>): Action {
        val weights = options.map {
            when (it) {
                Action.IDLE -> 3
                Action.SIT, Action.WAVE, Action.JUMP, Action.EMOTE -> 2
                Action.RUN -> 0
            }
        }
        var r = random.nextInt(weights.sum())
        for ((i, wgt) in weights.withIndex()) {
            if (r < wgt) return options[i]
            r -= wgt
        }
        return options.first()
    }
}
