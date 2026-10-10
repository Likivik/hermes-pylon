package com.m57.hermescontrol.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpApiContractTest {
    @Test
    fun `test endpoint parses fork health response directly`() =
        runBlocking {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse().setBody(
                        """
                        {
                          "ok": true,
                          "tools": [
                            {"name": "read_file", "description": "Read a file", "schema_chars": 401}
                          ]
                        }
                        """.trimIndent(),
                    ),
                )
                val baseUrl =
                    ServerEndpoint.parse(
                        server.url("/").toString(),
                        CleartextPolicy.ALLOW_WITH_WARNING,
                    ).baseUrl
                val client = createHermesHttpClient(OkHttpClient())
                val api = HermesGatewayApi(client, baseUrl)

                val response = api.testMcpServer("local tools")

                assertTrue(response.isSuccessful)
                assertEquals(true, response.body()?.ok)
                assertEquals(401, response.body()?.tools?.single()?.schemaChars)
                assertEquals("/api/mcp/servers/local%20tools/test", server.takeRequest().path)
            }
        }
}
