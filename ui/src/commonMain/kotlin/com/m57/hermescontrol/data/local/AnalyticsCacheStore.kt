package com.m57.hermescontrol.data.local

import androidx.compose.runtime.Composable
import com.m57.hermescontrol.data.model.AnalyticsResponse
import com.m57.hermescontrol.data.model.ModelsAnalyticsResponse

/**
 * Cache for analytics responses, keyed by trailing window and profile.
 *
 * The `/api/analytics/usage` endpoint is expensive on the backend, so a cold
 * load can take tens of seconds. Persisting the last successful response means
 * a cold app launch can render *something* instantly and refresh in the
 * background instead of showing a spinner for a query we already answered.
 *
 * Keyed by trailing window (`days`) AND profile id, so switching profiles
 * never leaks one profile's data into another.
 *
 * Android persists the cache to disk; desktop and web keep it in memory for the
 * session only, which is the honest behaviour until a multiplatform storage
 * backend exists.
 */
interface AnalyticsCacheStore {
    /** Load a previously cached window, or null if absent/corrupt. */
    suspend fun load(
        days: Int,
        profile: String?,
    ): Pair<AnalyticsResponse, ModelsAnalyticsResponse?>?

    /** Persist a successful window response. Best-effort; never throws. */
    suspend fun save(
        days: Int,
        profile: String?,
        usage: AnalyticsResponse,
        models: ModelsAnalyticsResponse?,
    )
}

/** The analytics cache available in the current composition. */
@Composable
expect fun rememberAnalyticsCacheStore(): AnalyticsCacheStore
