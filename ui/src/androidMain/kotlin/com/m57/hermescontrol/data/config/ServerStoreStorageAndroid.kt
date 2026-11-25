package com.m57.hermescontrol.data.config

import android.content.Context
import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioStorage
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/** The same file the app has always written: `<filesDir>/server_store.json`. */
internal fun serverStoreStorage(context: Context): Storage<ServerStoreState> =
    OkioStorage(
        fileSystem = FileSystem.SYSTEM,
        serializer = ServerStoreSerializer,
        producePath = { context.filesDir.resolve(SERVER_STORE_FILE).toOkioPath() },
    )

internal const val SERVER_STORE_FILE = "server_store.json"
