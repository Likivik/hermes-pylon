// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.ui.chat

import android.app.Application
import android.util.Log
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.local.HermesDatabase
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.session.ActiveSessionHolder
import com.m57.hermescontrol.data.ws.ConnectionStatus
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.data.ws.JsonRpcError
import com.m57.hermescontrol.data.ws.WsEvent
import com.m57.hermescontrol.data.ws.WsMethods
import com.m57.hermescontrol.ui.chat.fakes.FakeChatPersistenceRepository
import com.m57.hermescontrol.ui.chat.fakes.FakeSlashUsageStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Rename-path tests (Likivik patch — rail long-press → Edit → Save).
 *
 * A rename ALWAYS resumes first, then fires `session.title` from the resume
 * ack. Two things decide how the title is addressed:
 *  - the ack's `session_key` (stable storage id) wins when present — the
 *    gateway's session.title resolves via agent.session_id OR session_key;
 *  - otherwise the runtime id, and for a CURRENT-session rename whose ack is
 *    the gateway's fast-path payload (already-live session, no session_id /
 *    session_key reported) that means the runtime id the chat already held.
 *
 * A BACKGROUND rename must never fall back to the cached runtime id — it
 * belongs to the session the user is viewing, so doing so would title the
 * wrong session. With no addressable id in such an ack, no title is sent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRenameTest {
    private val testDispatcher = StandardTestDispatcher()
    private val mockEventsFlow = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)
    private val mockConnectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    private lateinit var app: Application
    private lateinit var fakeRepo: FakeChatPersistenceRepository
    private lateinit var fakeSlashUsageStore: FakeSlashUsageStore
    private lateinit var mockApi: com.m57.hermescontrol.data.remote.HermesApiService

    /** Counter used to generate unique WS request IDs (mirrors the client). */
    private var reqCount = 0

    /** Request id of the most recent session.resume send. */
    private var resumeRequestId = ""

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val testMainDispatcher = Dispatchers.Main
        reqCount = 0
        resumeRequestId = ""

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns testDispatcher
        every { Dispatchers.Main } returns testMainDispatcher

        mockkObject(AuthManager)
        every { AuthManager.getPinnedModels() } returns emptyList()
        mockkObject(HermesWsClient)
        mockkObject(ApiClient)
        mockkObject(HermesDatabase)

        app = mockk(relaxed = true)
        every { app.getString(R.string.chat_model_switch_failed, any()) } answers {
            val formatArgs = arg<Array<out Any>>(1)
            "Failed to switch model: ${formatArgs.first()}"
        }
        fakeRepo = FakeChatPersistenceRepository()
        fakeSlashUsageStore = FakeSlashUsageStore()
        every { AuthManager.getSelectedProfileId() } returns "profile-a"

        mockConnectionStatus.value = ConnectionStatus.DISCONNECTED

        every { AuthManager.getToken() } returns "test-token"
        every { AuthManager.isTypingEffectEnabled() } returns true
        every { AuthManager.getTypingEffectDelayMs() } returns 30
        every { AuthManager.isAutoReconnect() } returns false
        every { HermesWsClient.events } returns mockEventsFlow
        every { HermesWsClient.connectionStatus } returns mockConnectionStatus
        every { HermesWsClient.connect() } answers {
            mockConnectionStatus.value = ConnectionStatus.CONNECTING
        }
        every { HermesWsClient.disconnect() } returns Unit

        // Default send stub: unique ids + onSent callback.
        every { HermesWsClient.send(any(), any(), any()) } answers {
            reqCount++
            val id = "req-id-$reqCount"
            arg<((String) -> Unit)?>(2)?.invoke(id)
            id
        }
        every { HermesWsClient.sendMessage(any(), any(), any(), any()) } answers {
            reqCount++
            val id = "req-msg-$reqCount"
            arg<((String) -> Unit)?>(2)?.invoke(id)
            id
        }
        every { HermesWsClient.sendRedirect(any(), any(), any()) } answers {
            reqCount++
            val id = "req-redirect-$reqCount"
            arg<((String) -> Unit)?>(2)?.invoke(id)
            id
        }
        every { HermesWsClient.request(WsMethods.CONFIG_SET, any(), any()) } returns
            CompletableDeferred<Any?>(mapOf("ok" to true))

        mockApi = mockk(relaxed = true)
        every { ApiClient.hermesApi } returns mockApi
        coEvery { mockApi.getModelInfo() } returns
            retrofit2.Response.success(
                com.m57.hermescontrol.data.model.ModelInfoResponse(
                    model = "gpt-5.6-sol",
                    provider = "openai-codex",
                    effectiveContextLength = 272_000L,
                ),
            )
        coEvery { mockApi.getModelOptions(any(), any()) } returns
            retrofit2.Response.success(
                com.m57.hermescontrol.data.model.ModelOptionsResponse(
                    providers =
                        listOf(
                            com.m57.hermescontrol.data.model.ModelProvider(
                                slug = "openai",
                                name = "OpenAI",
                                models = listOf("gpt-4o", "gpt-4o-mini"),
                            ),
                        ),
                ),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ActiveSessionHolder.clear()
        unmockkAll()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun createViewModel(): ChatViewModel =
        ChatViewModel(app, false, fakeRepo, fakeSlashUsageStore, testDispatcher)

    /**
     * Create the ViewModel, emit GatewayReady, then create one session
     * explicitly (auto-create on launch is disabled — the user picks a rail
     * row). Returns the ViewModel; the live session id is "session-123".
     */
    private suspend fun TestScope.createViewModelWithSession(): ChatViewModel {
        val viewModel = createViewModel()
        advanceUntilIdle()

        mockConnectionStatus.value = ConnectionStatus.CONNECTED
        mockEventsFlow.emit(WsEvent.GatewayReady(null))
        advanceUntilIdle()

        viewModel.createNewSession()
        advanceUntilIdle()

        // The create is the last send, so reqCount points at it.
        val createId = "req-id-$reqCount"
        mockEventsFlow.emit(
            WsEvent.RpcResult(createId, mapOf("session_id" to "session-123")),
        )
        advanceUntilIdle()

        check(viewModel.uiState.value.currentSessionId == "session-123") {
            "createViewModelWithSession: live session was not installed"
        }
        return viewModel
    }

    /** Capture the id of the next session.resume send so the ack can match it. */
    private fun captureResumeRequestId() {
        every { HermesWsClient.send(WsMethods.SESSION_RESUME, any(), any()) } answers {
            reqCount++
            val id = "req-resume-$reqCount"
            resumeRequestId = id
            arg<((String) -> Unit)?>(2)?.invoke(id)
            id
        }
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun backgroundRename_resumesFirst_thenTitlesByStorageKey() =
        runTest {
            val viewModel = createViewModelWithSession()
            captureResumeRequestId()

            viewModel.renameSession("stored-bg", "New Test Name", "🧪")
            advanceUntilIdle()

            // 1. Resume carries the queued rename.
            verify {
                HermesWsClient.send(
                    WsMethods.SESSION_RESUME,
                    mapOf("session_id" to "stored-bg", "omit_messages" to true),
                    any(),
                )
            }

            // 2. Ack rotates the runtime id but keeps session_key = storage id.
            mockEventsFlow.emit(
                WsEvent.RpcResult(
                    resumeRequestId,
                    mapOf(
                        "session_id" to "runtime-8hex",
                        "message_count" to 0.0,
                        "messages" to emptyList<Map<String, Any?>>(),
                        "session_key" to "stored-bg",
                    ),
                ),
            )
            advanceUntilIdle()

            // 3. Title addressed by the STORAGE id…
            verify {
                HermesWsClient.send(
                    WsMethods.SESSION_TITLE,
                    mapOf("session_id" to "stored-bg", "title" to "New Test Name"),
                    any(),
                )
            }
            // …and never by the rotated runtime id.
            verify(exactly = 0) {
                HermesWsClient.send(
                    WsMethods.SESSION_TITLE,
                    mapOf("session_id" to "runtime-8hex", "title" to "New Test Name"),
                    any(),
                )
            }
            // (The optimistic rail title is not asserted here: the fake session
            // list is empty, so renameSession has no row to update. The RPC
            // addressing above is the contract under test.)
        }

    @Test
    fun backgroundRename_ackWithoutAddressableId_sendsNoTitle() =
        runTest {
            val viewModel = createViewModelWithSession()
            captureResumeRequestId()

            viewModel.renameSession("stored-bg", "New Test Name", null)
            advanceUntilIdle()

            // Ack carries NO session_id / session_key (nothing addressable).
            mockEventsFlow.emit(WsEvent.RpcResult(resumeRequestId, emptyMap<String, Any?>()))
            advanceUntilIdle()

            // No title: the cached runtime id belongs to the CURRENT session
            // ("session-123"), so falling back to it would rename the wrong one.
            verify(exactly = 0) {
                HermesWsClient.send(WsMethods.SESSION_TITLE, any(), any())
            }
        }

    @Test
    fun currentSessionRename_titlesByStorageKeyFromAck() =
        runTest {
            val viewModel = createViewModelWithSession()
            captureResumeRequestId()

            viewModel.renameSession("session-123", "Live Rename", null)
            advanceUntilIdle()

            // Even the CURRENT session resumes first — the gateway may have
            // reaped its detached runtime, and a bare session.title would 4001.
            verify {
                HermesWsClient.send(
                    WsMethods.SESSION_RESUME,
                    mapOf("session_id" to "session-123", "omit_messages" to true),
                    any(),
                )
            }

            mockEventsFlow.emit(
                WsEvent.RpcResult(
                    resumeRequestId,
                    mapOf(
                        "session_id" to "runtime-live-8hex",
                        "message_count" to 0.0,
                        "messages" to emptyList<Map<String, Any?>>(),
                        "session_key" to "session-123",
                    ),
                ),
            )
            advanceUntilIdle()

            verify {
                HermesWsClient.send(
                    WsMethods.SESSION_TITLE,
                    mapOf("session_id" to "session-123", "title" to "Live Rename"),
                    any(),
                )
            }
        }

    @Test
    fun currentSessionRename_fastPathAckWithoutSessionId_titlesByCachedRuntimeId() =
        runTest {
            val viewModel = createViewModelWithSession()
            captureResumeRequestId()

            viewModel.renameSession("session-123", "Live Rename", null)
            advanceUntilIdle()

            // Gateway fast-path payload for an already-live session: reports
            // nothing, so neither session_id nor session_key is present. This
            // used to silently drop the rename altogether.
            mockEventsFlow.emit(WsEvent.RpcResult(resumeRequestId, emptyMap<String, Any?>()))
            advanceUntilIdle()

            verify {
                HermesWsClient.send(
                    WsMethods.SESSION_TITLE,
                    mapOf("session_id" to "session-123", "title" to "Live Rename"),
                    any(),
                )
            }
        }

    @Test
    fun renameResumeFailure_surfacesError_andSendsNoTitle() =
        runTest {
            val viewModel = createViewModelWithSession()
            captureResumeRequestId()

            viewModel.renameSession("stored-dead", "Never Persists", null)
            advanceUntilIdle()

            mockEventsFlow.emit(
                WsEvent.RpcError(
                    resumeRequestId,
                    JsonRpcError(
                        code = 5000,
                        message = "resume failed: agent build exploded",
                    ),
                ),
            )
            advanceUntilIdle()

            // No title may be sent after a failed resume…
            verify(exactly = 0) {
                HermesWsClient.send(WsMethods.SESSION_TITLE, any(), any())
            }
            // …and the failure is surfaced, not silently dropped.
            assertTrue(
                "expected the resume failure in errorMessage, got " +
                    viewModel.uiState.value.errorMessage,
                viewModel.uiState.value.errorMessage?.contains("session.resume") == true,
            )
        }

    @Test
    fun backgroundRename_doesNotHijackCurrentSession() =
        runTest {
            val viewModel = createViewModelWithSession()
            captureResumeRequestId()

            viewModel.renameSession("stored-bg", "New Test Name", null)
            advanceUntilIdle()

            mockEventsFlow.emit(
                WsEvent.RpcResult(
                    resumeRequestId,
                    mapOf(
                        "session_id" to "runtime-8hex",
                        "session_key" to "stored-bg",
                        "messages" to emptyList<Map<String, Any?>>(),
                    ),
                ),
            )
            advanceUntilIdle()

            // The active chat must NOT be switched onto the renamed session.
            assertEquals("session-123", viewModel.uiState.value.currentSessionId)
        }
}
