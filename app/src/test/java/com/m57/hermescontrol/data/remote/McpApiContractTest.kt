package com.m57.hermescontrol.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

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
                val api =
                    Retrofit
                        .Builder()
                        .baseUrl(server.url("/"))
                        .addConverterFactory(
                            OkHttpProvider.json.asConverterFactory("application/json".toMediaType()),
                        ).build()
                        .create(HermesApiService::class.java)

                val response = api.testMcpServer("local tools")

                assertTrue(response.isSuccessful)
                assertEquals(true, response.body()?.ok)
                assertEquals(401, response.body()?.tools?.single()?.schemaChars)
                assertEquals("/api/mcp/servers/local%20tools/test", server.takeRequest().path)
            }
        }
}
