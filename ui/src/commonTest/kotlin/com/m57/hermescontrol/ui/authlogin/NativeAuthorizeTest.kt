package com.m57.hermescontrol.ui.authlogin

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * These assertions exist because the gateway validates the same things at the end of the
 * login, where the failure is expensive. A regression here should never reach a device.
 */
class NativeAuthorizeTest {
    private fun url(
        base: String = "http://127.0.0.1:9119",
        redirect: String = "http://127.0.0.1:4567/callback",
    ) = NativeAuthorize.url(
        gatewayBaseUrl = base,
        provider = "nous",
        redirectUri = redirect,
        codeChallenge = "challenge-value",
        state = "state-value",
    )

    @Test
    fun targetsTheNativeAuthorizeRoute() {
        assertEquals("/auth/native/authorize", Url(url()).encodedPath)
    }

    @Test
    fun carriesEveryParameterTheGatewayRequires() {
        val parsed = Url(url())

        assertEquals("nous", parsed.parameters["provider"])
        assertEquals("challenge-value", parsed.parameters["code_challenge"])
        assertEquals("S256", parsed.parameters["code_challenge_method"])
        assertEquals("http://127.0.0.1:4567/callback", parsed.parameters["redirect_uri"])
        assertEquals("state-value", parsed.parameters["state"])
    }

    /** A gateway mounted under a path prefix must keep it: the route is relative to it. */
    @Test
    fun keepsTheGatewayPathPrefix() {
        val parsed = Url(url(base = "https://example.test/hermes"))

        assertEquals("/hermes/auth/native/authorize", parsed.encodedPath)
    }

    @Test
    fun loopbackRedirectPointsAtTheListener() {
        assertEquals("http://127.0.0.1:4567/callback", NativeAuthorize.loopbackRedirect(4567))
    }

    @Test
    fun acceptsCanonicalLoopback() {
        assertNull(NativeAuthorize.redirectRejection("http://127.0.0.1:4567/callback"))
        assertNull(NativeAuthorize.redirectRejection("http://[::1]:4567/callback"))
        assertNull(NativeAuthorize.redirectRejection("http://127.0.0.1/callback"))
    }

    /**
     * `localhost` is rejected by the gateway in favour of IP literals (RFC 8252 §8.3). Spelling
     * it "helpfully" would break the flow at the very end.
     */
    @Test
    fun rejectsLocalhost() {
        assertNotNull(NativeAuthorize.redirectRejection("http://localhost:4567/callback"))
    }

    @Test
    fun rejectsNonHttpSchemes() {
        assertNotNull(NativeAuthorize.redirectRejection("pylon://callback"))
        assertNotNull(NativeAuthorize.redirectRejection("https://127.0.0.1:4567/callback"))
    }

    @Test
    fun rejectsAFragment() {
        assertNotNull(NativeAuthorize.redirectRejection("http://127.0.0.1:4567/callback#done"))
    }

    @Test
    fun rejectsUserinfo() {
        assertNotNull(NativeAuthorize.redirectRejection("http://user:pass@127.0.0.1:4567/callback"))
    }

    @Test
    fun rejectsBlankAndControlCharacters() {
        assertNotNull(NativeAuthorize.redirectRejection(""))
        assertNotNull(NativeAuthorize.redirectRejection("http://127.0.0.1:4567/call back"))
    }

    @Test
    fun rejectsANonLoopbackHost() {
        assertNotNull(NativeAuthorize.redirectRejection("http://192.168.1.5:4567/callback"))
    }
}
