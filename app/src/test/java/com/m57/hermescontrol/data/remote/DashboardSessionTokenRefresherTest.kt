package com.m57.hermescontrol.data.remote

import com.m57.hermescontrol.data.local.AuthManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.coroutines.CoroutineContext

class DashboardSessionTokenRefresherTest {
    // The refresher runs on Ktor now; a probe-shaped client is enough for the
    // extraction and cancellation paths these tests cover.
    private fun ktClient() = createHermesHttpClient(HermesHttpConfig.Probe)

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
        unmockkAll()
    }

    @Test
    fun fetchExtractsInjectedDashboardToken() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""<script>window.__HERMES_SESSION_TOKEN__ = "new-token";</script>"""),
        )

        val token = DashboardSessionTokenRefresher.fetch(server.url("/").toString(), ktClient())

        assertEquals("new-token", token)
        assertEquals("/", server.takeRequest().path)
    }

    @Test
    fun fetchDoesNotSwallowCancellation() {
        val cancelledDispatcher =
            object : CoroutineDispatcher() {
                override fun dispatch(
                    context: CoroutineContext,
                    block: Runnable,
                ) {
                    throw CancellationException("cancelled")
                }
            }

        assertThrows(CancellationException::class.java) {
            DashboardSessionTokenRefresher.fetch(
                server.url("/").toString(),
                ktClient(),
                cancelledDispatcher,
            )
        }
    }

    @Test
    fun fetchReturnsNullWhenDashboardDoesNotInjectToken() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html></html>"))

        val token = DashboardSessionTokenRefresher.fetch(server.url("/").toString(), ktClient())

        assertNull(token)
    }

    @Test
    fun fetchReturnsNullForFailedResponse() {
        server.enqueue(MockResponse().setResponseCode(500))

        val token = DashboardSessionTokenRefresher.fetch(server.url("/").toString(), ktClient())

        assertNull(token)
    }

    @Test
    fun fetchReturnsNullWhenTokenIsBlank() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""<script>window.__HERMES_SESSION_TOKEN__ = "";</script>"""),
        )

        val token = DashboardSessionTokenRefresher.fetch(server.url("/").toString(), ktClient())

        assertNull(token)
    }

    @Test
    fun refreshUpdatesAuthManagerOnSuccess() {
        mockkObject(AuthManager)
        val boundary =
            AuthManager.CredentialBoundary(
                profileId = "profile-a",
                profileBacked = true,
                endpoint = ServerEndpoint.parseForBuild(server.url("/").toString()),
                gated = false,
                token = "old-token",
            )
        every { AuthManager.credentialBoundary() } returns boundary
        every { AuthManager.commitRefreshedToken(boundary, any()) } returns true

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""<script>window.__HERMES_SESSION_TOKEN__ = "mocked-token";</script>"""),
        )

        val token = DashboardSessionTokenRefresher.refresh()

        assertEquals("mocked-token", token)
        verify { AuthManager.commitRefreshedToken(boundary, "mocked-token") }
    }

    @Test
    fun refreshReturnsNullOnException() {
        mockkObject(AuthManager)
        every { AuthManager.credentialBoundary() } throws RuntimeException("Network Error")

        val token = DashboardSessionTokenRefresher.refresh()

        assertNull(token)
    }

    @Test
    fun refreshReturnsNullWhenFetchReturnsNull() {
        mockkObject(AuthManager)
        every { AuthManager.credentialBoundary() } returns
            AuthManager.CredentialBoundary(
                profileId = "profile-a",
                profileBacked = true,
                endpoint = ServerEndpoint.parseForBuild(server.url("/").toString()),
                gated = false,
                token = "old-token",
            )
        // No token returned from fetch
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html></html>"))

        val token = DashboardSessionTokenRefresher.refresh()

        assertNull(token)
        verify(exactly = 0) { AuthManager.commitRefreshedToken(any(), any()) }
    }
}
