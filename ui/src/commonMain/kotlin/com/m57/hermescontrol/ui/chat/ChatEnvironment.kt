package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.local.PreferencesStore
import com.m57.hermescontrol.data.local.SlashUsageStore

/**
 * The platform services [ChatViewModel] needs, gathered in one value so the
 * ViewModel itself can live in commonMain.
 *
 * Each field is an already-existing port: a platform `actual` wires the concrete
 * implementation at the screen boundary (see `androidChatEnvironment`), and tests
 * substitute fakes. Nothing here mentions Android, so the ViewModel no longer
 * needs `AndroidViewModel` or an `Application`.
 */
class ChatEnvironment(
    /** Where chat history is persisted. The gateway stays the source of truth. */
    val messageStore: ChatMessageStore,
    val slashUsageStore: SlashUsageStore,
    val preferences: PreferencesStore,
    val attachmentOpener: AttachmentOpener,
)
