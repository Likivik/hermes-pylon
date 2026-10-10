package com.m57.hermescontrol.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * The single JSON codec used for every gateway payload.
 *
 * Multiplatform: the wire format is snake_case with unknown keys tolerated, so
 * the same configured [Json] serves the Android client, the console client and
 * any future target. [OkHttpProvider.json] aliases this instance so the two can
 * never drift.
 */
object HermesJson {
    @OptIn(ExperimentalSerializationApi::class)
    val instance: Json =
        Json {
            ignoreUnknownKeys = true
            namingStrategy = JsonNamingStrategy.SnakeCase
        }
}
