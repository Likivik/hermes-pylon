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
 * Characterisation net for the send pipeline, written before it is cut out of the ViewModel.
 *
 * `sendMessage` is 183 lines and had **no** test that named its routing decisions: refuse, dispatch a
 * slash command, open the model picker, submit a prompt, or redirect into a running turn. The 169 tests
 * in `ChatViewModelTest` exercise sends incidentally, as a step towards something else, so a rewrite
 * could preserve their outcomes and still send the wrong RPC.
 *
 * Six scenarios, one per decision. Run against both sides of the extraction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSendPipelineCharacterizationTest {
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
    fun `a send before a session exists is refused`() =
        runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            assertFalse("no session, no send", vm.sendMessage("hello"))
            advanceUntilIdle()
            assertTrue(fakeTransport.sentMessages.isEmpty())
        }

    @Test
    fun `blank text with no attachments is refused`() =
        runTest {
            val vm = viewModelWithSession()

            assertFalse(vm.sendMessage("   "))
            advanceUntilIdle()
            assertTrue(fakeTransport.sentMessages.isEmpty())
        }

    @Test
    fun `a slash command is never submitted as a prompt`() =
        runTest {
            val vm = viewModelWithSession()

            assertTrue("a slash command is accepted", vm.sendMessage("/status"))
            advanceUntilIdle()

            assertTrue(
                "the text must not reach prompt.submit",
                fakeTransport.sentMessages.none { it.text.contains("/status") },
            )
        }

    @Test
    fun `a bare slash model opens the picker instead of sending`() =
        runTest {
            val vm = viewModelWithSession()

            assertTrue(vm.sendMessage("/model"))
            advanceUntilIdle()

            assertTrue("the picker is open", vm.uiState.value.showModelPicker)
            assertTrue(fakeTransport.sentMessages.isEmpty())
        }

    @Test
    fun `a normal send shows the user message before the network answers`() =
        runTest {
            val vm = viewModelWithSession()

            assertEquals(true, vm.sendMessage("hello"))
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("hello", state.messages.last().content)
            assertEquals(MessageRole.USER, state.messages.last().role)
            assertTrue("the turn is marked active", state.isAgentTyping)
            assertEquals("hello", fakeTransport.sentMessages.last().text)
            assertTrue(fakeTransport.redirects.isEmpty())
        }

    @Test
    fun `a send during an active turn redirects instead of submitting`() =
        runTest {
            val vm = viewModelWithSession()
            vm.sendMessage("first")
            advanceUntilIdle()

            // A turn is running: the backend said so, and nothing has completed it.
            fakeTransport.eventFlow.emit(WsEvent.MessageStart("s-1"))
            advanceUntilIdle()

            vm.sendMessage("follow-up")
            advanceUntilIdle()

            assertEquals("one redirect", 1, fakeTransport.redirects.size)
            assertEquals("follow-up", fakeTransport.redirects.single().text)
            assertEquals("nothing was submitted", 1, fakeTransport.sentMessages.size)
        }
}
