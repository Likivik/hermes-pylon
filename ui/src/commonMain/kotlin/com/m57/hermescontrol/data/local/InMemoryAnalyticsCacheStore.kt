package com.m57.hermescontrol.data.local

import com.m57.hermescontrol.data.model.AnalyticsResponse
import com.m57.hermescontrol.data.model.ModelsAnalyticsResponse

/**
 * In-memory analytics cache for the desktop and web targets.
 *
 * Nothing is persisted: the cached windows live for the lifetime of the
 * process. That keeps the analytics screen usable on those targets without
 * pretending a disk cache exists.
 */
class InMemoryAnalyticsCacheStore : AnalyticsCacheStore {
    private val windows = mutableMapOf<String, Pair<AnalyticsResponse, ModelsAnalyticsResponse?>>()

    override suspend fun load(
        days: Int,
        profile: String?,
    ): Pair<AnalyticsResponse, ModelsAnalyticsResponse?>? = windows[key(days, profile)]

    override suspend fun save(
        days: Int,
        profile: String?,
        usage: AnalyticsResponse,
        models: ModelsAnalyticsResponse?,
    ) {
        windows[key(days, profile)] = usage to models
    }

    /** Stable cache key covering both window and profile. */
    private fun key(
        days: Int,
        profile: String?,
    ): String = "$days:${profile ?: DEFAULT_PROFILE}"
}

private const val DEFAULT_PROFILE = "default"
