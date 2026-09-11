package com.m57.hermescontrol.ui.chat.rail

import com.m57.hermescontrol.theme.HermesAvatarPalette
import com.m57.hermescontrol.ui.chat.RailMeta
import com.m57.hermescontrol.ui.chat.SessionUi
import androidx.compose.ui.graphics.Color

/** Title→emoji heuristic (auto-icon when no explicitly assigned icon). */
private val EmojiGroups = listOf<Pair<Regex, String>>(
    Regex("[аа]dhd|ади|внимание|фокус|focus") to "🧠",
    Regex("игра|рол|rp|roleplay|tavern|silly|персонаж|сцена") to "🎲",
    Regex("hermes|агент|agent|бот|bot") to "🤖",
    Regex("диза[iяй]|design|ui|ux|макет|лого") to "🎨",
    Regex("код|code|dev|разработ|программ|билд|build|github|git|репо") to "💻",
    Regex("план|plan|roadmap|дорожная|задач|todo") to "📋",
    Regex("заметк|note|идея|idea|мозг|мысл") to "💡",
    Regex("сервер|server|хост|deploy|инфра|nix") to "🛠️",
    Regex("деньг|деньги|money|бизнес|работ|ваканс|зарплат") to "💰",
    Regex("напомин|remind|позво|провер") to "⏰",
    Regex("рус|ru |язык|перевод") to "🌐",
    Regex("купл|покуп|shopp|магаз|авито|заказ") to "🛒",
    Regex("медиц|врач|health|здоров") to "🩺",
    Regex("музык|music|песн|плейлист") to "🎵",
    Regex("фото|аниме|изображ|картин|image|art|рисун") to "🖼️",
)

internal fun heuristicEmoji(title: String): String {
    val t = title.lowercase()
    for ((re, emoji) in EmojiGroups) {
        if (re.containsMatchIn(t)) return emoji
    }
    return ""
}

internal fun railColorFor(sessionId: String): Color {
    val hash = sessionId.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7FFFFFFF }
    return HermesAvatarPalette[hash % HermesAvatarPalette.size]
}

/** Resolved display data for one rail cell (computed from model, not state). */
internal data class RailItemDisplay(
    val session: SessionUi,
    val active: Boolean,
    val pinned: Boolean,
    val stale: Boolean,
    val icon: String,
    val label: String,
    val avatarIsFallbackColor: Boolean,
) {
    /** Fresh (non-pinned, non-stale) items are drag-reorderable. */
    val draggable: Boolean get() = !pinned && !stale
}

internal fun RailUiModel.displayFor(session: SessionUi): RailItemDisplay {
    val icon = railMeta[session.id]?.icon ?: heuristicEmoji(session.title)
    return RailItemDisplay(
        session = session,
        active = session.id == currentSessionId,
        pinned = session.id in pinnedSessionIds,
        stale = session.isStale,
        icon = icon,
        label = session.title.trim(),
        avatarIsFallbackColor = icon.isEmpty(),
    )
}
