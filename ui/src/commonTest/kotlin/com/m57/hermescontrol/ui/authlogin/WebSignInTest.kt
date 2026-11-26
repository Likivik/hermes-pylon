package com.m57.hermescontrol.ui.authlogin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The browser flow's only local logic is the URL, and the part that can cost a completed sign-in
 * is `next`: the gateway validates it *after* the user has authenticated.
 */
class WebSignInTest {
    private val base = "http://127.0.0.1:9119"

    @Test
    fun itTargetsTheGatewayBrowserFlow() {
        val url = WebSignIn.authorizeUrl(base)

        assertEquals("http://127.0.0.1:9119/auth/login?provider=nous&next=%2F", url)
    }

    @Test
    fun aTrailingSlashOnTheBaseDoesNotDouble() {
        assertEquals(
            "http://127.0.0.1:9119/auth/login?provider=nous&next=%2F",
            WebSignIn.authorizeUrl("http://127.0.0.1:9119/"),
        )
    }

    @Test
    fun thePostLoginPathIsPercentEncoded() {
        assertEquals(
            "http://127.0.0.1:9119/auth/login?provider=nous&next=%2Fsettings%3Ftab%3Dauth",
            WebSignIn.authorizeUrl(base, next = "/settings?tab=auth"),
        )
    }

    @Test
    fun aProviderOtherThanNousIsAllowed() {
        assertEquals(
            "http://127.0.0.1:9119/auth/login?provider=github&next=%2F",
            WebSignIn.authorizeUrl(base, provider = "github"),
        )
    }

    @Test
    fun aBlankProviderIsRejected() {
        assertFailsWith<IllegalArgumentException> { WebSignIn.authorizeUrl(base, provider = "  ") }
    }

    /** `//host` is a host, not a path: the redirect would leave the gateway's origin. */
    @Test
    fun aHostNamingPathIsRejected() {
        assertEquals("the post-login path must not name a host", WebSignIn.nextRejection("//evil.example"))
        assertFailsWith<IllegalArgumentException> { WebSignIn.authorizeUrl(base, next = "//evil.example") }
    }

    @Test
    fun anAbsoluteUrlIsNotAPath() {
        assertEquals("the post-login path must start with a slash", WebSignIn.nextRejection("https://evil.example/"))
    }

    @Test
    fun whitespaceInThePathIsRejected() {
        assertEquals("the post-login path must not contain whitespace", WebSignIn.nextRejection("/a b"))
    }

    @Test
    fun anEmptyPathIsRejected() {
        assertEquals("the post-login path is empty", WebSignIn.nextRejection(""))
    }

    @Test
    fun aPlainPathIsAccepted() {
        assertNull(WebSignIn.nextRejection("/"))
        assertNull(WebSignIn.nextRejection("/chat/42?tab=media"))
    }
}
