package com.m57.hermescontrol.ui.chat.rail

import org.kodein.emoji.*

/**
 * Char → Kodein Emoji bridge for the rail's animated icons.
 * Built once (lazy) from the full emoji catalog; lookup is a hash map.
 * Mirrors EmojiPicker.kt's catalog access pattern exactly.
 */
private val emojiByString: Map<String, Emoji> by lazy {
    val map = mutableMapOf<String, Emoji>()
    for (group in Emoji.allGroups().toList()) {
        for (emoji in Emoji.allOf(group).toList()) {
            map.putIfAbsent(emoji.details.string, emoji)
        }
    }
    map
}

internal fun emojiFromChar(str: String): Emoji? = emojiByString[str]
