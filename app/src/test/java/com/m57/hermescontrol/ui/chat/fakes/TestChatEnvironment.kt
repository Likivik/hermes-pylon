package com.m57.hermescontrol.ui.chat.fakes

import com.m57.hermescontrol.data.local.PreferencesStore
import com.m57.hermescontrol.data.local.SlashUsageStore
import com.m57.hermescontrol.data.model.Attachment
import com.m57.hermescontrol.ui.chat.AttachmentOpenOutcome
import com.m57.hermescontrol.ui.chat.AttachmentOpener
import com.m57.hermescontrol.ui.chat.ChatEnvironment
import com.m57.hermescontrol.ui.chat.ChatMessage
import com.m57.hermescontrol.ui.chat.ChatMessageStore

/**
 * Builds a [ChatEnvironment] for unit tests.
 *
 * Every field is a working in-memory implementation rather than a relaxed mock:
 * the ViewModel reads preferences during construction and can ask the opener to
 * run, and a relaxed mock returns nulls that turn into unrelated failures later.
 */
fun testChatEnvironment(
    slashUsageStore: SlashUsageStore = FakeSlashUsageStore(),
    /** Override when a test asserts what the platform opener actually did. */
    attachmentOpener: AttachmentOpener = NoopAttachmentOpener(),
): ChatEnvironment =
    ChatEnvironment(
        messageStore = ThrowingChatMessageStore(),
        slashUsageStore = slashUsageStore,
        preferences = EmptyPreferencesStore(),
        attachmentOpener = attachmentOpener,
    )

/** Tests inject a fake repository, so the store beneath it is never consulted. */
private class ThrowingChatMessageStore : ChatMessageStore {
    override fun upsertAll(
        messages: List<ChatMessage>,
        sessionId: String,
    ): Unit = error("ThrowingChatMessageStore is a placeholder; inject a fake repository instead")

    override suspend fun messagesForSession(sessionId: String): List<ChatMessage> =
        error("ThrowingChatMessageStore is a placeholder; inject a fake repository instead")

    override fun replaceMessagesForSession(
        sessionId: String,
        messages: List<ChatMessage>,
    ): Unit = error("ThrowingChatMessageStore is a placeholder; inject a fake repository instead")
}

private class EmptyPreferencesStore : PreferencesStore {
    private val strings = mutableMapOf<String, String>()
    private val sets = mutableMapOf<String, Set<String>>()

    override fun getString(key: String): String? = strings[key]

    override fun putString(
        key: String,
        value: String,
    ) {
        strings[key] = value
    }

    override fun getStringSet(key: String): Set<String> = sets[key].orEmpty()

    override fun putStringSet(
        key: String,
        value: Set<String>,
    ) {
        sets[key] = value
    }

    override fun remove(key: String) {
        strings.remove(key)
        sets.remove(key)
    }

    override fun allStrings(): Map<String, String> = strings.toMap()
}

private class NoopAttachmentOpener : AttachmentOpener {
    override suspend fun open(
        attachment: Attachment,
        onProgress: (String?) -> Unit,
    ): AttachmentOpenOutcome = AttachmentOpenOutcome.Handled
}
