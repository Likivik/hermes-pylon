package com.m57.hermescontrol.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.m57.hermescontrol.data.local.InMemorySlashUsageStore
import com.m57.hermescontrol.data.local.rememberPreferencesStore

/**
 * No local database on this target yet: history lives in memory and the gateway
 * stays the source of truth. Phase 5 of the multiplatform plan adds a file-backed
 * store behind the same [ChatMessageStore] interface.
 */
@Composable
actual fun rememberChatEnvironment(): ChatEnvironment {
    val preferences = rememberPreferencesStore()
    return remember(preferences) {
        ChatEnvironment(
            messageStore = InMemoryChatMessageStore(),
            slashUsageStore = InMemorySlashUsageStore(),
            preferences = preferences,
            attachmentOpener = unsupportedAttachmentOpener,
        )
    }
}
