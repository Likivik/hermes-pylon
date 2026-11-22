package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.PruneRequest
import com.m57.hermescontrol.data.model.SessionListResponse
import com.m57.hermescontrol.data.model.SessionStatsResponse
import com.m57.hermescontrol.data.remote.GatewayResponse

/**
 * What the sessions screen needs from the gateway: 3 calls of the 150 it offers.
 *
 * Replaces the `SessionsGateway` wrapper added in #257. The wrapper duplicated signatures for no gain; this names
 * only what this screen calls, and `SessionsSourceOf` is the production adapter.
 */
interface SessionsSource {
    suspend fun getSessions(
        limit: Int = 20,
        offset: Int = 0,
        order: String = "recent",
        source: String? = null,
        excludeSources: String? = null,
        /**
         * Profile whose session store to list. The WS socket is already profile-scoped;
         * this route is not, so omitting it silently lists the **default** profile's
         * sessions for a user working in any other one.
         */
        profile: String? = null,
    ): GatewayResponse<SessionListResponse>

    suspend fun getSessionStats(): GatewayResponse<SessionStatsResponse>

    suspend fun pruneSessions(body: PruneRequest): GatewayResponse<Unit>
}
