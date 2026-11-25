package com.m57.hermescontrol.data.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Design A: the profile lives in the client generation, so no call site can forget it.
 */
class ProfileScopeTest {
    private fun engine(onUrl: (String) -> Unit) =
        MockEngine { request ->
            onUrl(request.url.toString())
            respond("{}", HttpStatusCode.OK)
        }

    @Test
    fun scopedClientAppendsTheProfileToEveryRequest() =
        runTest {
            var seen: String? = null
            val client = createHermesHttpClient(engine { seen = it }, HermesHttpConfig.Default.copy(profile = "work"))

            client.get("http://gw.test/api/sessions")

            assertTrue("profile=work" in seen!!, seen!!)
        }

    @Test
    fun unscopedClientSendsNoProfile() =
        runTest {
            var seen: String? = null
            val client = createHermesHttpClient(engine { seen = it })

            client.get("http://gw.test/api/sessions")

            assertFalse("profile=" in seen!!, seen!!)
        }

    @Test
    fun blankProfileIsTreatedAsAbsent() =
        runTest {
            var seen: String? = null
            val client = createHermesHttpClient(engine { seen = it }, HermesHttpConfig.Default.copy(profile = "  "))

            client.get("http://gw.test/api/status")

            assertFalse("profile=" in seen!!, seen!!)
        }
}
