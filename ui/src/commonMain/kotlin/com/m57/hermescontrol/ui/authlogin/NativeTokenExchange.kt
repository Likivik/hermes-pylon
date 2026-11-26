package com.m57.hermescontrol.ui.authlogin

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * The two token calls of the gateway's native sign-in.
 *
 * Deliberately holds its own [HttpClient] rather than the gateway client: this flow must not send
 * a session cookie, and a fresh client cannot have one. Cookies and bearer tokens are different
 * credentials; mixing them on one client is how a "logged out" app keeps authenticating.
 *
 * Status mapping follows the gateway exactly — `400` invalid code, `401 session_expired` from the
 * refresh path, `503` provider unreachable — and everything else is retryable, never fatal.
 */
class NativeTokenExchange(
    private val client: HttpClient,
) {
    /** Exchange a loopback code plus the PKCE verifier for tokens. The code is single-use. */
    suspend fun redeem(
        gatewayBaseUrl: String,
        code: String,
        codeVerifier: String,
    ): TokenResult =
        call(
            endpointUrl(gatewayBaseUrl, "auth", "native", "token"),
            buildJsonObject {
                put("code", code)
                put("code_verifier", codeVerifier)
            },
        )

    /** Rotate a refresh token. Every provider rejecting it yields [TokenResult.SessionExpired]. */
    suspend fun refresh(
        gatewayBaseUrl: String,
        refreshToken: String,
        provider: String,
    ): TokenResult =
        call(
            endpointUrl(gatewayBaseUrl, "auth", "native", "refresh"),
            buildJsonObject {
                put("refresh_token", refreshToken)
                if (provider.isNotEmpty()) put("provider", provider)
            },
        )

    private suspend fun call(
        url: String,
        body: JsonObject,
    ): TokenResult {
        val response =
            try {
                client.post(url) {
                    setBody(TextContent(body.toString(), ContentType.Application.Json))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                return TokenResult.Retryable(failure::class.simpleName ?: "transport failure")
            }

        val status = response.status.value
        val text =
            try {
                response.bodyAsText()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ""
            }

        return when (status) {
            200 -> parseToken(text)
            400 -> TokenResult.InvalidCode
            401 -> TokenResult.SessionExpired
            else -> TokenResult.Retryable("HTTP $status")
        }
    }

    private fun parseToken(text: String): TokenResult {
        val body =
            try {
                Json.parseToJsonElement(text).jsonObject
            } catch (_: Exception) {
                return TokenResult.Retryable("unreadable token body")
            }

        val access = body.stringOrNull("access_token")
        val refresh = body.stringOrNull("refresh_token")
        if (access.isNullOrEmpty() || refresh.isNullOrEmpty()) {
            return TokenResult.Retryable("token body missing access_token or refresh_token")
        }

        return TokenResult.Success(
            NativeToken(
                accessToken = access,
                refreshToken = refresh,
                expiresAtEpochSeconds = body.epochSecondsOrNull("expires_at"),
                provider = body.stringOrNull("provider").orEmpty(),
            ),
        )
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /**
     * `expires_at` is read as a number when it looks like one, and as a number written in a string
     * otherwise. Anything else is unknown, which [NativeToken.needsRefresh] treats as "do not
     * pre-emptively refresh" rather than as "expired".
     */
    private fun JsonObject.epochSecondsOrNull(key: String): Long? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.longOrNull ?: primitive.takeIf { it.isString }?.content?.toLongOrNull()
    }

    private fun endpointUrl(
        baseUrl: String,
        vararg segments: String,
    ): String =
        URLBuilder(Url(baseUrl)).apply { appendPathSegments(*segments) }.buildString()
}
