package com.m57.hermescontrol.ui.chat

/**
 * What a rename request actually asks the gateway for.
 *
 * The dialog lets the user edit the icon without touching the title, and it sends both every time. An empty
 * or blank title therefore means "icon only" — not "rename to nothing" — and must not cost a server round
 * trip: a `session.title` with an empty value is a request the gateway has no sensible answer for.
 *
 * A real title is trimmed. The gateway stores what it is given, so trailing whitespace from a mobile keyboard
 * would otherwise end up in the session list.
 */
sealed interface RenamePlan {
    /** The title box was left alone: apply the icon and stop. */
    data object IconOnly : RenamePlan

    data class Title(val title: String) : RenamePlan
}

fun planRename(newTitle: String?): RenamePlan {
    val trimmed = newTitle?.trim()
    return if (trimmed.isNullOrEmpty()) RenamePlan.IconOnly else RenamePlan.Title(trimmed)
}
