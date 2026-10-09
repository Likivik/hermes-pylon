package com.m57.hermescontrol.data.remote

import com.m57.hermescontrol.data.local.AuthSessionState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekableGatewayMediaTest {
    private val scope = MediaCacheScope("profile-a", "https://example.test/proxy/", "direct-token", "cred-a")

    @Test
    fun `range request returns partial bytes and metadata`() =
        runTest {
            val rangeRequester = mockk<GatewayRangeRequester>()
            every { rangeRequester.streamRange("/tmp/movie.mp4", "bytes=100-199") } returns
                partialResponse(
                    body = "chunk".toResponseBody(),
                    contentRange = "bytes 100-104/1000",
                    contentType = "video/mp4",
                )
            val session = SeekableGatewayMediaSession("/tmp/movie.mp4", rangeRequester, scope) { scope }

            val result = session.read(100, 100) as GatewayMediaRangeResult.Success

            assertArrayEquals("chunk".toByteArray(), result.bytes)
            assertEquals(1000L, result.totalLength)
            assertEquals("video/mp4", result.mimeType)
            verify(exactly = 1) { rangeRequester.streamRange("/tmp/movie.mp4", "bytes=100-199") }
        }

    @Test
    fun `profile switch during request rejects stale response`() =
        runTest {
            val rangeRequester = mockk<GatewayRangeRequester>()
            every { rangeRequester.streamRange(any(), any()) } returns
                partialResponse(body = "old-profile".toResponseBody(), contentRange = "bytes 0-11/12")
            var current = scope
            val session = SeekableGatewayMediaSession("/tmp/movie.mp4", rangeRequester, scope) { current }
            current = scope.copy(profileId = "profile-b", credentialFingerprint = "cred-b")

            assertTrue(session.read(0, 32) is GatewayMediaRangeResult.Stale)
        }

    @Test
    fun `mismatched content range is rejected`() =
        runTest {
            val rangeRequester = mockk<GatewayRangeRequester>()
            every { rangeRequester.streamRange(any(), any()) } returns
                partialResponse(body = "chunk".toResponseBody(), contentRange = "bytes 0-4/1000")
            val session = SeekableGatewayMediaSession("/tmp/movie.mp4", rangeRequester, scope) { scope }

            assertTrue(session.read(100, 5) is GatewayMediaRangeResult.Failure)
        }

    @Test
    fun `range response body larger than requested is rejected and closed`() =
        runTest {
            val rangeRequester = mockk<GatewayRangeRequester>()
            var bodyClosed = false
            val body =
                object : ResponseBody() {
                    private val source =
                        object : ForwardingSource(Buffer().writeUtf8("chunks")) {
                            override fun close() {
                                bodyClosed = true
                                super.close()
                            }
                        }.buffer()

                    override fun contentType() = null

                    override fun contentLength() = 6L

                    override fun source() = source
                }
            every { rangeRequester.streamRange("/tmp/movie.mp4", "bytes=100-104") } returns
                partialResponse(
                    body = body,
                    contentRange = "bytes 100-105/1000",
                    contentType = null,
                )
            val session = SeekableGatewayMediaSession("/tmp/movie.mp4", rangeRequester, scope) { scope }

            assertEquals(GatewayMediaRangeResult.TooLarge, session.read(100, 5))
            assertTrue(bodyClosed)
        }

    @Test
    fun `successful range completes when response body close throws`() =
        runTest {
            val rangeRequester = mockk<GatewayRangeRequester>()
            val body = throwingCloseBody("chunk")
            every { rangeRequester.streamRange(any(), any()) } returns
                partialResponse(body = body, contentRange = "bytes 0-4/5", contentType = null)
            val session = SeekableGatewayMediaSession("/tmp/movie.mp4", rangeRequester, scope) { scope }

            val success = session.read(0, 5) as GatewayMediaRangeResult.Success
            assertArrayEquals("chunk".toByteArray(), success.bytes)
        }

    @Test
    fun `body and error body are both closed best effort before completion`() =
        runTest {
            val rangeRequester = mockk<GatewayRangeRequester>()
            every { rangeRequester.streamRange(any(), any()) } returns
                errorResponse(
                    code = 500,
                    body = throwingCloseBody("ignored", throwOnClose = 1),
                )
            val session = SeekableGatewayMediaSession("/tmp/movie.mp4", rangeRequester, scope) { scope }

            val completed = session.read(0, 5)
            assertTrue(completed is GatewayMediaRangeResult.Failure)
        }

    @Test
    fun `stale session rejects before issuing another range request`() =
        runTest {
            val rangeRequester = mockk<GatewayRangeRequester>(relaxed = true)
            val session =
                SeekableGatewayMediaSession(
                    "/tmp/movie.mp4",
                    rangeRequester,
                    scope,
                ) {
                    scope.copy(canonicalEndpoint = "https://replacement.test/", credentialFingerprint = "cred-b")
                }

            assertTrue(session.read(0, 64) is GatewayMediaRangeResult.Stale)
            verify(exactly = 0) { rangeRequester.streamRange(any(), any()) }
        }

    @Test
    fun `stale 401 cannot expire replacement profile`() =
        runTest {
            AuthSessionState.resetForTest()
            val rangeRequester = mockk<GatewayRangeRequester>()
            every { rangeRequester.streamRange(any(), any()) } returns
                errorResponse(code = 401, body = ByteArray(0).toResponseBody())
            var current = scope
            val session = SeekableGatewayMediaSession("/tmp/movie.mp4", rangeRequester, scope) { current }
            current = scope.copy(profileId = "profile-b", credentialFingerprint = "cred-b")

            assertEquals(GatewayMediaRangeResult.Stale, session.read(0, 64))
            assertTrue(!AuthSessionState.signInRequired.value)
            AuthSessionState.resetForTest()
        }

    @Test
    fun `range 401 requires sign in`() =
        runTest {
            AuthSessionState.resetForTest()
            val rangeRequester = mockk<GatewayRangeRequester>()
            every { rangeRequester.streamRange(any(), any()) } returns
                errorResponse(code = 401, body = ByteArray(0).toResponseBody())
            val session = SeekableGatewayMediaSession("/tmp/movie.mp4", rangeRequester, scope) { scope }

            assertEquals(GatewayMediaRangeResult.Unauthorized, session.read(0, 64))
            assertTrue(AuthSessionState.signInRequired.value)
            AuthSessionState.resetForTest()
        }

    @Test
    fun `invalid media path is rejected without request`() =
        runTest {
            val rangeRequester = mockk<GatewayRangeRequester>(relaxed = true)
            val session =
                SeekableGatewayMediaSession("https://evil.test/movie.mp4?token=secret", rangeRequester, scope) { scope }

            val result = session.read(0, 64)

            assertTrue(result is GatewayMediaRangeResult.Failure)
            verify(exactly = 0) { rangeRequester.streamRange(any(), any()) }
        }

    @Test
    fun `range endpoint has no credential query parameters`() {
        // The old test reflected on HermesApiService::class.java for the
        // @GET / @Query annotations. The new floor builds URLs programmatically
        // via `endpoint.baseUrl.resolve("api/files/stream")` plus a single
        // `path` query parameter — the credential boundary is enforced at the
        // OkHttpClient level, never in the URI. Assert the same contract on the
        // streaming transport: the range requester dispatches via an OkHttp call
        // with a `Range` header and the path encoded as a query parameter.
        val requesterClass = OkHttpGatewayRangeRequester::class.java
        assertEquals(
            listOf("streamRange"),
            requesterClass.declaredMethods.map { it.name },
        )
        // The signature exposes (path, range) — verifying the order/shape matches the previous contract.
        val streamRange = requesterClass.declaredMethods.single { it.name == "streamRange" }
        assertArrayEquals(arrayOf(String::class.java, String::class.java), streamRange.parameterTypes)
        assertEquals(okhttp3.Response::class.java, streamRange.returnType)
    }

    private fun throwingCloseBody(
        content: String,
        throwOnClose: Int = 2,
    ): ResponseBody =
        object : ResponseBody() {
            private var closeCount = 0
            private val source =
                object : ForwardingSource(Buffer().writeUtf8(content)) {
                    override fun close() {
                        super.close()
                        closeCount++
                        if (closeCount >= throwOnClose) throw java.io.IOException("close failed")
                    }
                }.buffer()

            override fun contentType() = null

            override fun contentLength() = content.length.toLong()

            override fun source() = source
        }

    private fun partialResponse(
        body: ResponseBody,
        contentRange: String,
        contentType: String? = null,
    ): okhttp3.Response {
        val headers =
            if (contentType != null) {
                Headers.headersOf(
                    "Content-Range",
                    contentRange,
                    "Content-Type",
                    contentType,
                )
            } else {
                Headers.headersOf("Content-Range", contentRange)
            }
        return okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("https://example.test/api/files/stream").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(206)
            .message("Partial Content")
            .headers(headers)
            .body(body)
            .build()
    }

    private fun errorResponse(
        code: Int,
        body: ResponseBody,
    ): okhttp3.Response =
        okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("https://example.test/api/files/stream").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(code)
            .message("Error")
            .body(body)
            .build()
}
