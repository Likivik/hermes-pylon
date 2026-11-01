package com.m57.hermescontrol.data.config

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioStorage
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Desktop keeps the store next to the user's config: `~/.hermes-control/server_store.json`,
 * creating the directory on first use.
 */
internal fun serverStoreStorage(): Storage<ServerStoreState> {
    val dir = File(System.getProperty("user.home"), ".hermes-control").apply { mkdirs() }
    return OkioStorage(
        fileSystem = FileSystem.SYSTEM,
        serializer = ServerStoreSerializer,
        producePath = { dir.resolve(SERVER_STORE_FILE).toOkioPath() },
    )
}

internal const val SERVER_STORE_FILE = "server_store.json"
