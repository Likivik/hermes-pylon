package com.m57.hermescontrol.ui.mcp

import com.m57.hermescontrol.data.model.McpServer
import com.m57.hermescontrol.data.model.McpServerTestResponse
import com.m57.hermescontrol.data.model.McpServerToolInfo
import com.m57.hermescontrol.data.model.McpServersResponse
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.GatewayResponse
import com.m57.hermescontrol.data.remote.HermesGatewayApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class McpServersViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var api: HermesGatewayApi

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(ApiClient)
        api = mockk()
        every { ApiClient.hermesApi } returns api
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `test all tests enabled servers and records each result`() {
        coEvery { api.getMcpServers() } returns
            GatewayResponse.success(
                McpServersResponse(
                    listOf(
                        McpServer(name = "healthy", enabled = true),
                        McpServer(name = "broken", enabled = true),
                        McpServer(name = "disabled", enabled = false),
                    ),
                ),
            )
        coEvery { api.testMcpServer("healthy") } returns
            GatewayResponse.success(
                McpServerTestResponse(
                    ok = true,
                    tools = listOf(McpServerToolInfo("read", schemaChars = 80)),
                ),
            )
        coEvery { api.testMcpServer("broken") } returns
            GatewayResponse.error(503, "unavailable")

        val viewModel = McpServersViewModel(ioDispatcher = dispatcher)
        viewModel.loadServers()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.testAllServers()
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { api.testMcpServer("healthy") }
        coVerify(exactly = 3) { api.testMcpServer("broken") }
        coVerify(exactly = 0) { api.testMcpServer("disabled") }
        val state = viewModel.uiState.value
        assertFalse(state.isTestingAll)
        assertTrue(state.testingServers.isEmpty())
        assertEquals(true, state.serverTestResults["healthy"]?.ok)
        assertEquals(false, state.serverTestResults["broken"]?.ok)
        assertEquals("Tested 2 servers: 1 passed, 1 failed", state.toastMessage)
    }

    @Test
    fun `test all bounds concurrent server tests`() {
        val servers = (1..7).map { McpServer(name = "server-$it", enabled = true) }
        coEvery { api.getMcpServers() } returns GatewayResponse.success(McpServersResponse(servers))
        val release = CompletableDeferred<Unit>()
        val inFlight = AtomicInteger()
        val maximumInFlight = AtomicInteger()
        coEvery { api.testMcpServer(any()) } coAnswers {
            val current = inFlight.incrementAndGet()
            maximumInFlight.updateAndGet { maximum -> maxOf(maximum, current) }
            release.await()
            inFlight.decrementAndGet()
            GatewayResponse.success(McpServerTestResponse(ok = true))
        }

        val viewModel = McpServersViewModel(ioDispatcher = dispatcher)
        viewModel.loadServers()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.testAllServers()
        dispatcher.scheduler.runCurrent()

        assertEquals(4, maximumInFlight.get())
        coVerify(exactly = 4) { api.testMcpServer(any()) }

        release.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 7) { api.testMcpServer(any()) }
        assertEquals(4, maximumInFlight.get())
    }

    @Test
    fun `single and batch tests share one concurrency bound`() {
        val servers = (1..5).map { McpServer(name = "batch-$it", enabled = true) }
        coEvery { api.getMcpServers() } returns GatewayResponse.success(McpServersResponse(servers))
        val release = CompletableDeferred<Unit>()
        val inFlight = AtomicInteger()
        val maximumInFlight = AtomicInteger()
        coEvery { api.testMcpServer(any()) } coAnswers {
            val current = inFlight.incrementAndGet()
            maximumInFlight.updateAndGet { maximum -> maxOf(maximum, current) }
            release.await()
            inFlight.decrementAndGet()
            GatewayResponse.success(McpServerTestResponse(ok = true))
        }

        val viewModel = McpServersViewModel(ioDispatcher = dispatcher)
        viewModel.loadServers()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.testServer("single-1")
        viewModel.testServer("single-2")
        viewModel.testAllServers()
        dispatcher.scheduler.runCurrent()

        assertEquals(4, maximumInFlight.get())
        coVerify(exactly = 1) { api.testMcpServer("single-1") }
        coVerify(exactly = 1) { api.testMcpServer("single-2") }
        coVerify(exactly = 2) { api.testMcpServer(match { it.startsWith("batch-") }) }

        release.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 7) { api.testMcpServer(any()) }
        assertEquals(4, maximumInFlight.get())
    }

    @Test
    fun `single and batch tests for the same server are serialized without clearing active state`() {
        coEvery { api.getMcpServers() } returns
            GatewayResponse.success(McpServersResponse(listOf(McpServer(name = "shared", enabled = true))))
        val firstRelease = CompletableDeferred<Unit>()
        val secondRelease = CompletableDeferred<Unit>()
        val invocation = AtomicInteger()
        coEvery { api.testMcpServer("shared") } coAnswers {
            when (invocation.incrementAndGet()) {
                1 -> firstRelease.await()
                2 -> secondRelease.await()
            }
            GatewayResponse.success(McpServerTestResponse(ok = true))
        }

        val viewModel = McpServersViewModel(ioDispatcher = dispatcher)
        viewModel.loadServers()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.testServer("shared")
        dispatcher.scheduler.runCurrent()
        viewModel.testAllServers()
        dispatcher.scheduler.runCurrent()

        assertEquals(1, invocation.get())
        assertTrue("shared" in viewModel.uiState.value.testingServers)

        firstRelease.complete(Unit)
        dispatcher.scheduler.runCurrent()
        assertEquals(2, invocation.get())
        assertTrue("shared" in viewModel.uiState.value.testingServers)

        secondRelease.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isTestingAll)
        assertTrue(viewModel.uiState.value.testingServers.isEmpty())
        assertEquals(true, viewModel.uiState.value.serverTestResults["shared"]?.ok)
    }
}
