package com.m57.hermescontrol.data.config

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.WebLocalStorage

/**
 * The browser keeps the store in `localStorage`, so profiles and the selected
 * server survive a reload. `localStorage` is capped at a few MB; this state is a
 * handful of profile records, so the cap is not a concern here.
 */
internal fun serverStoreStorage(): Storage<ServerStoreState> =
    WebLocalStorage(
        serializer = ServerStoreSerializer,
        name = SERVER_STORE_FILE,
    )

internal const val SERVER_STORE_FILE = "server_store.json"
