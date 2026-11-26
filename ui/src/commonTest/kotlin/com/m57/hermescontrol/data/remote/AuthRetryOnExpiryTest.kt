package com.m57.hermescontrol.data.remote

import com.m57.hermescontrol.data.local.AuthSessionState
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The 401 hook is the difference between "the token rotated" and "you have been signed out".
 *
 * These tests drive `safeApiCall` itself rather than a copy of its logic, because the failure
 * mode here is a missing branch in a function every request goes through: nothing calls the
 * renewal, and the symptom is a user ejected mid-session.
 */
class AuthRetryOnExpiryTest {
    @AfterTest
    fun tearDown() {
        AuthRenewal.resetForTest()
        AuthSessionState.resetForTest()
    }

    private fun unauthorised() = GatewayResponse<String>(401, null, "expired")

    private fun ok(body: String) = GatewayResponse(200, body, null)

    private fun assertExpired(result: NetworkResult<String>) {
        val failure = assertIs<NetworkResult.Failure>(result)
        assertIs<NetworkError.AuthExpired>(failure.error)
    }

    @Test
    fun aRenewedCredentialGetsOneMoreAttempt() =
        runTest {
            var renewals = 0
            var calls = 0
            AuthRenewal.renew = {
                renewals += 1
                true
            }

            val result =
                safeApiCall<String> {
                    calls += 1
                    if (calls == 1) unauthorised() else ok("recovered")
                }

            assertEquals("recovered", assertIs<NetworkResult.Success<String>>(result).data)
            assertEquals(1, renewals, "one rotation")
            assertEquals(2, calls, "the call is retried, not repeated forever")
            assertFalse(AuthSessionState.signInRequired.value, "a recovered call must not sign the user out")
        }

    /** A password or cookie session installs no renewal; expiry must behave exactly as before. */
    @Test
    fun withoutARenewalExpiryStillSignsTheUserOut() =
        runTest {
            AuthRenewal.resetForTest()
            var calls = 0

            val result =
                safeApiCall<String> {
                    calls += 1
                    unauthorised()
                }

            assertExpired(result)
            assertEquals(1, calls)
            assertTrue(AuthSessionState.signInRequired.value)
        }

    /**
     * If the gateway rejects the renewed credential too, the session really is over. Renewing
     * again would rotate the token over and over while every attempt fails.
     */
    @Test
    fun aSecondRejectionEndsTheSessionWithoutRenewingAgain() =
        runTest {
            var renewals = 0
            AuthRenewal.renew = {
                renewals += 1
                true
            }

            val result = safeApiCall<String> { unauthorised() }

            assertExpired(result)
            assertEquals(1, renewals, "exactly one rotation, never a loop")
            assertTrue(AuthSessionState.signInRequired.value)
        }

    /** A renewal that cannot produce a token must not swallow the expiry. */
    @Test
    fun aFailedRenewalFallsBackToSigningTheUserOut() =
        runTest {
            var renewals = 0
            AuthRenewal.renew = {
                renewals += 1
                false
            }

            val result = safeApiCall<String> { unauthorised() }

            assertExpired(result)
            assertEquals(1, renewals)
            assertTrue(AuthSessionState.signInRequired.value)
        }

    /** A 403 is a permission problem, not an expiry: the renewal must not fire. */
    @Test
    fun aForbiddenResponseDoesNotTriggerARenewal() =
        runTest {
            var renewals = 0
            AuthRenewal.renew = {
                renewals += 1
                true
            }

            val result = safeApiCall<String> { GatewayResponse(403, null, "forbidden") }

            assertIs<NetworkResult.Failure>(result)
            assertEquals(0, renewals)
        }
}
