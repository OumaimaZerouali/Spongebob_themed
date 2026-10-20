package com.github.oumaimazerouali.spongebobtheme.progress

import java.awt.Image

/** One animation: [frames] frames of [frameW] × [frameH] px, side by side in [image]. */
class Anim(val image: Image, val frames: Int, val frameW: Int, val frameH: Int, val animatedGif: Boolean = false)

/** What a character can do. Only [run] is required. */
enum class Action { RUN, IDLE, SIT, JUMP, WAVE, EMOTE }

/**
 * A character from a sheet. On a labelled sheet every character is a block of six groups in this order:
 * idle, run, sit, jump, wave, emote. Anything else is a run-only character.
 */
class CharacterSprites(val name: String, val anims: Map<Action, Anim>) {
    val run: Anim get() = anims.getValue(Action.RUN)
    val actions: List<Action> get() = anims.keys.filter { it != Action.RUN }

    companion object {
        /** Order of the groups for one character on a labelled sheet. */
        val SHEET_ORDER = listOf(Action.IDLE, Action.RUN, Action.SIT, Action.JUMP, Action.WAVE, Action.EMOTE)
    }
}
