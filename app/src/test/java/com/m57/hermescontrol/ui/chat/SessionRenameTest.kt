package com.m57.hermescontrol.ui.chat

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.m57.hermescontrol.ConnectionStatus
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.session.ActiveSessionHolder
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.data.ws.WsEvent
import com.m57.hermescontrol.data.ws.WsMethods
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.hermes_database.HermesDatabase
import hermes_cli.AuthManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Rename-path tests (Likivik patch — rail long-press → Edit → Save):
 *
 * The rename flows over two RPCs for background sessions:
 *   1. session.resume (with pendingRenameTitle queued on the request)
 *   2. session.title — sent from the resume ack, addressed by the ack's
 *      `session_key` field (stable storage id), NOT the rotated runtime id.
 *
 * For the CURRENT session it is a single session.title addressed by the
 * runtime id (agent.session_id), because post-compression the stored id no
 * longer matches the gateway's live lookup key.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRenameTest {
    private val testDispatcher = StandardTestDispatcher()
    private val mockEventsFlow = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)
    private val mockConnectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    private lateinit var app: android.content.Context
    private lateinit var fakeRepo: FakeChatPersistenceRepository
    private lateinit var fakeSlashUsageStore: FakeSlashUsageStore
    private lateinit var mockApi: com.m57.hermescontrol.data.remote.HermesApiService

    private var reqCount = 0

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val testMainDispatcher = Dispatchers.Main
        reqCount = 0

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
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

        // Default send stub: unique IDs + onSent callback.
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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ActiveSessionHolder.clear()
        io.mockk.unmockkAll()
    }

    private fun createViewModel(): ChatViewModel =
        ChatViewModel(app, false, fakeRepo, fakeSlashUsageStore, testDispatcher)

    private suspend fun TestScope.bootConnected(): ChatViewModel {
        val viewModel = createViewModel()
        advanceUntilIdle()
        mockConnectionStatus.value = ConnectionStatus.CONNECTED
        mockEventsFlow.emit(WsEvent.GatewayReady(null))
        advanceUntilIdle()
        // session.create for the initial session (req-id-3).
        mockEventsFlow.emit(
            WsEvent.RpcResult("req-id-3", mapOf("session_id" to "session-123")),
        )
        advanceUntilIdle()
        return viewModel
    }

    private fun stubResumeAck(storageId: String, runtimeId: String) {
        every {
            HermesWsClient.send(WsMethods.SESSION_RESUME, any(), any())
        } answers {
            reqCount++
            val id = "req-resume-$reqCount"
            arg<((String) -> Unit)?>(2)?.invoke(id)
            id
        }
        // Any RpcResult with id starting req-resume carries the resume payload.
        // Emitted explicitly by the tests after the rename call.
    }

    @Test
    fun backgroundRename_resumesFirst_thenTitlesBySessionKey() =
        runTest {
            val viewModel = bootConnected()
            stubResumeAck("stored-bg", "runtime-8hex")

            // Background rename (different id from the current session).
            viewModel.renameSession("stored-bg", "New Test Name", "🧪")
            advanceUntilIdle()

            // First RPC = session.resume with the queued rename.
            io.mockk.verify {
                HermesWsClient.send(
                    WsMethods.SESSION_RESUME,
                    mapOf("session_id" to "stored-bg", "omit_messages" to true),
                    any(),
                )
            }

            // Resume ack: runtime id rotates, session_key stays the storage id.
            mockEventsFlow.emit(
                WsEvent.RpcResult(
                    "req-resume-2",
                    mapOf(
                        "session_id" to "runtime-8hex",
                        "resumed" to "stored-bg",
                        "message_count" to 0.0,
                        "messages" to emptyList<Map<String, Any?>>(),
                        "session_key" to "stored-bg",
                    ),
                ),
            )
            advanceUntilIdle()

            // session.title must be addressed by the STORAGE id.
            io.mockk.verify {
                HermesWsClient.send(
                    WsMethods.SESSION_TITLE,
                    mapOf("session_id" to "stored-bg", "title" to "New Test Name"),
                )
            }
            // …and never by the rotated runtime id.
            io.mockk.verify(exactly = 0) {
                HermesWsClient.send(
                    WsMethods.SESSION_TITLE,
                    mapOf("session_id" to "runtime-8hex", any()),
                )
            }
            // The rail reflects the new title immediately (optimistic).
            assertEquals(
                "New Test Name",
                viewModel.uiState.value.sessions.first { it.id == "stored-bg" }.title,
            )
        }

    @Test
    fun backgroundRename_resumeFailure_surfacesError_andDoesNotSendTitle() =
        runTest {
            val viewModel = bootConnected()

            every {
                HermesWsClient.send(WsMethods.SESSION_RESUME, any(), any())
            } answers {
                reqCount++
                val id = "req-resume-fail-$reqCount"
                arg<((String) -> Unit)?>(2)?.invoke(id)
                id
            }

            viewModel.renameSession("stored-dead", "Never Persists", null)
            advanceUntilIdle()

            // Resume fails server-side.
            mockEventsFlow.emit(
                WsEvent.RpcError(
                    "req-resume-fail-3",
                    com.m57.hermescontrol.data.ws.JsonRpcError(
                        code = 5000,
                        message = "resume failed: agent build exploded",
                    ),
                ),
            )
            advanceUntilIdle()

            // No session.title may be sent after a failed resume.
            io.mockk.verify(exactly = 0) {
                HermesWsClient.send(eq(WsMethods.SESSION_TITLE), any(), any())
            }
            // The failure is surfaced, not silently dropped.
            assertEquals(
                true,
                viewModel.uiState.value.errorMessage?.contains("session.resume") ?: false,
            )
        }

    @Test
    fun liveSessionRename_addressesByRuntimeId_notStoredId() =
        runTest {
            val viewModel = bootConnected()
            // Current session is live: runtime id 8-hex, storage id session-123.
            ActiveSessionHolder.set("runtime-live-8hex", "session-123")

            viewModel.renameSession("session-123", "Live Rename", null)
            advanceUntilIdle()

            // Addressed by the RUNTIME id (post-compression the stored id no
            // longer matches the live lookup key).
            io.mockk.verify {
                HermesWsClient.send(
                    WsMethods.SESSION_TITLE,
                    mapOf("session_id" to "runtime-live-8hex", "title" to "Live Rename"),
                )
            }
            io.mockk.verify(exactly = 0) {
                HermesWsClient.send(
                    WsMethods.SESSION_TITLE,
                    mapOf("session_id" to "session-123", any()),
                )
            }
        }

    @Test
    fun backgroundRename_doesNotHijackCurrentSession() =
        runTest {
            val viewModel = bootConnected()
            stubResumeAck("stored-bg", "runtime-8hex")

            viewModel.renameSession("stored-bg", "New Test Name", null)
            advanceUntilIdle()
            mockEventsFlow.emit(
                WsEvent.RpcResult(
                    "req-resume-2",
                    mapOf(
                        "session_id" to "runtime-8hex",
                        "resumed" to "stored-bg",
                        "session_key" to "stored-bg",
                        "messages" to emptyList<Map<String, Any?>>(),
                    ),
                ),
            )
            advanceUntilIdle()

            // The current chat must NOT be switched to the renamed session.
            assertEquals("session-123", viewModel.uiState.value.currentSessionId)
        }
}
