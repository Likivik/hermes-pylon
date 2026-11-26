package com.m57.hermescontrol.data.local

import com.m57.hermescontrol.ui.authlogin.NativeToken
import com.m57.hermescontrol.ui.authlogin.NativeTokenExchange
import com.m57.hermescontrol.ui.authlogin.TokenResult
import com.m57.hermescontrol.util.nowMillis
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The durable side of the native sign-in: which token the app should present right now.
 *
 * Two rules drive the design.
 *
 * *Renew near expiry, not at it.* A token that expires while a request is in flight fails that
 * request, so [refreshSkewSeconds] renews early.
 *
 * *One rotation at a time.* Several callers can want the token in the same tick. The gateway
 * rotates the refresh token on every use, so without [lock] all but one of them would end up
 * holding a dead token.
 */
class NativeSession(
    private val exchange: NativeTokenExchange,
    private val store: NativeTokenStore,
    private val refreshSkewSeconds: Long = NativeToken.DEFAULT_SKEW_SECONDS,
    private val nowEpochSeconds: () -> Long = { nowMillis() / 1000 },
) {
    private val lock = Mutex()

    /** What is stored, with no network call. Null when nothing usable is stored. */
    fun stored(): NativeToken? = store.load()

    /**
     * A token fit to send now.
     *
     * Returns the stored token when it is fresh, a renewed one when it is close to expiry, and
     * null when the session is over.
     */
    suspend fun current(gatewayBaseUrl: String): NativeToken? {
        val token = store.load() ?: return null
        if (!token.needsRefresh(nowEpochSeconds(), refreshSkewSeconds)) return token
        return rotate(force = false, gatewayBaseUrl = gatewayBaseUrl)
    }

    /**
     * Rotates regardless of the clock, for a 401 that arrived while holding a token that looked
     * fresh. Without this the app would keep presenting a token the gateway has already refused.
     */
    suspend fun renewAfterRejection(gatewayBaseUrl: String): NativeToken? =
        rotate(force = true, gatewayBaseUrl = gatewayBaseUrl)

    /** Ends the session locally. The caller is responsible for sending the user to sign in. */
    fun forget() = store.clear()

    /**
     * A retryable failure returns the held token rather than dropping it: a `503` from the
     * provider is not an expired session, and signing the user out for it throws away a working
     * credential. Only a provider-side rejection ends the session.
     */
    private suspend fun rotate(
        force: Boolean,
        gatewayBaseUrl: String,
    ): NativeToken? =
        lock.withLock {
            val held = store.load() ?: return@withLock null

            // A concurrent caller may have rotated it while this one waited for the lock. Reading
            // again here is what makes the single-flight real rather than nominal.
            if (!force && !held.needsRefresh(nowEpochSeconds(), refreshSkewSeconds)) {
                return@withLock held
            }

            when (val result = exchange.refresh(gatewayBaseUrl, held.refreshToken, held.provider)) {
                is TokenResult.Success -> {
                    store.save(result.token)
                    result.token
                }

                is TokenResult.SessionExpired -> {
                    store.clear()
                    null
                }

                is TokenResult.InvalidCode -> {
                    store.clear()
                    null
                }

                is TokenResult.Retryable -> held
            }
        }
}
