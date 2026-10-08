package com.m57.hermescontrol.data.config

import com.m57.hermescontrol.data.remote.CleartextPolicy
import com.m57.hermescontrol.data.remote.ServerEndpoint

/**
 * Pure-Kotlin migration logic that promotes a [ServerStoreState] from the
 * legacy host-and-port model to the canonical, complete base-URL form.
 *
 * The Android-side `DataMigration<ServerStoreState>` wrapper (which
 * implements `androidx.datastore.core.DataMigration`) lives in :app and
 * delegates here, so the business logic stays platform-neutral and can be
 * exercised from JVM-only unit tests in `:shared` if we ever add a
 * `commonTest` source set.
 *
 * Migrates the former host-and-port model to canonical complete base URLs.
 */
fun migrateServerUrls(state: ServerStoreState): ServerStoreState =
    state.copy(
        baseUrl =
            normalizeExisting(state.baseUrl)
                ?: ServerEndpoint.fromLegacy(
                    state.host,
                    state.port,
                ).baseUrl.toString(),
        connectionProfiles =
            state.connectionProfiles.map { profile ->
                profile.copy(
                    baseUrl =
                        normalizeExisting(profile.baseUrl)
                            ?: ServerEndpoint.fromLegacy(
                                profile.host,
                                profile.port,
                            ).baseUrl.toString(),
                )
            },
    )

private fun normalizeExisting(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return ServerEndpoint.parse(
        raw,
        CleartextPolicy.ALLOW_WITH_WARNING,
    ).baseUrl.toString()
}
