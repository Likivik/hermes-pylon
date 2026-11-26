package com.m57.hermescontrol.ui.authlogin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The gateway compares the challenge as an opaque string, so an encoding slip here fails
 * only at the very end of a real login. These cases pin the encoding to RFC 7636's own
 * example instead of to whatever this code happens to produce.
 */
class PkceTest {
    /** RFC 7636 Appendix B: the one published verifier/challenge pair. */
    @Test
    fun matchesTheRfc7636ExampleVector() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challengeFor(verifier))
    }

    @Test
    fun verifierIsTheRfcMinimumLength() {
        assertEquals(43, Pkce.newVerifier().length)
    }

    @Test
    fun verifierUsesOnlyTheUnreservedAlphabet() {
        val allowed = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '_', '.', '~')

        assertTrue(Pkce.newVerifier().all { it in allowed })
    }

    /**
     * A padded challenge would not match: the gateway's comparison is exact, and `=` is not
     * in the base64url unreserved set either.
     */
    @Test
    fun neitherValueCarriesBase64Padding() {
        val verifier = Pkce.newVerifier()

        assertFalse(Pkce.challengeFor(verifier).contains('='))
        assertFalse(verifier.contains('='))
        assertFalse(Pkce.newState().contains('='))
    }

    @Test
    fun twoVerifiersDiffer() {
        assertNotEquals(Pkce.newVerifier(), Pkce.newVerifier())
    }

    @Test
    fun twoStatesDiffer() {
        assertNotEquals(Pkce.newState(), Pkce.newState())
    }

    @Test
    fun challengeIsStableForTheSameVerifier() {
        val verifier = Pkce.newVerifier()

        assertEquals(Pkce.challengeFor(verifier), Pkce.challengeFor(verifier))
    }
}
