package com.m57.hermescontrol.ui.toolsets

import com.m57.hermescontrol.data.model.Toolset
import com.m57.hermescontrol.data.model.ToolsetToggleRequest
import com.m57.hermescontrol.data.remote.GatewayResponse
import com.m57.hermescontrol.data.remote.source.Toolsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The point of the `*Source` interfaces: this ViewModel can be driven with a fake.
 *
 * Note what is absent — no `mockkObject(GatewayConnection)`, no relaxed mock of a 150-method class. The screen names two
 * calls, so the fake implements two. That is the whole testability argument for the gateway split; if this test
 * ever needs a mocked singleton to pass, the seam has been lost.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolsetsSeamTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeToolsetsSource : Toolsets {
        var loadCount = 0
        var toggled: Pair<String, Boolean>? = null

        override suspend fun getToolsets(): GatewayResponse<List<Toolset>> {
            loadCount++
            return GatewayResponse.success(listOf(Toolset(name = "web", enabled = true)))
        }

        override suspend fun toggleToolset(
            name: String,
            body: ToolsetToggleRequest,
        ): GatewayResponse<Unit> {
            toggled = name to body.enabled
            return GatewayResponse.success(Unit)
        }
    }

    @Test
    fun `loads through the injected source, not the singleton`() =
        runBlocking {
            val fake = FakeToolsetsSource()
            val viewModel = ToolsetsViewModel(source = fake)

            viewModel.loadToolsets()

            withTimeout(5_000) { viewModel.uiState.first { it.toolsets.isNotEmpty() } }
            assertEquals(1, fake.loadCount)
            assertEquals("web", viewModel.uiState.value.toolsets.single().name)
        }
}
