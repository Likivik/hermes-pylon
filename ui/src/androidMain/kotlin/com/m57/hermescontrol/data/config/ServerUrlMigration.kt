package com.m57.hermescontrol.data.config

import androidx.datastore.core.DataMigration

/**
 * Migrates the former host-and-port model to canonical complete base URLs.
 *
 * The platform-neutral logic lives in the :shared module under
 * [migrateServerUrls]; this class is a thin Android-DataStore
 * wrapper around it. The companion keeps `migrateState(...)` available so
 * the pre-existing `ServerUrlMigrationTest` (which treats migration as a
 * pure transform) keeps working unchanged.
 */
class ServerUrlMigration : DataMigration<ServerStoreState> {
    override suspend fun shouldMigrate(currentData: ServerStoreState): Boolean =
        currentData.baseUrl.isNullOrBlank() ||
            currentData.connectionProfiles.any { it.baseUrl.isNullOrBlank() }

    override suspend fun migrate(currentData: ServerStoreState): ServerStoreState = migrateServerUrls(currentData)

    override suspend fun cleanUp() = Unit

    companion object {
        // Tested as a pure function by ServerUrlMigrationTest.
        fun migrateState(state: ServerStoreState): ServerStoreState = migrateServerUrls(state)
    }
}
