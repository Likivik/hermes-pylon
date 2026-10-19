package com.m57.hermescontrol.ui.chat

import android.content.Context
import com.m57.hermescontrol.data.local.AndroidPreferencesStore
import com.m57.hermescontrol.data.local.AndroidSlashUsageStore
import com.m57.hermescontrol.data.local.HermesDatabase
import com.m57.hermescontrol.ui.common.StringResolver

/**
 * Builds the Android side of [ChatEnvironment].
 *
 * This is the only place the chat ViewModel's platform services are assembled, so
 * the ViewModel itself stays free of `Application`, Room, SharedPreferences and
 * `Context` — which is what lets it move to commonMain.
 */
fun androidChatEnvironment(
    context: Context,
    resolver: StringResolver,
): ChatEnvironment {
    val appContext = context.applicationContext ?: context
    return ChatEnvironment(
        messageStore = RoomChatMessageStore(HermesDatabase.get(appContext).chatMessageDao()),
        slashUsageStore = AndroidSlashUsageStore(appContext),
        preferences = AndroidPreferencesStore(appContext),
        attachmentOpener =
            AndroidAttachmentOpener(
                appContext,
                resolver,
                appContext.cacheDir.absolutePath,
            ),
    )
}
