package com.m57.hermescontrol.data.remote

import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The retry taxonomy, locked on both platforms.
 *
 * This exists because the contract checker flagged the web `isRetryable` and I called it
 * a defect — "a 404 retries until backoff caps". That was wrong: HTTP status handling
 * happens before the exception branch, and a 4xx returns a typed failure. These tests
 * hold that reading in place so the claim cannot drift again, and so the platform split
 * (5xx/429 retried by status; IOExceptions decided by `isRetryable`) is exercised on the
 * JVM and in the browser by the same file.
 */
class SafeApiCallRetryTest {
    private fun response(code: Int) = GatewayResponse<Unit>(code, null, "status $code")

    @Test
    fun `a 404 is not retried`() =
        runTest {
            var calls = 0
            val result =
                safeApiCall<Unit>(retries = 2) {
                    calls++
                    response(404)
                }

            assertEquals(1, calls, "a 4xx must be reported, not retried")
            assertTrue(result is NetworkResult.Failure, "a 404 is a failure")
            val error = (result as NetworkResult.Failure).error
            assertTrue(error is NetworkError.Http, "expected a typed HTTP error, got $error")
            assertEquals(404, (error as NetworkError.Http).code)
        }

    @Test
    fun `a 503 is retried`() =
        runTest {
            var calls = 0
            safeApiCall<Unit>(retries = 1) {
                calls++
                response(503)
            }

            assertEquals(2, calls, "5xx is retried up to the retry budget")
        }

    @Test
    fun `a 429 is retried`() =
        runTest {
            var calls = 0
            safeApiCall<Unit>(retries = 1) {
                calls++
                response(429)
            }

            assertEquals(2, calls, "429 is retried up to the retry budget")
        }

    /**
     * Platform contract: a generic IOException is retryable on both the JVM and web.
     * The platform-specific cases (UnknownHostException, SSLException) cannot be
     * constructed in common code and stay in the JVM suite.
     */
    @Test
    fun `a generic transport failure is retried`() =
        runTest {
            var calls = 0
            val result =
                safeApiCall<Unit>(retries = 1) {
                    calls++
                    throw IOException("connection reset")
                }

            assertEquals(2, calls, "an opaque transport failure is worth one retry")
            assertTrue(result is NetworkResult.Failure, "exhausted retries are a failure")
        }
}
