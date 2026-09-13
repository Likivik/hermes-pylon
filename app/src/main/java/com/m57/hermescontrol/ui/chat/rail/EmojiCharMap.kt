package com.m57.hermescontrol.ui.chat.rail

import org.kodein.emoji.Emoji

/**
 * Char → Kodein Emoji bridge for the rail's animated icons.
 * Built once (lazy) from the full emoji catalog; lookup is a hash map.
 */
private val emojiByString: Map<String, Emoji> by lazy {
    val map = mutableMapOf<String, Emoji>()
    for (group in Emoji.allGroups()) {
        for (emoji in Emoji.allOf(group)) {
            map.putIfAbsent(emoji.details.string, emoji)
        }
    }
    map
}

internal fun emojiFromChar(str: String): Emoji? = emojiByString[str]
