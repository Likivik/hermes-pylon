package com.m57.hermescontrol.ui.chat

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.local.HermesDatabase
import com.m57.hermescontrol.data.ws.ConnectionStatus
import com.m57.hermescontrol.data.ws.WsEvent
import com.m57.hermescontrol.ui.chat.fakes.FakeChatApi
import com.m57.hermescontrol.ui.chat.fakes.FakeChatPersistenceRepository
import com.m57.hermescontrol.ui.chat.fakes.FakeChatTransport
import com.m57.hermescontrol.ui.chat.fakes.FakeSlashUsageStore
import com.m57.hermescontrol.ui.chat.fakes.testChatEnvironment
import com.m57.hermescontrol.ui.common.FakeStringResolver
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Characterisation net for the event router, before it is cut out of the ViewModel.
 *
 * `handleWsEvent` is 342 lines and `handleRpcResult` is 325. Moving either without a net means the
 * extraction's correctness is judged by reading a diff. These scenarios pin the behaviour that must
 * survive the move: which events reach the transcript, which are ignored by session identity, and what
 * a completed turn leaves behind.
 *
 * Deliberately narrow. A broad harness in this module would duplicate the 169 tests in
 * `ChatViewModelTest`; this file exists to be *run against both sides* of the extraction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRouterCharacterizationTest {
    private val testDispatcher = StandardTestDispatcher()
    private val fakeTransport = FakeChatTransport()
    private val fakeApi = FakeChatApi()
    private lateinit var app: Application
    private lateinit var resolver: FakeStringResolver
    private lateinit var fakeRepo: FakeChatPersistenceRepository
    private lateinit var fakeSlashUsageStore: FakeSlashUsageStore
    private val createdViewModels = mutableListOf<ChatViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        mockkObject(AuthManager)
        mockkObject(HermesDatabase)
        every { AuthManager.getToken() } returns "test-token"
        every { AuthManager.getSelectedProfileId() } returns "profile-test"
        every { AuthManager.isTypingEffectEnabled() } returns false
        every { AuthManager.getTypingEffectDelayMs() } returns 0
        every { AuthManager.isAutoReconnect() } returns false
        every { AuthManager.getPinnedModels() } returns emptyList()

        app = mockk(relaxed = true)
        resolver = FakeStringResolver()
        fakeRepo = FakeChatPersistenceRepository()
        fakeSlashUsageStore = FakeSlashUsageStore()
        fakeTransport.status.value = ConnectionStatus.DISCONNECTED
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): ChatViewModel =
        ChatViewModel(
            testChatEnvironment(fakeSlashUsageStore),
            resolver,
            false,
            fakeRepo,
            fakeSlashUsageStore,
            testDispatcher,
            io = testDispatcher,
            wsClient = fakeTransport,
            api = fakeApi,
        ).also { createdViewModels += it }

    /** Gateway up, one session created through the real request path. */
    private suspend fun TestScope.viewModelWithSession(): ChatViewModel {
        val vm = createViewModel()
        advanceUntilIdle()
        fakeTransport.status.value = ConnectionStatus.CONNECTED
        fakeTransport.eventFlow.emit(WsEvent.GatewayReady(null))
        advanceUntilIdle()

        vm.createNewSession()
        advanceUntilIdle()
        val createId = "req-id-${fakeTransport.sendCount}"
        fakeTransport.eventFlow.emit(WsEvent.RpcResult(createId, mapOf("session_id" to "s-1")))
        advanceUntilIdle()
        check(vm.uiState.value.currentSessionId == "s-1") { "session not installed" }
        return vm
    }

    @Test
    fun `a streamed turn lands in the transcript and clears the typing flag`() =
        runTest {
            val vm = viewModelWithSession()

            vm.sendMessage("hello")
            advanceUntilIdle()

            fakeTransport.eventFlow.emit(WsEvent.MessageStart("s-1"))
            advanceUntilIdle()
            fakeTransport.eventFlow.emit(WsEvent.MessageToken("hello", "s-1"))
            advanceUntilIdle()
            fakeTransport.eventFlow.emit(WsEvent.MessageToken(" world", "s-1"))
            advanceUntilIdle()
            fakeTransport.eventFlow.emit(WsEvent.MessageComplete("hello world", "s-1"))
            advanceUntilIdle()

            val state = vm.uiState.value
            val assistant = state.messages.lastOrNull { it.role == MessageRole.ASSISTANT }
            assertEquals("hello world", assistant?.content)
            assertFalse("the turn is over", state.isAgentTyping)
            assertTrue(state.hasConnectedOnce)
        }

    @Test
    fun `tokens addressed to another session are ignored`() =
        runTest {
            val vm = viewModelWithSession()

            fakeTransport.eventFlow.emit(WsEvent.MessageStart("other"))
            advanceUntilIdle()
            fakeTransport.eventFlow.emit(WsEvent.MessageToken("leaked", "other"))
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(
                "nothing from another session may reach the transcript",
                state.messages.none { it.content.contains("leaked") },
            )
        }

    @Test
    fun `a result that matches no pending request changes nothing`() =
        runTest {
            val vm = viewModelWithSession()
            val before = vm.uiState.value

            fakeTransport.eventFlow.emit(WsEvent.RpcResult("never-sent", mapOf("session_id" to "s-9")))
            advanceUntilIdle()

            assertEquals(before.currentSessionId, vm.uiState.value.currentSessionId)
            assertEquals(before.messages.size, vm.uiState.value.messages.size)
        }
}
