package com.m57.hermescontrol.ui.chat.fakes

import com.m57.hermescontrol.ui.chat.ChatPersistenceStore
import com.m57.hermescontrol.ui.chat.OperationRegistrationHook
import com.m57.hermescontrol.ui.chat.RoomChatMessageStore

/**
 * In-memory [ChatPersistenceStore] for tests.
 *
 * Wraps [FakeChatMessageDao] so tests can read/write messages
 * without Room or SQLCipher. Pass to [ChatViewModel]'s `repo`
 * constructor parameter in tests.
 *
 * ```
 * val fakeRepo = FakeChatPersistenceRepository()
 * fakeRepo.dao.addMessageDirect(someEntity)
 * val vm = ChatViewModel(app, resolver, startCleanup, fakeRepo)
 * ```
 */
internal open class FakeChatPersistenceRepository(
    val dao: FakeChatMessageDao = FakeChatMessageDao(),
    operationRegistrationHook: OperationRegistrationHook = OperationRegistrationHook {},
) : ChatPersistenceStore(RoomChatMessageStore(dao), operationRegistrationHook) {
    /** Clear all stored messages. */
    fun clear() = dao.clear()
}
