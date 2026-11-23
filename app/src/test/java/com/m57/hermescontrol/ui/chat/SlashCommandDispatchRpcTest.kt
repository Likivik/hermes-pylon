// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.ui.chat

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.local.HermesDatabase
import com.m57.hermescontrol.data.ws.ConnectionStatus
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.data.ws.WsEvent
import com.m57.hermescontrol.data.ws.WsMethods
import com.m57.hermescontrol.ui.chat.fakes.FakeChatGateway
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
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Issue #549 — Layer 1+2 linkage: a non-hardcoded slash command must be
 * forwarded to the backend via [WsMethods.COMMAND_DISPATCH] with the EXACT
 * param shape the gateway expects: { name, arg, session_id }.
 *
 * Verified against the live backend (hermes-agent tui_gateway/server.py
 * @method("command.dispatch")): the gateway reads `params["name"]` and
 * `params["arg"]`. A `{"command": ...}` shape (used by an earlier probe)
 * resolves to an empty name and returns error 4018. So the mobile MUST send
 * name/arg, not command. This test locks that contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SlashCommandDispatchRpcTest {
    private val testDispatcher = StandardTestDispatcher()
    private val fakeTransport = FakeChatTransport()
    private val fakeApi = FakeChatGateway()
    private lateinit var app: Application
    private lateinit var resolver: FakeStringResolver
    private lateinit var fakeRepo: FakeChatPersistenceRepository
    private lateinit var fakeSlashUsageStore: FakeSlashUsageStore

    /**
     * Every ViewModel built by this class, so `tearDown` can cancel its
     * `viewModelScope`. Without this a coroutine from one test survives into the
     * next and surfaces as `UncaughtExceptionsBeforeTest` on whichever test
     * happens to run afterwards — the flake this class kept producing.
     */
    private val createdViewModels = mutableListOf<ChatViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val testMainDispatcher = Dispatchers.Main

        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns testDispatcher
        every { Dispatchers.Main } returns testMainDispatcher

        mockkObject(AuthManager)
        mockkObject(HermesDatabase)

        app = mockk(relaxed = true)
        resolver = FakeStringResolver()
        fakeRepo = FakeChatPersistenceRepository()
        fakeSlashUsageStore = FakeSlashUsageStore()

        fakeTransport.status.value = ConnectionStatus.DISCONNECTED

        every { AuthManager.getToken() } returns "test-token"
        every { AuthManager.getSelectedProfileId() } returns "profile-test"
        every { AuthManager.isTypingEffectEnabled() } returns true
        every { AuthManager.getTypingEffectDelayMs() } returns 30
        every { AuthManager.isAutoReconnect() } returns false
        fakeTransport.status.value = ConnectionStatus.DISCONNECTED
        fakeTransport.bindingIsCurrent = true
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
        unmockkAll()
        // Clears reconnect/timeout jobs the production client left behind.
        HermesWsClient.cancelBackgroundWorkForTest()
    }

    private suspend fun TestScope.createViewModelWithSession(): Pair<ChatViewModel, String> {
        val vm =
            ChatViewModel(
                testChatEnvironment(fakeSlashUsageStore),
                resolver,
                false,
                fakeRepo,
                fakeSlashUsageStore,
                testDispatcher,
                wsClient = fakeTransport,
                api = fakeApi,
            )
        createdViewModels += vm
        advanceUntilIdle()
        fakeTransport.status.value = ConnectionStatus.CONNECTED
        fakeTransport.eventFlow.emit(WsEvent.GatewayReady(null))
        advanceUntilIdle()
        // Auto-create on launch is disabled (Likivik): the ViewModel no longer
        // fires session.create on GatewayReady, so the test must request the
        // session explicitly — mirroring the user tapping a rail row — and then
        // answer the create that was actually sent (the last send wins).
        vm.createNewSession()
        advanceUntilIdle()
        val createId = "req-id-${fakeTransport.sendCount}"
        fakeTransport.eventFlow.emit(WsEvent.RpcResult(createId, mapOf("session_id" to "session-xyz")))
        advanceUntilIdle()
        check(vm.uiState.value.currentSessionId == "session-xyz") {
            "createViewModelWithSession: session was not installed"
        }
        return Pair(vm, "session-xyz")
    }

    @Test
    fun `non-hardcoded slash command forwards name arg session_id to COMMAND_DISPATCH`() =
        runTest {
            val (vm, sessionId) = createViewModelWithSession()

            // /help is NOT client-special-cased -> RpcDispatch
            vm.sendMessage("/help")
            advanceUntilIdle()

            assertTrue("expected a COMMAND_DISPATCH request", fakeTransport.requests.isNotEmpty())
            assertEquals(WsMethods.COMMAND_DISPATCH, fakeTransport.requests.last().method)
            val params = fakeTransport.requests.last().params
            assertEquals("help", params["name"])
            assertEquals("", params["arg"])
            assertEquals(sessionId, params["session_id"])
        }

    @Test
    fun `slash queue submits prompt with queued flag`() =
        runTest {
            val (vm, sessionId) = createViewModelWithSession()

            vm.sendMessage("/queue do the thing")
            advanceUntilIdle()

            assertTrue(fakeTransport.requests.none { it.method == WsMethods.COMMAND_DISPATCH })
            assertEquals(sessionId, fakeTransport.sentMessages.last().sessionId)
            assertEquals("do the thing", fakeTransport.sentMessages.last().text)
            assertEquals(true, fakeTransport.sentMessages.last().queued)
            val userBubble =
                vm.uiState.value.messages.lastOrNull {
                    it.role == MessageRole.USER
                }
            assertEquals("do the thing", userBubble?.content)
        }

    @Test
    fun `bare queue shows usage without sending`() =
        runTest {
            val (vm, _) = createViewModelWithSession()
            vm.sendMessage("/queue")
            advanceUntilIdle()

            assertEquals(0, fakeTransport.sentMessages.size)
            assertEquals(
                "usage: /queue <prompt>",
                vm.uiState.value.messages.lastOrNull()?.content,
            )
        }

    @Test
    fun `non-registry-miss backend error is surfaced directly (no fallback)`() =
        runTest {
            val (vm, _) = createViewModelWithSession()

            fakeTransport.onRequest = { method, _, _ ->
                val d = CompletableDeferred<Any?>()
                d.completeExceptionally(
                    // A real, actionable error (not the "not a command" 4018
                    // that triggers the slash.exec fallback).
                    HermesWsClient.HermesRpcException("session busy — /interrupt first"),
                )
                d
            }

            vm.sendMessage("/help")
            advanceUntilIdle()

            val last = vm.uiState.value.messages.lastOrNull()
            assertEquals("/help: session busy — /interrupt first", last?.content)
        }

    @Test
    fun `registry-miss 4018 on command dispatch falls back to slash_exec and surfaces output`() =
        runTest {
            val (vm, sessionId) = createViewModelWithSession()

            fakeTransport.onRequest = { m, _, _ ->
                val d = CompletableDeferred<Any?>()
                if (m == WsMethods.COMMAND_DISPATCH) {
                    // Backend rejects /status with the registry-miss 4018 (issue #576).
                    d.completeExceptionally(
                        HermesWsClient.HermesRpcException(
                            "not a quick/plugin/bundle/skill command: status",
                        ),
                    )
                } else {
                    // slash.exec runs the full COMMAND_REGISTRY and returns output.
                    d.complete(mapOf("output" to "STATUS: gateway reachable"))
                }
                d
            }

            vm.sendMessage("/status")
            advanceUntilIdle()

            // The fallback must have hit slash.exec with the full command string.
            // (methodSlot/paramsSlot hold the LAST call = slash.exec.)
            assertEquals(WsMethods.SLASH_EXEC, fakeTransport.requests.last().method)
            val params = fakeTransport.requests.last().params
            assertEquals("/status", params["command"])
            assertEquals(sessionId, params["session_id"])

            // And the slash.exec output must be surfaced to the user.
            val last = vm.uiState.value.messages.lastOrNull()
            assertEquals("STATUS: gateway reachable", last?.content)
        }

    @Test
    fun `blocklisted cli_only or TUI-only command is rejected with friendly message and no RPC`() =
        runTest {
            val (vm, _) = createViewModelWithSession()

            fakeTransport.onRequest = { method, _, _ ->
                CompletableDeferred<Any?>(Unit)
            }

            // /redraw is TUI-only (issue #574) — hidden from suggestions but a
            // user can still type it. It must be blocked before any RPC fires.
            vm.sendMessage("/redraw")
            advanceUntilIdle()

            val last = vm.uiState.value.messages.lastOrNull()
            assertTrue(
                "expected a 'not supported on mobile' message, got: ${last?.content}",
                last?.content?.contains("not supported on mobile") == true,
            )
            assertEquals("no RPC should fire for a blocklisted command", 0, fakeTransport.requests.size)
        }

    @Test
    fun `slash_exec double-fault surfaces the secondary error`() =
        runTest {
            val (vm, _) = createViewModelWithSession()

            fakeTransport.onRequest = { method, _, _ ->
                val m = method
                val d = CompletableDeferred<Any?>()
                if (m == WsMethods.COMMAND_DISPATCH) {
                    // Registry miss -> triggers the slash.exec fallback.
                    d.completeExceptionally(
                        HermesWsClient.HermesRpcException(
                            "not a quick/plugin/bundle/skill command: status",
                        ),
                    )
                } else {
                    // slash.exec ALSO fails (e.g. worker can't start).
                    d.completeExceptionally(
                        HermesWsClient.HermesRpcException("slash worker start failed: boom"),
                    )
                }
                d
            }

            vm.sendMessage("/status")
            advanceUntilIdle()

            // Both RPCs fail -> the secondary slash.exec error must surface.
            val last = vm.uiState.value.messages.lastOrNull()
            assertEquals("/status: slash worker start failed: boom", last?.content)
        }

    @Test
    fun `slash_exec blank output appends no assistant message`() =
        runTest {
            val (vm, _) = createViewModelWithSession()

            fakeTransport.onRequest = { method, _, _ ->
                val m = method
                val d = CompletableDeferred<Any?>()
                if (m == WsMethods.COMMAND_DISPATCH) {
                    d.completeExceptionally(
                        HermesWsClient.HermesRpcException(
                            "not a quick/plugin/bundle/skill command: status",
                        ),
                    )
                } else {
                    // slash.exec succeeded but returned no/empty output.
                    d.complete(mapOf("output" to ""))
                }
                d
            }

            val before = vm.uiState.value.messages.size
            vm.sendMessage("/status")
            advanceUntilIdle()

            // The user's "/status" message is added, but blank slash.exec output
            // must NOT append an assistant bubble — so the last message is still
            // the user's own command, and only one message was added.
            assertEquals(before + 1, vm.uiState.value.messages.size)
            assertEquals("/status", vm.uiState.value.messages.lastOrNull()?.content)
        }

    @Test
    fun `slash init forwards name=init to COMMAND_DISPATCH`() =
        runTest {
            val (vm, sessionId) = createViewModelWithSession()

            // /init generates-or-updates AGENTS.md (backend command.dispatch
            // name=="init" branch, hermes-agent tui_gateway/server.py ~L15451).
            // NOT client-special-cased -> RpcDispatch.
            vm.sendMessage("/init")
            advanceUntilIdle()

            assertEquals(WsMethods.COMMAND_DISPATCH, fakeTransport.requests.last().method)
            val params = fakeTransport.requests.last().params
            assertEquals("init", params["name"])
            assertEquals("", params["arg"])
            assertEquals(sessionId, params["session_id"])
        }

    @Test
    fun `slash init with extra arg forwards arg separately`() =
        runTest {
            val (vm, sessionId) = createViewModelWithSession()

            vm.sendMessage("/init extra context here")
            advanceUntilIdle()

            assertEquals(WsMethods.COMMAND_DISPATCH, fakeTransport.requests.last().method)
            val params = fakeTransport.requests.last().params
            assertEquals("init", params["name"])
            assertEquals("extra context here", params["arg"])
            assertEquals(sessionId, params["session_id"])
        }

    @Test
    fun `slash focus forwards name=focus to COMMAND_DISPATCH`() =
        runTest {
            val (vm, sessionId) = createViewModelWithSession()

            // /focus is the display-only focus view (backend command.dispatch
            // name=="focus" branch, hermes-agent tui_gateway/server.py ~L15522).
            // NOT client-special-cased -> RpcDispatch.
            vm.sendMessage("/focus")
            advanceUntilIdle()

            assertEquals(WsMethods.COMMAND_DISPATCH, fakeTransport.requests.last().method)
            val params = fakeTransport.requests.last().params
            assertEquals("focus", params["name"])
            assertEquals("", params["arg"])
            assertEquals(sessionId, params["session_id"])
        }

    @Test
    fun `slash focus on off status forwards arg to COMMAND_DISPATCH`() =
        runTest {
            val (vm, sessionId) = createViewModelWithSession()

            vm.sendMessage("/focus on")
            advanceUntilIdle()

            assertEquals(WsMethods.COMMAND_DISPATCH, fakeTransport.requests.last().method)
            val params = fakeTransport.requests.last().params
            assertEquals("focus", params["name"])
            assertEquals("on", params["arg"])
            assertEquals(sessionId, params["session_id"])
        }

    @Test
    fun `command dispatch type=send submits the returned prompt (init)`() =
        runTest {
            val (vm, _) = createViewModelWithSession()

            fakeTransport.onRequest = { method, _, _ ->
                val d = CompletableDeferred<Any?>()
                if (method == WsMethods.COMMAND_DISPATCH) {
                    // Backend /init returns type:"send" with the AGENTS.md prompt.
                    d.complete(mapOf("type" to "send", "message" to "Scan this repo and write AGENTS.md"))
                } else {
                    d.complete(mapOf("session_id" to "session-xyz"))
                }
                d
            }
            // submitPrompt() forwards the returned prompt via wsClient.sendMessage.
            vm.sendMessage("/init")
            advanceUntilIdle()

            // type:"send" must be forwarded to the agent as a normal prompt turn.
            assertEquals("Scan this repo and write AGENTS.md", fakeTransport.sentMessages.last().text)
        }

    @Test
    fun `command dispatch type=exec surfaces output as a message (focus)`() =
        runTest {
            val (vm, _) = createViewModelWithSession()

            fakeTransport.onRequest = { method, _, _ ->
                val m = method
                val d = CompletableDeferred<Any?>()
                if (m == WsMethods.COMMAND_DISPATCH) {
                    // Backend /focus returns type:"exec" with a notice line.
                    d.complete(mapOf("type" to "exec", "output" to "Focus view: ON (tool progress pinned off)"))
                } else {
                    d.complete(mapOf("session_id" to "session-xyz"))
                }
                d
            }

            vm.sendMessage("/focus on")
            advanceUntilIdle()

            val last = vm.uiState.value.messages.lastOrNull()
            assertEquals("Focus view: ON (tool progress pinned off)", last?.content)
        }
}
