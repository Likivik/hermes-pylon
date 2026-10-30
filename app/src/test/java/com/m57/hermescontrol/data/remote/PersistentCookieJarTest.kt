package com.m57.hermescontrol.data.remote

import io.ktor.http.Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PersistentCookieJar] — the issue #470 cookie stack.
 *
 * Uses [FakeEncryptedCookieStore] (no Android deps) and exercises:
 * - Set-Cookie capture + re-attachment on matching requests
 * - host/path/session-cookie matching rules
 * - atomic server-scope switching (multi-server isolation)
 * - async persistence round-trip through the fake store
 * - pruning of expired (but not session) cookies
 */
class PersistentCookieJarTest {
    private fun makeJar(): PersistentCookieJar =
        PersistentCookieJar(
            store = FakeEncryptedCookieStore(),
            storeScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )

    private fun sessionCookie(
        host: String,
        value: String,
        name: String = SESSION_COOKIE_NAME,
    ): CookieHolder =
        CookieHolder(
            name = name,
            value = value,
            expiresAt = System.currentTimeMillis() + 10L * 365 * 24 * 60 * 60 * 1000,
            domain = host,
            path = "/",
            secure = false,
            httpOnly = true,
            hostOnly = true,
        )

    @Test
    fun saveFromResponse_thenLoadForRequest_attachesCookie() =
        runTest {
            val jar = makeJar()
            jar.useStore("server-a")
            val url = Url("http://dashboard.local/")
            jar.saveFromResponse(url, listOf(sessionCookie("dashboard.local", "abc123")))

            val loaded = jar.loadForRequest(Url("http://dashboard.local/api/status"))
            assertEquals(1, loaded.size)
            assertEquals("abc123", loaded[0].value)
            assertEquals(SESSION_COOKIE_NAME, loaded[0].name)
        }

    @Test
    fun cookie_doesNotLeakToOtherHost() =
        runTest {
            val jar = makeJar()
            jar.useStore("server-a")
            jar.saveFromResponse(
                Url("http://dashboard.local/"),
                listOf(sessionCookie("dashboard.local", "abc123")),
            )

            val loaded = jar.loadForRequest(Url("http://evil.local/"))
            assertTrue(loaded.isEmpty())
        }

    @Test
    fun useStore_isolatesServers() =
        runTest {
            val jar = makeJar()
            jar.useStore("server-a")
            jar.saveFromResponse(
                Url("http://a.local/"),
                listOf(sessionCookie("a.local", "cookie-a")),
            )
            jar.useStore("server-b")
            jar.saveFromResponse(
                Url("http://b.local/"),
                listOf(sessionCookie("b.local", "cookie-b")),
            )

            // Active scope is server-b now — only b's cookie should load.
            val fromB = jar.loadForRequest(Url("http://b.local/x"))
            assertEquals("cookie-b", fromB.firstOrNull()?.value)

            // Switch back to server-a.
            jar.useStore("server-a")
            val fromA = jar.loadForRequest(Url("http://a.local/x"))
            assertEquals("cookie-a", fromA.firstOrNull()?.value)
        }

    @Test
    fun persistence_roundTripThroughStore() =
        runTest {
            val store = FakeEncryptedCookieStore()
            val jar =
                PersistentCookieJar(
                    store = store,
                    storeScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
                )
            jar.useStore("persist-scope")
            jar.saveFromResponse(
                Url("http://p.local/"),
                listOf(sessionCookie("p.local", "persisted-value")),
            )

            // The fake store should now hold the serialized cookie.
            assertTrue(store.storedHolders("persist-scope").any { it.value == "persisted-value" })

            // A fresh jar sharing the same store must reload it.
            val jar2 =
                PersistentCookieJar(
                    store = store,
                    storeScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
                )
            jar2.useStore("persist-scope")
            val reloaded = jar2.loadForRequest(Url("http://p.local/"))
            assertEquals("persisted-value", reloaded.firstOrNull()?.value)
        }

    @Test
    fun pruneServerCache_removesExpiredKeepsSession() =
        runTest {
            val jar = makeJar()
            jar.useStore("prune-scope")
            val url = Url("http://p.local/")
            val expired =
                CookieHolder(
                    name = "expired_cookie",
                    value = "x",
                    expiresAt = System.currentTimeMillis() - 1000,
                    domain = "p.local",
                    path = "/",
                    secure = false,
                    httpOnly = false,
                    hostOnly = true,
                )
            jar.saveFromResponse(url, listOf(expired, sessionCookie("p.local", "live")))

            jar.pruneServerCache()

            val remaining = jar.loadForRequest(url)
            assertEquals(1, remaining.size)
            assertEquals(SESSION_COOKIE_NAME, remaining[0].name)
        }

    @Test
    fun saveReplacesCookieWithSameNameAndPath() =
        runTest {
            val jar = makeJar()
            jar.useStore("dup-scope")
            val url = Url("http://d.local/")
            jar.saveFromResponse(url, listOf(sessionCookie("d.local", "v1")))
            jar.saveFromResponse(url, listOf(sessionCookie("d.local", "v2")))

            val loaded = jar.loadForRequest(url)
            assertEquals(1, loaded.size)
            assertEquals("v2", loaded[0].value)
        }

    @Test
    fun clearActive_dropsScopeCookies() =
        runTest {
            val jar = makeJar()
            jar.useStore("clear-scope")
            jar.saveFromResponse(
                Url("http://c.local/"),
                listOf(sessionCookie("c.local", "bye")),
            )
            jar.clearActive()

            assertTrue(jar.loadForRequest(Url("http://c.local/")).isEmpty())
        }

    @Test
    fun clearActiveCannotBeOvertakenByEarlierSave() =
        runTest {
            val saveStarted = CompletableDeferred<Unit>()
            val releaseSave = CompletableDeferred<Unit>()
            val clearFinished = CompletableDeferred<Unit>()
            val store =
                object : CookieStore {
                    val values = mutableMapOf<String, List<CookieHolder>>()

                    override suspend fun save(
                        serverId: String,
                        cookies: List<CookieHolder>,
                    ) {
                        saveStarted.complete(Unit)
                        releaseSave.await()
                        values[serverId] = cookies
                    }

                    override suspend fun load(serverId: String): List<CookieHolder> = values[serverId].orEmpty()

                    override suspend fun clear(serverId: String) {
                        values.remove(serverId)
                        clearFinished.complete(Unit)
                    }

                    override suspend fun clearAll() {
                        values.clear()
                    }
                }
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
            val jar = PersistentCookieJar(store = store, storeScope = scope)
            jar.useStore("ordered-scope")
            jar.saveFromResponse(
                Url("http://ordered.local/"),
                listOf(sessionCookie("ordered.local", "old")),
            )
            saveStarted.await()

            jar.clearActive()
            releaseSave.complete(Unit)
            clearFinished.await()

            assertTrue(store.values.isEmpty())
        }

    @Test
    fun clearActiveCannotReloadPersistedCookieBeforeAsyncDelete() =
        runTest {
            val clearStarted = CompletableDeferred<Unit>()
            val releaseClear = CompletableDeferred<Unit>()
            val persisted = sessionCookie("clear.local", "old")
            val store =
                object : CookieStore {
                    override suspend fun save(
                        serverId: String,
                        cookies: List<CookieHolder>,
                    ) = Unit

                    override suspend fun load(serverId: String): List<CookieHolder> = listOf(persisted)

                    override suspend fun clear(serverId: String) {
                        clearStarted.complete(Unit)
                        releaseClear.await()
                    }

                    override suspend fun clearAll() = Unit
                }
            val jar =
                PersistentCookieJar(
                    store = store,
                    storeScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
                )
            jar.useStore("clear-scope")
            assertEquals("old", jar.getSessionCookieValue())

            jar.clearActive()
            clearStarted.await()

            assertNull(jar.getSessionCookieValue())
            assertTrue(jar.loadForRequest(Url("http://clear.local/")).isEmpty())
            releaseClear.complete(Unit)
        }

    @Test
    fun credentialBoundaryRejectsResponseCapturedBeforeClear() =
        runTest {
            val jar = makeJar()
            jar.useStore("clear-scope")
            val staleBoundary = jar.captureCredentialBoundary()

            jar.clearActive()

            assertTrue(!jar.acceptsCredentialBoundary(staleBoundary))
            assertTrue(!jar.captureCredentialBoundary().acceptsCookies)
        }

    @Test
    fun explicitAuthenticationCreatesFreshAcceptedBoundary() =
        runTest {
            val jar = makeJar()
            jar.useStore("clear-scope")
            val oldBoundary = jar.captureCredentialBoundary()
            jar.clearActive()

            jar.beginAuthentication()
            val loginBoundary = jar.captureCredentialBoundary()

            assertTrue(!jar.acceptsCredentialBoundary(oldBoundary))
            assertTrue(jar.acceptsCredentialBoundary(loginBoundary))
            assertTrue(loginBoundary.generation > oldBoundary.generation)
        }

    @Test
    fun expiredSessionCookieDeletesStoredCredential() =
        runTest {
            val jar = makeJar()
            val url = Url("https://dashboard.local/")
            jar.useStore("server-a")
            jar.saveFromResponse(url, listOf(sessionCookie("dashboard.local", "active")))
            val deletion =
                CookieHolder(
                    name = SESSION_COOKIE_NAME,
                    value = "",
                    expiresAt = 0,
                    domain = "dashboard.local",
                    path = "/",
                    secure = false,
                    httpOnly = false,
                    hostOnly = true,
                )

            jar.saveFromResponse(url, listOf(deletion))

            assertNull(jar.getSessionCookieValue())
            assertTrue(jar.loadForRequest(url).isEmpty())
        }

    @Test
    fun sameNameAndPathCookiesRetainDistinctDomainScopes() =
        runTest {
            val store = FakeEncryptedCookieStore()
            val jar =
                PersistentCookieJar(
                    store = store,
                    storeScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
                )
            val origin = Url("https://auth.example.com/")
            jar.useStore("server-a")
            val hostOnly =
                CookieHolder(
                    name = SESSION_COOKIE_NAME,
                    value = "host-only",
                    expiresAt = COOKIE_MAX_DATE_MILLIS,
                    domain = "auth.example.com",
                    path = "/",
                    secure = false,
                    httpOnly = false,
                    hostOnly = true,
                )
            val parentDomain =
                CookieHolder(
                    name = SESSION_COOKIE_NAME,
                    value = "parent-domain",
                    expiresAt = COOKIE_MAX_DATE_MILLIS,
                    domain = "example.com",
                    path = "/",
                    secure = false,
                    httpOnly = false,
                    hostOnly = false,
                )

            jar.saveFromResponse(origin, listOf(hostOnly, parentDomain))

            assertEquals(
                setOf("host-only", "parent-domain"),
                jar.loadForRequest(origin).map { it.value }.toSet(),
            )
            assertEquals(
                listOf("parent-domain"),
                jar.loadForRequest(Url("https://other.example.com/")).map { it.value },
            )

            val reloaded =
                PersistentCookieJar(
                    store = store,
                    storeScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
                )
            reloaded.useStore("server-a")
            assertEquals(
                setOf("host-only", "parent-domain"),
                reloaded.loadForRequest(origin).map { it.value }.toSet(),
            )
        }

    @Test
    fun legacyDomainlessSessionCookieMatchesActiveServerRequests() =
        runTest {
            val jar = makeJar()
            val legacy = requireNotNull(wrapSessionCookie("legacy-value"))
            val origin = Url("https://dashboard.example.com/")
            jar.useStore(PersistentCookieJar.DEFAULT_SERVER_ID)

            jar.saveFromResponse(origin, listOf(legacy))

            assertEquals(
                listOf("legacy-value"),
                jar.loadForRequest(origin).map { it.value },
            )
        }

    @Test
    fun staleHttpResponseCannotRestoreCookieAfterClear() {
        val server = MockWebServer()
        val responseStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val store = FakeEncryptedCookieStore()
        val jar =
            PersistentCookieJar(
                store = store,
                storeScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            )
        CookieManager.setJarForTest(jar)
        val client =
            okhttp3.OkHttpClient
                .Builder()
                .cookieJar(OkHttpCookieJarAdapter { jar })
                .addInterceptor { chain ->
                    val boundary = jar.captureCredentialBoundary()
                    val request =
                        chain.request().newBuilder().tag(CookieCredentialBoundary::class.java, boundary).build()
                    chain.proceed(request)
                }.addNetworkInterceptor { chain ->
                    responseStarted.complete(Unit)
                    kotlinx.coroutines.runBlocking { releaseResponse.await() }
                    val response = chain.proceed(chain.request())
                    val boundary = chain.request().tag(CookieCredentialBoundary::class.java)
                    if (boundary != null) {
                        jar.saveFromResponse(
                            boundary,
                            Url(chain.request().url.toString()),
                            parseSetCookies(
                                Url(chain.request().url.toString()),
                                response.headers
                                    .toMultimap()
                                    .flatMap { (name, values) -> values.map { name to it } }
                                    .toSet(),
                            ),
                        )
                    }
                    response.newBuilder().removeHeader("Set-Cookie").build()
                }.build()
        server.enqueue(
            MockResponse()
                .setHeader("Set-Cookie", "hermes_session_at=stale; Path=/; HttpOnly")
                .setBody("ok"),
        )
        server.start()
        val call =
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                client.newCall(okhttp3.Request.Builder().url(server.url("/")).build()).execute().use { response ->
                    response.body.string()
                }
            }
        kotlinx.coroutines.runBlocking {
            responseStarted.await()
            jar.clearActive()
            jar.beginAuthentication()
            releaseResponse.complete(Unit)
            call.join()
        }

        assertNull(jar.getSessionCookieValue())
        server.shutdown()
    }

    @Test
    fun getSessionCookieValue_acceptsServerCookieNameVariants() =
        runTest {
            val variants =
                listOf(
                    SESSION_COOKIE_NAME,
                    "__Host-$SESSION_COOKIE_NAME",
                    "__Secure-$SESSION_COOKIE_NAME",
                )

            variants.forEachIndexed { index, name ->
                val jar = makeJar()
                val serverId = "variant-$index"
                val host = "variant-$index.local"
                jar.useStore(serverId)
                jar.saveFromResponse(
                    Url("https://$host/"),
                    listOf(
                        sessionCookie(
                            host = host,
                            value = "value-$index",
                            name = name,
                        ),
                    ),
                )

                assertEquals("value-$index", jar.getSessionCookieValue())
            }
        }

    @Test
    fun loadForRequest_returnsEmptyWhenNoCookies() {
        val jar = makeJar()
        assertTrue(jar.loadForRequest(Url("http://empty.local/")).isEmpty())
        assertNull(jar.getSessionCookieValue())
    }

    @Test
    fun getSessionCookieValue_findsPrefixedHostCookie() =
        runTest {
            val jar = makeJar()
            jar.useStore("prefixed-scope")
            val url = Url("https://dash.local/")
            val prefixed =
                CookieHolder(
                    name = "__Host-hermes_session_at",
                    value = "host-prefixed-value",
                    expiresAt = System.currentTimeMillis() + 10L * 365 * 24 * 60 * 60 * 1000,
                    domain = "dash.local",
                    path = "/",
                    secure = true,
                    httpOnly = true,
                    hostOnly = true,
                )
            jar.saveFromResponse(url, listOf(prefixed))

            assertEquals("host-prefixed-value", jar.getSessionCookieValue())
        }

    @Test
    fun getSessionCookieValue_prefersStrictestVariant() =
        runTest {
            val jar = makeJar()
            jar.useStore("multi-scope")
            val url = Url("https://dash.local/")
            // Both a bare and a __Secure- prefixed cookie present; the strictest
            // (__Host- / __Secure- first in SESSION_COOKIE_NAMES) must win.
            val bare =
                CookieHolder(
                    name = SESSION_COOKIE_NAME,
                    value = "bare-value",
                    expiresAt = System.currentTimeMillis() + 10L * 365 * 24 * 60 * 60 * 1000,
                    domain = "dash.local",
                    path = "/",
                    secure = false,
                    httpOnly = true,
                    hostOnly = true,
                )
            val secure =
                CookieHolder(
                    name = "__Secure-hermes_session_at",
                    value = "secure-value",
                    expiresAt = System.currentTimeMillis() + 10L * 365 * 24 * 60 * 60 * 1000,
                    domain = "dash.local",
                    path = "/",
                    secure = true,
                    httpOnly = true,
                    hostOnly = true,
                )
            jar.saveFromResponse(url, listOf(bare, secure))

            assertEquals("secure-value", jar.getSessionCookieValue())
        }
}
