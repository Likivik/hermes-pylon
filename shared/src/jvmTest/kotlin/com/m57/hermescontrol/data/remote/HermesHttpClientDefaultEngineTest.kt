package com.m57.hermescontrol.data.remote

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Proves the engine-agnostic factory resolves a real engine on this target.
 *
 * `createHermesHttpClient()` builds `HttpClient { }` with no engine argument, so
 * Ktor has to find one through `META-INF/services/io.ktor.client.HttpClientEngineContainer`.
 * When none is resolvable it throws `IllegalStateException` instead of returning,
 * so constructing the client at all is the assertion; `ktor-client-engine-defaults`
 * in commonMain is what supplies the engine here.
 *
 * This overload had no callers before, which meant the default-engine path was
 * never executed by any test — only inferred from the engine artifacts on the
 * classpath.
 */
class HermesHttpClientDefaultEngineTest {
    @Test
    fun `engine-agnostic factory resolves a default engine`() {
        val client = createHermesHttpClient()
        try {
            assertNotNull(client.engine, "no engine resolved for the default-engine factory")
            assertTrue(
                client.engine.toString().isNotBlank(),
                "resolved engine has no name: ${client.engine}",
            )
        } finally {
            client.close()
        }
    }
}
