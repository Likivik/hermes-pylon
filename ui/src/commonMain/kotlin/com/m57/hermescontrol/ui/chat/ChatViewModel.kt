// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.AppEnvironment
import com.m57.hermescontrol.data.local.AuthDefaults
import com.m57.hermescontrol.data.local.AuthSessionState
import com.m57.hermescontrol.data.local.PreferencesStore
import com.m57.hermescontrol.data.local.SlashUsageStore
import com.m57.hermescontrol.data.local.authStore
import com.m57.hermescontrol.data.model.Attachment
import com.m57.hermescontrol.data.model.AttachmentSource
import com.m57.hermescontrol.data.model.ModelProvider
import com.m57.hermescontrol.data.model.PinnedModel
import com.m57.hermescontrol.data.model.SessionMessage
import com.m57.hermescontrol.data.remote.AuthPayloads
import com.m57.hermescontrol.data.remote.HermesJson
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.attachmentReader
import com.m57.hermescontrol.data.remote.mediaStore
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.data.session.ActiveSessionHolder
import com.m57.hermescontrol.data.ws.CommandBlocklist
import com.m57.hermescontrol.data.ws.CommandCatalog
import com.m57.hermescontrol.data.ws.ConnectionBinding
import com.m57.hermescontrol.data.ws.ConnectionStatus
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.data.ws.JsonRpcError
import com.m57.hermescontrol.data.ws.PrivilegedRequestBinding
import com.m57.hermescontrol.data.ws.ServerRequestBinding
import com.m57.hermescontrol.data.ws.WsEvent
import com.m57.hermescontrol.data.ws.WsMethods
import com.m57.hermescontrol.data.ws.toAny
import com.m57.hermescontrol.data.ws.toJsonElement
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.attachment_error_access_denied
import com.m57.hermescontrol.generated.resources.attachment_error_missing_gateway_path
import com.m57.hermescontrol.generated.resources.attachment_error_not_found
import com.m57.hermescontrol.generated.resources.attachment_error_open
import com.m57.hermescontrol.generated.resources.attachment_error_session_expired
import com.m57.hermescontrol.generated.resources.attachment_error_too_large
import com.m57.hermescontrol.generated.resources.chat_model_confirm_default_message
import com.m57.hermescontrol.generated.resources.chat_model_switch_failed
import com.m57.hermescontrol.generated.resources.chat_model_switch_no_session
import com.m57.hermescontrol.generated.resources.chat_model_switch_session_changed
import com.m57.hermescontrol.generated.resources.chat_new_session_failed
import com.m57.hermescontrol.generated.resources.chat_privileged_not_acknowledged
import com.m57.hermescontrol.ui.chat.rail.RailLoadStatus
import com.m57.hermescontrol.ui.chat.rail.railLoadStatusOf
import com.m57.hermescontrol.ui.common.StringResolver
import com.m57.hermescontrol.ui.profiles.ProfileProvider
import com.m57.hermescontrol.ui.sessions.AUTOMATION_SOURCE
import com.m57.hermescontrol.util.HermesLog
import com.m57.hermescontrol.util.isRunningUnderTest
import com.m57.hermescontrol.util.nowMillis
import com.m57.hermescontrol.util.synchronizedOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile

internal const val TAG = "ChatViewModel"
private const val MESSAGE_PAGE_SIZE = 150

/**
 * How long an unacknowledged `session.create` waits before it is retried.
 *
 * `session.create` is fire-and-forget: [HermesWsClient.send] can queue it for a
 * later socket, or drop it outright once credentials were cleared. Chat cannot
 * recover on its own from a dropped create — it has already discarded the old
 * session id — so the request needs its own liveness deadline.
 */
private const val SESSION_CREATE_TIMEOUT_MS = 10_000L

/** Total `session.create` attempts, including the first, before giving up. */
private const val SESSION_CREATE_MAX_ATTEMPTS = 3

/**
 * Gateway error code for "agent does not support active-turn redirect"
 * (hermes-agent `tui_gateway/server.py`, `session.redirect` handler).
 */
private const val REDIRECT_UNSUPPORTED_CODE = 4010
private const val MAX_RETIRED_RESUME_REQUESTS = 64

// ── Rail (REST) ───────────────────────────────────────────────────────────

/** Route caps `limit` at 100; see `hermes_cli/web_routers/sessions.py`. */
private const val RAIL_PAGE_SIZE = 100
private const val RAIL_MAX_PAGES = 3
private const val RAIL_MIN_INTERVAL_MS = 10_000L
private const val RAIL_BACKOFF_MAX_MS = 300_000L

internal data class PendingRpcRequest(
    val method: String,
    val resumeSessionId: String? = null,
    val resumeFence: ResumeFence? = null,
    /** Runtime session that initiated a branch, used to fence late responses. */
    val branchSessionId: String? = null,
    /** Session/text captured for a session.redirect so a 4010 rejection can resend. */
    val redirectSessionId: String? = null,
    val redirectText: String? = null,
    val redirectConnectionBinding: ConnectionBinding? = null,
    /** Attempt generation for a session.create, used to fence retried answers. */
    val createGeneration: Long? = null,
    /**
     * Rename waiting on this resume: once the ack installs a live runtime
     * session, `session.title` succeeds (the gateway resolves titles through
     * the LIVE session map — storage ids 404 with 4001).
     */
    val pendingRenameTitle: String? = null,
    val pendingRenameIcon: String? = null,
    /** Runtime id a session.title was addressed to (error attribution). */
    val titleTargetId: String? = null,
    /** Stored id a session.delete is working on; also drives close-then-delete. */
    val deleteSessionId: String? = null,
    /** True when this resume exists only to learn a runtime sid for a refused delete. */
    val closeAfterResume: Boolean = false,
    /** True once a delete has been retried after closing — stops the recovery looping. */
    val deleteRetried: Boolean = false,
)

internal data class ResumeFence(
    val storageSessionId: String,
    val runtimeSessionId: String?,
    val profileId: String,
    val connectionBinding: ConnectionBinding,
    val generation: Long,
    val transcriptRevision: Long,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModel(
    environment: ChatEnvironment,
    private val resolver: StringResolver,
    private val startCleanup: Boolean,
    repo: ChatPersistenceRepository = ChatPersistenceRepository(environment.messageStore),
    slashUsageStore: SlashUsageStore = environment.slashUsageStore,
    searchDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default,
    /**
     * Retry delay for an unacknowledged `session.create`.
     *
     * `null` (production) means [SESSION_CREATE_TIMEOUT_MS], still subject to
     * the historical unit-test skip. Tests inject a small value to opt into the
     * retry path explicitly, so existing suites keep their previous timing.
     */
    private val sessionCreateRetryDelayMs: Long? = null,
    /** Resolves the profile to scope to; production passes the client, tests pass a constant. */
    private val profileProvider: ProfileProvider = ProfileProvider { null },
    /** Profile changes to follow; empty in tests, the client's selection flow in production. */
    private val profileChanges: Flow<String?> = emptyFlow(),
    /** The selection, synchronously, for per-profile storage keys. */
    private val selectedProfileNow: () -> String? = { DefaultChatApi.selectedProfileId },
    internal val selectedGatewayId: () -> String = {
        AppEnvironment.current.auth.getSelectedProfileId() ?: AuthDefaults.PROFILE_ID
    },
    selectedProfileIds: kotlinx.coroutines.flow.Flow<String> = AppEnvironment.current.auth.selectedGatewayIdFlow,
    private val preferences: PreferencesStore = environment.preferences,
    private val attachmentOpener: AttachmentOpener = environment.attachmentOpener,
    /** IO dispatcher for background work; injectable so tests can drive it deterministically. */
    internal val io: kotlinx.coroutines.CoroutineDispatcher = com.m57.hermescontrol.util.ioDispatcher,
    /** The websocket seam. Production is the singleton; tests pass a fake. */
    private val wsClient: ChatTransport = DefaultChatTransport,
    /** The HTTP seam, same rule. */
    private val api: ChatApi = DefaultChatApi,
) : ViewModel() {
    /** Batch 3 seam: the history reads, out of this class. */
    private val historyLoader = ChatHistoryLoader(api, io)

    // ── Internal state ───────────────────────────────────────────────────
    internal val uiStateMutable = MutableStateFlow(ChatUiState())

    internal val streamingStateMutable = MutableStateFlow(StreamingState())

    /**
     * Maps an in-flight RPC id to the context needed to handle its result.
     *
     * A plain map, not `ConcurrentHashMap`: this ViewModel's state is confined to
     * `viewModelScope` (the main dispatcher), and `java.util.concurrent` does not
     * exist on every Kotlin target.
     */
    internal val pendingRequests = mutableMapOf<String, PendingRpcRequest>()
    private val resumeRequestLock = Any()
    private val retiredResumeRequests = LinkedHashSet<String>()

    /** Runtime TUI session returned by session.resume; Desktop storage keeps the original ID. */
    internal var runtimeSessionId: String? = null
    private var loadedMessageOffset = 0
    private var latestPaging = false

    private var conversationGeneration = 0L
    private val historyLoadJobs = mutableSetOf<Job>()

    private var cacheLoadCounter = 0L

    @Volatile private var activeCacheLoadOwner: Long? = null
    private var cacheLoadJob: Job? = null
    private var activeCacheMessageIds: Set<String> = emptySet()

    val streamingState: StateFlow<StreamingState> = streamingStateMutable.asStateFlow()

    /** Tracks the auto-clear coroutine for reaction animations. */
    internal var reactionClearJob: Job? = null

    /**
     * Expiry timers for live approval cards. Keyed by message and complete transport binding,
     * because request ids can be reused; see [ApprovalExpiryScheduler].
     */
    internal val approvalExpiry = ApprovalExpiryScheduler(viewModelScope)

    // ── Session persistence ──────────────────────────────────────────────
    internal val repo: ChatPersistenceRepository = repo
    private val slashUsageStore: SlashUsageStore = slashUsageStore
    private val slashUsageProfileIds = selectedProfileIds.distinctUntilChanged()
    private val slashDispatcher = SlashCommandDispatcher()
    private val searchDelegate =
        ChatSearchDelegate(
            scope = viewModelScope,
            uiState = uiStateMutable,
            dispatcher = searchDispatcher,
        )
    private val attachmentsDelegate = ChatAttachmentsDelegate(uiState = uiStateMutable)

    /**
     * Model options cached from GET /api/model/options so the in-session model
     * picker (issue #589) opens instantly when the user types /model or taps the
     * top-bar chip. Preloaded at GatewayReady; refreshed on open if empty.
     */
    private var cachedModelOptions: List<ModelProvider> = emptyList()

    internal val streamingController =
        ChatStreamingController(
            scope = viewModelScope,
            uiState = uiStateMutable,
            streamingState = streamingStateMutable,
            isCurrentSession = { sessionId -> isCurrentSession(sessionId) },
            isTestEnvironment = { isTestEnvironment() },
        )

    // ── Public state ─────────────────────────────────────────────────────

    /**
     * Combined UI state: merges internal state with the WS connection status
     * flow so there is a single source of truth for connection state.
     */
    val uiState: StateFlow<ChatUiState> =
        combine(
            uiStateMutable,
            wsClient.connectionStatus,
        ) { state, connStatus ->
            state.copy(
                connection = state.connection.copy(connectionStatus = connStatus),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            uiStateMutable.value,
        )

    /**
     * Session ID to resume when the WebSocket connects. Set synchronously by
     * [ChatScreen] via `SideEffect` during composition — before any WS event
     * can be processed. This prevents the race where [GatewayReady] fires
     * before ChatScreen's `LaunchedEffect` can call [switchSession], causing
     * [createNewSession] to create an empty chat that overwrites the
     * notification session (issue #240).
     */
    var initialSessionId: String? = null

    init {
        viewModelScope.launch(io) {
            // Switching the gateway profile re-scopes the listing: refresh the rail
            // at once instead of waiting for the next foreground or socket signal.
            // A "profile" switch in this app is a *connection* switch: a different
            // server, with a different session store. Drop the old server's rows at
            // once and re-list from the new one instead of showing stale sessions.
            AppEnvironment.current.auth.selectedGatewayIdFlow
                .drop(1)
                .distinctUntilChanged()
                .collect {
                    // A profile name is per gateway: carrying it across a connection switch
                    // would scope every request to a profile the new gateway may not have.
                    api.clearSelectedProfile()
                    api.currentProfileName()
                    uiStateMutable.update {
                        it.copy(
                            rail = it.rail.copy(sessions = emptyList(), railStatus = RailLoadStatus.Loading),
                        )
                    }
                    requestRailRefresh(force = true)
                }
        }
        viewModelScope.launch(io) {
            // Learn the serving profile once, then scope every request to it. The client
            // rebuilds on change, so the rail can never mix two profiles' rows.
            if (api.selectedProfileId == null) {
                val resolved =
                    try {
                        profileProvider.current()
                    } catch (e: Exception) {
                        // A screen must survive an unreachable gateway: no profile is a
                        // degraded scope, not a crash.
                        HermesLog.e("ChatVM", "Profile resolution failed", e)
                        null
                    }
                resolved?.let(api::setSelectedProfile)
            }
        }
        viewModelScope.launch(io) {
            uiStateMutable.collect { state ->
                state.currentSessionId?.let { id -> transcriptCache.put(selectedProfileNow(), id, state.messages) }
            }
        }
        viewModelScope.launch(io) {
            wsClient.connectionStatus.collect { status ->
                if (status == ConnectionStatus.CONNECTED) {
                    uiStateMutable.update { it.copy(connection = it.connection.afterFirstConnected()) }
                }
            }
        }
        viewModelScope.launch(io) {
            profileChanges.collect {
                // The socket is scoped at handshake: re-open it so chat continues in the
                // profile the user selected instead of the previous one. Only when a socket
                // is already up — otherwise cold start would flap for nothing.
                if (wsClient.connectionStatus.value == ConnectionStatus.CONNECTED) {
                    wsClient.disconnect()
                    wsClient.connect()
                }
                resetConversationForProfileChange()
                uiStateMutable.update { state ->
                    state.copy(
                        rail = state.rail.copy(sessions = emptyList(), railStatus = RailLoadStatus.Loading),
                    )
                }
                requestRailRefresh(force = true)
                // The new profile has its own history; resume its last session if it has one.
                // With none, the pane stays empty and the rail is the way in.
                lastSession()?.let { remembered -> switchSession(remembered) }
            }
        }

        refreshSettings()

        viewModelScope.launch {
            slashUsageProfileIds.flatMapLatest(slashUsageStore::counts).collect { counts ->
                uiStateMutable.update {
                    it.copy(
                        composer = it.composer.afterSlashUsageCounts(counts),
                    )
                }
            }
        }

        connectWebSocket(setLoading = false)
        viewModelScope.launch {
            wsClient.events.collect { event ->
                try {
                    handleWsEvent(event)
                } catch (e: Exception) {
                    HermesLog.e("ChatVM", "Uncaught in event loop", e)
                    uiStateMutable.update {
                        it.copy(
                            transcript = it.transcript.copy(isLoading = false),
                        )
                    }
                }
            }
        }
        // B7 (Jun 30 2026, kanban t_connection_loading): clear loading state on connection failure or status change
        viewModelScope.launch {
            wsClient.connectionStatus.collect { status ->
                if (status == ConnectionStatus.DISCONNECTED ||
                    status == ConnectionStatus.RECONNECTING ||
                    status == ConnectionStatus.NO_NETWORK ||
                    status == ConnectionStatus.AUTH_EXPIRED
                ) {
                    retireSync()
                    uiStateMutable.value.currentSessionId?.let(repo::invalidateReplacementWrites)
                    conversationGeneration++
                    invalidateResumeRequests()
                    clearPrivilegedControls()
                    uiStateMutable.update {
                        it.copy(
                            transcript = it.transcript.copy(isLoading = false, isSessionReady = false),
                        )
                    }
                    // Fail any in-flight awaited RPCs so callers don't hang
                    // across the disconnect (delegated to HermesWsClient, issue #526).
                    wsClient.rejectAllPending()
                }
            }
        }
        if (wsClient.connectionStatus.value == ConnectionStatus.CONNECTED) {
            // Deferred: when the WS is already CONNECTED mid-construction (fast
            // e2e mock retry), handleGatewayReady would run before later-declared
            // property initializers (the preferences store) execute —
            // the lazy delegate is still null → NPE. Posting to viewModelScope
            // runs it after construction completes, matching the async
            // gateway.ready arrival in production.
            // `launch` may run immediately on the main dispatcher while the
            // constructor is still initializing later properties. Post one
            // message-loop turn so all property initializers have completed.
            // Dispatchers.Main (NOT viewModelScope's Main.immediate) so the body
            // really is deferred one queue turn. Using the dispatcher instead of
            // a Looper Handler keeps the ViewModel free of an android.os.Looper
            // dependency, which a plain JVM unit test can then drive.
            viewModelScope.launch(Dispatchers.Main) {
                handleGatewayReady()
            }
        }
    }

    // ── Connection ───────────────────────────────────────────────────────

    private fun connectWebSocket(setLoading: Boolean = false) {
        // In loopback (token) mode the session token is the WS credential and
        // must be present before connecting. In gated (ticket) mode the ticket
        // is minted fresh by HermesWsClient.refreshWsTicketIfNeeded() from the
        // persisted session cookie, so getToken() is expected to be empty here
        // and must NOT block the connect (issue #640: chat showed "reconnect"
        // immediately after basic-auth login because this guard returned early).
        val isGated =
            runCatching { authStore.isGatedMode() }
                .onFailure {
                    // Falling back to token auth here makes the connect return early for a
                    // cookie-only dashboard — the same failure shape as issue #640, where
                    // chat showed "reconnect" straight after login. Never silent.
                    HermesLog.w(TAG, "authStore.isGatedMode() failed; assuming token auth: ${it.message}")
                }
                .getOrNull() ?: false
        if (!isGated) {
            val token = authStore.getToken() ?: return
            if (token.isBlank()) return
        }

        // Don't disturb an already-working (or already-recovering) connection.
        // HermesWsClient is a global singleton shared by every tab; the chat tab
        // is recreated on every open, so calling connect() here must be a no-op
        // unless the singleton is in a terminal state. Re-entering connect() while
        // it is CONNECTING/RECONNECTING races the in-flight socket and can leave
        // the status stuck on RECONNECTING (see HermesWsClient.connect).
        val status = wsClient.connectionStatus.value
        if (status == ConnectionStatus.CONNECTING ||
            status == ConnectionStatus.RECONNECTING ||
            status == ConnectionStatus.AUTH_EXPIRED
        ) {
            return
        }

        if (setLoading) {
            uiStateMutable.update {
                it.copy(
                    transcript = it.transcript.copy(isLoading = true),
                )
            }
        }

        viewModelScope.launch(io) {
            wsClient.connect()
        }

        // B7 (Jun 30 2026, kanban t_connection_loading): safety timeout to clear spinner if connection hangs
        if (!isTestEnvironment()) {
            viewModelScope.launch {
                delay(10_000L)
                if (uiStateMutable.value.isLoading) {
                    uiStateMutable.update {
                        it.copy(
                            transcript = it.transcript.copy(isLoading = false),
                        )
                    }
                }
            }
        }
    }

    // ── WS Event Handling ────────────────────────────────────────────────

    internal fun handleGatewayReady() {
        uiStateMutable.update {
            it.copy(
                transcript = it.transcript.copy(isLoading = false),
            )
        }
        addSystemMessage("Connected to Hermes", transient = true)
        loadSessions()
        fetchCommandCatalog()
        fetchModelContextLength()
        preloadModelOptions()
        val currentId = uiStateMutable.value.currentSessionId
        if (currentId != null) {
            val resumeFence = captureResumeFence(currentId)
            viewModelScope.launch(io) {
                if (resumeFence != null && isResumeFenceCurrent(resumeFence)) {
                    wsClient.send(
                        WsMethods.SESSION_RESUME,
                        mapOf("session_id" to currentId, "omit_messages" to true),
                        onSent = { id -> trackResumeRequest(id, resumeFence) },
                    )
                }
            }
            loadSessionMessages(currentId)
        } else {
            val initial = initialSessionId
            if (!initial.isNullOrBlank()) {
                initialSessionId = null
                switchSession(initial)
            } else {
                // Likivik patch: resume the last-open session across launches
                // instead of creating a new one every cold open. If there is
                // no last session, do NOT auto-create — leave the chat pane
                // empty and let the user pick from the rail (which
                // loadSessions() above populates). The send path is guarded
                // on currentSessionId != null, so a null selection safely
                // disables input until a session is chosen.
                val last = lastSession()
                if (!last.isNullOrBlank()) {
                    switchSession(last)
                }
                // else: no auto-create. Stay on the empty state.
            }
        }
    }

    // ── Message streaming ────────────────────────────────────────────────

    /**
     * Checks if an incoming WS event belongs to the currently active
     * session. Returns true if the event should be processed.
     */
    internal fun isCurrentSession(eventSessionId: String?): Boolean {
        // If the event has no session ID, process it (legacy compatibility)
        if (eventSessionId == null) return true
        return eventSessionId == runtimeSessionId || eventSessionId == uiStateMutable.value.currentSessionId
    }

    // ── RPC response handling ────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    internal fun handleDispatchResult(result: Any?) {
        when (val outcome = classifyDispatchResult(result)) {
            null -> return
            is DispatchOutcome.SendPrompt -> submitPrompt(outcome.message)
            is DispatchOutcome.ShowOutput -> addAssistantMessage(outcome.output)
            is DispatchOutcome.FollowAlias -> handleSlashCommand(outcome.target)
        }
    }

    internal fun handleRpcError(
        id: String,
        error: Any?,
    ) {
        val request = pendingRequests.remove(id) ?: return
        val method = request.method
        val errorCode =
            when (error) {
                is JsonRpcError -> error.code
                is Map<*, *> -> (error["code"] as? Number)?.toInt()
                else -> null
            }
        val errorMsg =
            when (error) {
                is JsonRpcError -> error.message
                is Map<*, *> -> error["message"] as? String ?: error.toString()
                else -> error.toString()
            }

        // A gateway whose running agent cannot steer an in-flight turn answers
        // session.redirect with 4010. The user's text is already on screen but
        // was never delivered, so resend it as a normal prompt rather than
        // surfacing an error and losing the message (issue #710).
        if (method == WsMethods.SESSION_REDIRECT) {
            val sessionId = request.redirectSessionId
            val text = request.redirectText
            val connectionBinding = request.redirectConnectionBinding
            if (errorCode == REDIRECT_UNSUPPORTED_CODE &&
                sessionId != null &&
                !text.isNullOrBlank() &&
                connectionBinding != null
            ) {
                HermesLog.d(TAG, "session.redirect unsupported — resending as prompt.submit")
                // ChatWsEventReducer.onRpcError already ran for this event and
                // parked a generic error banner. The rejection is recoverable,
                // so clear it — the user should see the resent turn, not a
                // failure they cannot act on.
                uiStateMutable.update {
                    it.copy(
                        transcript = it.transcript.copy(errorMessage = null),
                    )
                }
                viewModelScope.launch(io) {
                    wsClient.sendMessageForConnection(
                        binding = connectionBinding,
                        sessionId = sessionId,
                        text = text,
                        onSent = { retryId -> trackRequest(retryId, WsMethods.PROMPT_SUBMIT) },
                    )
                }
                return
            }
        }

        // Surface error in UI (these are server-pushed RpcError for
        // fire-and-forget RPCs — awaited RPCs handle their own failure
        // via the HermesWsClient.request() deferred).
        // 4023: the gateway refuses to delete a session whose runtime is still
        // live. Close that runtime (learned via a resume) and retry the delete
        // exactly once — `deleteRetried` stops this from looping.
        if (method == WsMethods.SESSION_DELETE && errorCode == 4023) {
            val stored = request.deleteSessionId
            if (stored != null && !request.deleteRetried) {
                HermesLog.i(TAG, "session.delete refused (still active) — closing runtime, then retrying: $stored")
                uiStateMutable.update {
                    it.copy(
                        transcript = it.transcript.copy(errorMessage = null),
                    )
                }
                startCloseThenDelete(stored)
                return
            }
        }

        // Failures of the rename/delete RPCs get an action-shaped message: the
        // optimistic rail state reverts on the next session.list refresh, so the
        // user has to know the server refused, and why.
        if (method == WsMethods.SESSION_TITLE) {
            HermesLog.e(TAG, "session.title failed ($errorCode): $errorMsg target=${request.titleTargetId}")
            uiStateMutable.update {
                it.copy(
                    transcript = it.transcript.copy(isLoading = false, errorMessage = "Rename failed: $errorMsg"),
                )
            }
            return
        }
        if (method == WsMethods.SESSION_DELETE) {
            HermesLog.e(TAG, "session.delete failed ($errorCode): $errorMsg target=${request.deleteSessionId}")
            uiStateMutable.update {
                it.copy(
                    transcript = it.transcript.copy(isLoading = false, errorMessage = "Delete failed: $errorMsg"),
                )
            }
            return
        }

        // Likivik patch: a failed rename-resume means the rename is LOST —
        // surface it (the optimistic rail title reverts on the next
        // session.list refresh, so the user must know why).
        if (request.pendingRenameTitle != null && method == WsMethods.SESSION_RESUME) {
            HermesLog.e(TAG, "rename-resume failed ($errorCode): $errorMsg")
        }

        uiStateMutable.update {
            it.copy(
                transcript = it.transcript.copy(isLoading = false, errorMessage = "Error ($method): $errorMsg"),
            )
        }
    }

    // ── Send message ─────────────────────────────────────────────────────

    /**
     * Send a user prompt, uploading any pending attachments to the backend
     * first via their dedicated RPC methods.
     *
     * Flow:
     * 1. Snapshot pending attachments (then clear them from UI)
     * 2. Add user message to UI + DB immediately (optimistic UX)
     * 3. For each image → await `image.attach_bytes` (requires session_id)
     * 4. For each file → await `file.attach` (requires session_id), collect @file: refs
     * 5. Send `prompt.submit` with text + @file: refs — images auto-picked up by backend
     */
    fun sendMessage(text: String): Boolean {
        val state = uiStateMutable.value
        val dispatchGeneration = conversationGeneration
        val boundConnection = wsClient.connectionBinding(selectedGatewayId())

        // Admission and routing are decided by a pure rule (SendPlan.kt); everything below is effect.
        when (
            val start =
                planSend(
                    text = text,
                    hasAttachments = state.pendingAttachments.isNotEmpty(),
                    sessionReady = state.isSessionReady,
                    hasStorageSessionId = state.currentSessionId != null,
                    hasRuntimeSessionId = runtimeSessionId != null,
                    hasConnection = boundConnection != null,
                )
        ) {
            SendStart.Refused -> return false
            SendStart.OpenModelPicker -> {
                // Issue #589: a bare "/model" opens the picker instead of requiring
                // the user to hand-type the provider/model.
                openModelPicker()
                return true
            }

            is SendStart.DispatchCommand -> {
                handleSlashCommand(start.command)
                return true
            }

            SendStart.Prepare -> Unit
        }

        val storageSessionId = state.currentSessionId ?: return false
        val agentSessionId = runtimeSessionId ?: return false
        val dispatchConnection = boundConnection ?: return false

        // Snapshot + clear attachments so the input bar empties immediately
        val attachments = uiStateMutable.value.pendingAttachments.toList()
        clearAttachments()

        // Read the streaming flag BEFORE the optimistic update below sets
        // isAgentTyping = true, otherwise every send would look mid-turn and
        // route through session.redirect (issue #710).
        val wasStreaming = uiStateMutable.value.isAgentTyping

        val userMessage =
            ChatMessage(
                role = MessageRole.USER,
                content = text,
                attachments = if (attachments.isNotEmpty()) attachments else null,
            )

        // Update UI immediately
        uiStateMutable.update { state ->
            state.copy(
                transcript = state.transcript.copy(messages = state.messages + userMessage, isAgentTyping = true),
            )
        }

        // Persist under the original Desktop session ID.
        val acceptedRevision = repo.replacementGeneration(storageSessionId)
        viewModelScope.launch(io) {
            repo.persistMessage(userMessage, storageSessionId, acceptedRevision)
        }

        // Upload attachments then submit prompt
        viewModelScope.launch(io) {
            val fileRefs = mutableListOf<String>()

            for (attachment in attachments) {
                val b64 = readContentUriBase64(attachment.uri)
                if (b64 == null) {
                    HermesLog.w(TAG, "Skipping unreadable attachment")
                    continue
                }

                try {
                    if (attachment.isImage) {
                        // Await so the backend stages the image into
                        // session["attached_images"] BEFORE prompt.submit runs
                        // (a fire-and-forget send raced prompt.submit and the
                        // image was dropped). Requires session_id or the gateway
                        // 4001s "session not found" (desktop passes it too).
                        val response =
                            sendRpcAndAwait(
                                method = WsMethods.IMAGE_ATTACH_BYTES,
                                params =
                                    mapOf(
                                        "session_id" to agentSessionId,
                                        "content_base64" to "data:${attachment.mimeType};base64,$b64",
                                        "filename" to attachment.name,
                                        "ext" to attachment.fileExtension,
                                    ),
                            )
                        if (response != null) {
                            val result = if (response is JsonElement) response.toAny() else response
                            val ok = (result as? Map<*, *>)?.get("attached") as? Boolean
                            if (ok != true) {
                                HermesLog.w(TAG, "Image attachment request failed")
                            }
                        }
                    } else {
                        // Await the @file: ref text so we can embed it in the prompt.
                        // file.attach also requires session_id or the gateway 4001s
                        // "session not found" (same resolver as image.attach_bytes).
                        sendRpcAndAwait(
                            method = WsMethods.FILE_ATTACH,
                            params =
                                mapOf(
                                    "session_id" to agentSessionId,
                                    "data_url" to "data:${attachment.mimeType};base64,$b64",
                                    "name" to attachment.name,
                                ),
                        )?.let { response ->
                            val result = if (response is JsonElement) response.toAny() else response
                            val refText = (result as? Map<*, *>)?.get("ref_text") as? String
                            if (!refText.isNullOrBlank()) fileRefs.add(refText)
                        }
                    }
                } catch (e: Exception) {
                    HermesLog.e(TAG, "Failed to upload attachment (${e::class.simpleName})")
                    uiStateMutable.update {
                        it.copy(
                            transcript = it.transcript.copy(errorMessage = "Upload failed: ${attachment.name}"),
                        )
                    }
                }
            }

            // Build prompt text — prepend @file: refs for non-image files
            val fullText =
                if (fileRefs.isEmpty()) {
                    text
                } else {
                    fileRefs.joinToString("\n") +
                        if (text.isNotBlank()) "\n\n$text" else ""
                }

            // Attachment preparation can suspend while the active conversation
            // changes. Fence by generation as well as ID so switching away and
            // back to the same session cannot dispatch the stale prompt.
            if (dispatchGeneration != conversationGeneration) return@launch

            // While a turn is still streaming and the prompt carries no
            // attachments, steer the in-flight turn via session.redirect
            // instead of queueing a second prompt.submit (issue #710).
            // session.redirect is text-only, so any attachment send stays on
            // the normal path. A backend that cannot redirect answers 4010;
            // handleRpcError re-sends the text as a normal prompt so the
            // typed message is never silently lost.
            if (chooseDelivery(wasStreaming = wasStreaming, hasAttachments = attachments.isNotEmpty()) ==
                Delivery.Redirect
            ) {
                wsClient.sendRedirectForConnection(
                    binding = dispatchConnection,
                    sessionId = agentSessionId,
                    text = fullText,
                    onSent = { id ->
                        trackRedirectRequest(id, agentSessionId, fullText, dispatchConnection)
                    },
                )
            } else {
                wsClient.sendMessageForConnection(
                    binding = dispatchConnection,
                    sessionId = agentSessionId,
                    text = fullText,
                    onSent = { id -> trackRequest(id, WsMethods.PROMPT_SUBMIT) },
                )
            }
        }
        return true
    }

    /**
     * Read an attachment reference and encode it to base64 for the gateway payload.
     *
     * The platform reader is installed by the host; on a target without one this
     * returns null and the attachment is skipped rather than failing the send.
     */
    private suspend fun readContentUriBase64(uriString: String): String? = attachmentReader?.readBase64(uriString)

    /**
     * Send a JSON-RPC call and suspend until the response arrives, delegating the deferred + 120s
     * timeout to the injected transport (issue #526).
     *
     * This used to call `HermesWsClient.request` directly, which was the last place the ViewModel
     * touched the singleton: attachment sends could not be driven by a fake, so the ordering that
     * issues #526 and #710 were about — staging an image *before* `prompt.submit` — had no test.
     *
     * Throws [HermesWsClient.HermesRpcException] on RPC error, or
     * [kotlinx.coroutines.TimeoutCancellationException] if the server never answers in time.
     */
    private suspend fun sendRpcAndAwait(
        method: String,
        params: Map<String, Any>,
    ): Any? = wsClient.request(method, params).await()

    // ── Attachment management ─────────────────────────────────────────────

    /**
     * Add a picked file as a pending attachment.
     * [uri] should be a content:// URI string; the ViewModel will read
     * the content and encode it for sending.
     */
    fun addAttachment(
        uri: String,
        name: String,
        mimeType: String,
        size: Long,
    ) = attachmentsDelegate.addAttachment(uri, name, mimeType, size)

    fun addAttachments(attachments: List<Attachment>) = attachmentsDelegate.addAttachments(attachments)

    fun removeAttachment(index: Int) = attachmentsDelegate.removeAttachment(index)

    fun clearAttachments() = attachmentsDelegate.clearAttachments()

    private fun handleSlashCommand(command: String) {
        val result = slashDispatcher.dispatch(command)
        val displayContent =
            if (result is SlashResult.QueuePrompt) result.displayContent else command
        val userMsg = ChatMessage(role = MessageRole.USER, content = displayContent)
        val sessionId = uiStateMutable.value.currentSessionId

        uiStateMutable.update {
            it.copy(
                transcript = it.transcript.copy(messages = it.messages + userMsg),
            )
        }

        // Persist — OUTSIDE update{}
        if (sessionId != null) {
            val acceptedRevision = repo.replacementGeneration(sessionId)
            viewModelScope.launch(io) {
                repo.persistMessage(userMsg, sessionId, acceptedRevision)
            }
        }

        // Block desktop/CLI-only + TUI-only commands that don't function on
        // mobile (issue #576, deliverable #3). These are also hidden from the
        // suggestion menu, but a user can still type one — intercept it here
        // (before any RPC fires) with a clear message instead of a doomed call.
        if (CommandBlocklist.contains(command)) {
            addAssistantMessage(
                "${command.split(" ", limit = 2)[0]} is not supported on mobile",
            )
            return
        }

        when (result) {
            is SlashResult.Interrupt -> {
                interruptSession { recordAcceptedSlash(command) }
            }

            is SlashResult.NewSession -> {
                createNewSession(onDispatched = { recordAcceptedSlash(command) })
            }

            is SlashResult.SessionBranch -> {
                branchSession(command) { recordAcceptedSlash(command) }
            }

            is SlashResult.ModelSwitch -> {
                handleModelSwitch(command, recordUsage = true)
            }

            is SlashResult.OpenHistory -> {
                uiStateMutable.update {
                    it.copy(
                        composer = it.composer.afterOpenHistoryRequest(true),
                    )
                }
                recordAcceptedSlash(command)
            }

            is SlashResult.QueuePrompt -> {
                handleQueueCommand(command)
            }

            is SlashResult.Undo -> {
                handleUndoCommand(result.count, command)
            }

            is SlashResult.RpcDispatch -> {
                dispatchViaRpc(command)
            }
        }
    }

    private data class UndoFence(
        val storageSessionId: String,
        val runtimeSessionId: String,
        val profileId: String,
        val connectionBinding: ConnectionBinding,
        val generation: Long,
        val persistenceGeneration: Long,
    )

    private data class CacheLoadFence(
        val storageSessionId: String,
        val runtimeSessionId: String?,
        val profileId: String,
        val generation: Long,
    )

    private fun captureCacheLoadFence(sessionId: String) =
        CacheLoadFence(
            storageSessionId = sessionId,
            runtimeSessionId = runtimeSessionId,
            profileId = selectedGatewayId(),
            generation = conversationGeneration,
        )

    private fun isCacheLoadCurrent(
        fence: CacheLoadFence,
        owner: Long,
    ): Boolean =
        activeCacheLoadOwner == owner &&
            fence.generation == conversationGeneration &&
            fence.profileId == selectedGatewayId() &&
            fence.storageSessionId == uiStateMutable.value.currentSessionId &&
            fence.runtimeSessionId == runtimeSessionId

    private val chatHistory: ChatHistory by lazy {
        ChatHistory(
            io = io,
            launch = ::launchHistoryLoad,
            fetchPage = { sessionId, offset, limit, order -> fetchMessagePage(sessionId, offset, limit, order) },
            mapMessages = { sessionId, raw, offset, latest -> mapServerMessages(sessionId, raw, offset, latest) },
            persist = { messages, sessionId, revision, stillCurrent ->
                repo.persistMessagesIfCurrent(messages, sessionId, revision, stillCurrent)
            },
            captureFence = ::captureHistoryLoadFence,
            write = ::applyHistoryUpdate,
        )
    }

    private fun applyHistoryUpdate(update: ChatHistoryUpdate) {
        when (update) {
            ChatHistoryUpdate.OlderStarted ->
                uiStateMutable.update { state ->
                    state.copy(
                        transcript = TranscriptReducer.reduce(state.transcript, TranscriptIntent.OlderLoadStarted),
                    )
                }

            is ChatHistoryUpdate.OlderLoaded ->
                uiStateMutable.update { current ->
                    loadedMessageOffset = update.effectiveOffset
                    current.copy(
                        transcript =
                            TranscriptReducer.reduce(
                                current.transcript,
                                TranscriptIntent.OlderLoaded(
                                    older = update.older,
                                    useLatestPaging = update.useLatestPaging,
                                    returned = update.returned,
                                    effectiveOffset = update.effectiveOffset,
                                    limit = update.limit,
                                ),
                            ),
                    )
                }

            is ChatHistoryUpdate.MessageCountKnown ->
                uiStateMutable.update { current ->
                    current.copy(
                        rail =
                            current.rail.copy(
                                sessions =
                                    current.sessions.map {
                                        if (it.id == update.sessionId) it.copy(messageCount = update.count) else it
                                    },
                            ),
                    )
                }

            ChatHistoryUpdate.LoadStarted ->
                uiStateMutable.update { state ->
                    state.copy(
                        transcript = TranscriptReducer.reduce(state.transcript, TranscriptIntent.LoadStarted),
                    )
                }

            is ChatHistoryUpdate.LoadSucceeded -> {
                val cachedIds = activeCacheMessageIds
                retireCacheLoad()
                latestPaging = update.useLatestPaging
                uiStateMutable.update { state ->
                    val hasResumeHistory = state.messages.any { it.id.startsWith("resume-${update.sessionId}-") }
                    state.copy(
                        transcript =
                            TranscriptReducer.reduce(
                                state.transcript,
                                TranscriptIntent.HistoryLoaded(
                                    authoritative = update.messages,
                                    baselineIds = update.baselineIds,
                                    cachedIds = cachedIds,
                                    hasResumeHistory = hasResumeHistory,
                                    useLatestPaging = update.useLatestPaging,
                                    returned = update.returned,
                                    offset = update.offset,
                                    pageSize = MESSAGE_PAGE_SIZE,
                                ),
                            ),
                    )
                }
                val hasRestHistory =
                    uiStateMutable.value.messages.any { serverMessageIndex(it.id, update.sessionId) != null }
                if (update.messages.isNotEmpty() && hasRestHistory) loadedMessageOffset = update.offset
            }

            is ChatHistoryUpdate.LoadFailed ->
                uiStateMutable.update { state ->
                    state.copy(
                        transcript =
                            TranscriptReducer.reduce(
                                state.transcript,
                                TranscriptIntent.HistoryFailed(reason = update.reason, nowMs = nowMs()),
                            ),
                    )
                }

            ChatHistoryUpdate.LoadFinished -> Unit

            ChatHistoryUpdate.SyncStarted -> Unit

            is ChatHistoryUpdate.SyncMerged ->
                uiStateMutable.update { current ->
                    val merged =
                        mergeSyncedMessages(
                            current = current.messages,
                            incoming = update.incoming,
                            isServerMessage = { id ->
                                serverMessageIndex(id, update.sessionId) != null
                            },
                        )
                    if (sameTranscript(current.messages, merged)) {
                        current
                    } else {
                        current.copy(
                            transcript =
                                current.transcript.copy(
                                    messages = merged,
                                    todos = restoredTodos(current.todos, merged),
                                ),
                        )
                    }
                }

            ChatHistoryUpdate.SyncFinished -> Unit

            ChatHistoryUpdate.OlderFinished ->
                uiStateMutable.update { state ->
                    state.copy(transcript = state.transcript.copy(isLoadingOlder = false))
                }
        }
    }

    private fun captureHistoryLoadFence(sessionId: String): HistoryLoadFence? {
        val profileId = selectedGatewayId()
        val binding = wsClient.connectionBinding(profileId) ?: return null
        val generation = conversationGeneration
        val runtime = runtimeSessionId
        val revision = repo.replacementGeneration(sessionId)
        return HistoryLoadFence(sessionId = sessionId, transcriptRevision = revision) {
            generation == conversationGeneration &&
                profileId == selectedGatewayId() &&
                sessionId == uiStateMutable.value.currentSessionId &&
                runtime == runtimeSessionId &&
                revision == repo.replacementGeneration(sessionId) &&
                wsClient.isConnectionBindingCurrent(binding)
        }
    }

    private fun isHistoryLoadFenceCurrent(fence: HistoryLoadFence): Boolean = fence.isCurrent()

    private fun invalidateHistoryRefresh() {
        chatHistory.retireRefresh()
    }

    private fun retireCacheLoad() {
        activeCacheLoadOwner = null
        cacheLoadCounter++
        cacheLoadJob?.cancel()
        cacheLoadJob = null
        activeCacheMessageIds = emptySet()
    }

    private fun launchHistoryLoad(block: suspend () -> Unit): Job {
        val job = viewModelScope.launch { block() }
        synchronizedOn(historyLoadJobs) { historyLoadJobs += job }
        job.invokeOnCompletion { synchronizedOn(historyLoadJobs) { historyLoadJobs -= job } }
        return job
    }

    private fun invalidateHistoryLoads(sessionId: String) {
        invalidateHistoryRefresh()
        repo.invalidateReplacementWrites(sessionId)
        invalidateResumeRequests()
        val jobs = synchronizedOn(historyLoadJobs) { historyLoadJobs.toList().also { historyLoadJobs.clear() } }
        jobs.forEach(Job::cancel)
    }

    private fun handleUndoCommand(
        count: String,
        command: String,
    ) {
        val storageSessionId = uiStateMutable.value.currentSessionId ?: return
        val agentSessionId = runtimeSessionId ?: return
        val profileId = selectedGatewayId()
        val connectionBinding = wsClient.connectionBinding(profileId) ?: return
        val fence =
            UndoFence(
                storageSessionId = storageSessionId,
                runtimeSessionId = agentSessionId,
                profileId = profileId,
                connectionBinding = connectionBinding,
                generation = conversationGeneration,
                persistenceGeneration = repo.replacementGeneration(storageSessionId),
            )
        viewModelScope.launch(io) {
            try {
                val result =
                    wsClient
                        .requestForConnection(
                            fence.connectionBinding,
                            WsMethods.COMMAND_DISPATCH,
                            mapOf("name" to "undo", "arg" to count, "session_id" to agentSessionId),
                        ).await()
                if (!isUndoFenceCurrent(fence)) return@launch
                recordAcceptedSlash(command)
                handleUndoResult(result, fence)
            } catch (e: Exception) {
                if (isUndoFenceCurrent(fence)) {
                    addAssistantMessage(e.message ?: "Failed to undo.")
                }
            }
        }
    }

    private fun isUndoFenceCurrent(fence: UndoFence): Boolean =
        fence.generation == conversationGeneration &&
            fence.profileId == selectedGatewayId() &&
            fence.storageSessionId == uiStateMutable.value.currentSessionId &&
            fence.runtimeSessionId == runtimeSessionId &&
            wsClient.isConnectionBindingCurrent(fence.connectionBinding)

    private suspend fun handleUndoResult(
        result: Any?,
        fence: UndoFence,
    ) {
        val map = result as? Map<*, *> ?: return
        if (map["type"] != "prefill") {
            if (isUndoFenceCurrent(fence)) handleDispatchResult(result)
            return
        }
        val prefill = map["message"] as? String ?: ""
        val notice = (map["notice"] as? String).orEmpty().ifBlank { "Rewound conversation" }
        invalidateHistoryLoads(fence.storageSessionId)
        val authoritativeFence =
            fence.copy(persistenceGeneration = repo.replacementGeneration(fence.storageSessionId))
        val transcript = fetchCompleteSessionHistory(authoritativeFence) ?: return
        if (!isUndoFenceCurrent(authoritativeFence)) return
        val feedback = ChatMessage(role = MessageRole.SYSTEM, content = notice)
        val reconciled = transcript + feedback
        if (
            !repo.replaceMessagesIfCurrent(
                reconciled,
                fence.storageSessionId,
                authoritativeFence.persistenceGeneration,
            )
        ) {
            return
        }
        if (!isUndoFenceCurrent(authoritativeFence)) return
        loadedMessageOffset = 0
        latestPaging = true
        uiStateMutable.update { state ->
            if (!isUndoFenceCurrent(fence)) return@update state
            state.copy(
                transcript =
                    state.transcript.copy(
                        messages = reconciled,
                        todos = restoredTodos(emptyList(), transcript),
                        hasOlderMessages = false,
                        isLoadingOlder = false,
                    ),
                composer = state.composer.afterPendingPrefill(prefill),
            )
        }
    }

    private suspend fun fetchCompleteSessionHistory(fence: UndoFence): List<ChatMessage>? {
        val pages = mutableListOf<List<ChatMessage>>()
        var offset = 0
        while (true) {
            if (!isUndoFenceCurrent(fence)) return null
            val result = fetchMessagePage(fence.storageSessionId, offset, MESSAGE_PAGE_SIZE, order = "latest")
            if (!isUndoFenceCurrent(fence)) return null
            if (result !is NetworkResult.Success) return null
            val pagination = result.data.pagination
            val effectiveOffset = pagination?.offset ?: offset
            pages += mapServerMessages(fence.storageSessionId, result.data.messages.orEmpty(), effectiveOffset)
            val returned = pagination?.returned ?: result.data.messages.size
            val total = pagination?.total
            if (returned <= 0 ||
                (total != null && effectiveOffset + returned >= total) ||
                (total == null && returned < MESSAGE_PAGE_SIZE)
            ) {
                break
            }
            offset = effectiveOffset + returned
        }
        return if (isUndoFenceCurrent(fence)) pages.asReversed().flatten().distinctBy { it.id } else null
    }

    fun consumePendingPrefill() {
        uiStateMutable.update {
            it.copy(
                composer = it.composer.afterPendingPrefill(null),
            )
        }
    }

    private fun handleQueueCommand(command: String) {
        val arg = command.split(" ", limit = 2).getOrElse(1) { "" }.trim()
        if (arg.isBlank()) {
            addAssistantMessage("usage: /queue <prompt>")
            return
        }
        submitPrompt(
            text = arg,
            queued = true,
            onDispatched = { recordAcceptedSlash(command) },
        )
    }

    /**
     * Fork the active conversation via the session.branch WS RPC (issue #533).
     * The backend already supports session.branch; the mobile previously had
     * no client surface, so `/fork` fell through to command.dispatch and 4018'd.
     * The optional arg becomes the new branch's title.
     */
    private fun branchSession(
        command: String,
        onDispatched: (() -> Unit)? = null,
    ) {
        val sessionId = runtimeSessionId
        if (sessionId == null) {
            addAssistantMessage("No active session. Use `/new` to create one.")
            return
        }
        val arg = command.split(" ", limit = 2).getOrElse(1) { "" }.trim()
        val params = mutableMapOf<String, Any>("session_id" to sessionId)
        if (arg.isNotBlank()) params["name"] = arg
        viewModelScope.launch(io) {
            wsClient.send(
                WsMethods.SESSION_BRANCH,
                params,
                onSent = { id ->
                    trackBranchRequest(id, sessionId)
                    onDispatched?.invoke()
                },
            )
        }
    }

    private fun dispatchViaRpc(command: String) {
        val sessionId = runtimeSessionId
        if (sessionId == null) {
            addAssistantMessage("No active session. Use `/new` to create one.")
            return
        }
        val parts = command.split(" ", limit = 2)
        val name = parts[0].lowercase().removePrefix("/")
        val arg = parts.getOrElse(1) { "" }
        viewModelScope.launch(io) {
            try {
                // Primary path: command.dispatch handles quick/plugin/bundle/
                // skill commands + a few hardcoded ones. It returns a hard 4018
                // "not a ... command" for everything that lives only in the TUI
                // slash worker (the 29 commands that 4018'd on mobile — issue
                // #576). For those we fall back to slash.exec, which runs the
                // full COMMAND_REGISTRY through the worker.
                val result =
                    wsClient
                        .request(
                            WsMethods.COMMAND_DISPATCH,
                            mapOf("name" to name, "arg" to arg, "session_id" to sessionId),
                        ).await()
                recordAcceptedSlash(command)
                handleDispatchResult(result)
            } catch (e: HermesWsClient.HermesRpcException) {
                val msg = e.message.orEmpty()
                // Registry miss on command.dispatch: the backend emits exactly
                // "not a quick/plugin/bundle/skill command: <name>" (tui_gateway
                // server.py L12408). Match that precise phrase so unrelated
                // errors can't accidentally trigger the slash.exec fallback.
                if (msg.contains("not a quick/plugin/bundle/skill command")) {
                    // Registry miss on command.dispatch -> retry via slash.exec,
                    // which routes the full CLI command set through the worker.
                    try {
                        val result =
                            wsClient
                                .request(
                                    WsMethods.SLASH_EXEC,
                                    mapOf(
                                        "command" to "/$name${if (arg.isNotEmpty()) " $arg" else ""}",
                                        "session_id" to sessionId,
                                    ),
                                ).await()
                        recordAcceptedSlash(command)
                        val output = (result as? Map<*, *>)?.get("output") as? String
                        if (!output.isNullOrBlank()) addAssistantMessage(output)
                    } catch (e2: HermesWsClient.HermesRpcException) {
                        addAssistantMessage("/$name: ${e2.message}")
                    }
                } else {
                    // Legit error from command.dispatch (busy, no history, etc.)
                    addAssistantMessage("/$name: ${e.message}")
                }
            }
        }
    }

    /**
     * Hot-swap the current session's model via the backend's model-switch
     * mechanism (issue #589).
     *
     * The TUI gateway's `prompt.submit` does NOT parse slash commands (it would
     * make the LLM treat "/model ..." as a chat message), and `command.dispatch`
     * only knows quick/plugin/bundle/skill commands (4018s on /model). The
     * correct RPC is `config.set` with `key="model"` — the gateway (server.py
     * `config.set`, L10253) routes `key=="model"` straight to `_apply_model_switch`
     * using the same `_sessions.get(session_id)` lookup that the working
     * `command.dispatch` uses.
     *
     * IMPORTANT: `config.set` key=model passes the value DIRECTLY to
     * `parse_model_flags` (it does NOT strip a leading "/model" like slash.exec /
     * prompt.submit do). So we strip the "/model" prefix here and send the bare
     * spec `parse_model_flags` understands:
     *   `<model> --provider <slug> --session`
     * (matching the TUI client's `modelValueForConfigSet`).
     */
    private fun handleModelSwitch(
        command: String,
        recordUsage: Boolean,
    ) {
        // Strip a leading "/model" (and any following whitespace) — config.set
        // key=model expects the bare spec, not a slash command. Match the
        // dispatcher's case-insensitive "/model" detection so a typed "/MODEL"
        // (or any casing) doesn't forward the literal slash prefix to the
        // backend, where parse_model_flags wouldn't recognize it.
        val spec =
            if (command.startsWith("/model", ignoreCase = true)) {
                command.substring(6).trim()
            } else {
                command.trim()
            }
        applySessionModel(spec, recordUsage = recordUsage)
    }

    /**
     * Submits [text] as a prompt to the current session via WS, without
     * adding a duplicate user message. Used by [handleDispatchResult] when
     * a slash command resolves to a normal user prompt.
     */
    private fun submitPrompt(
        text: String,
        queued: Boolean = false,
        onDispatched: (() -> Unit)? = null,
    ) {
        if (text.isBlank()) return
        val sessionId = runtimeSessionId ?: return
        uiStateMutable.update {
            it.copy(
                transcript = it.transcript.copy(isAgentTyping = true),
            )
        }
        viewModelScope.launch(io) {
            wsClient.sendMessage(
                sessionId,
                text,
                onSent = { id ->
                    trackRequest(id, WsMethods.PROMPT_SUBMIT)
                    onDispatched?.invoke()
                },
                queued = queued,
            )
        }
    }

    private fun addAssistantMessage(text: String) {
        val msg = ChatMessage(role = MessageRole.ASSISTANT, content = text)
        uiStateMutable.update {
            it.copy(
                transcript = it.transcript.copy(messages = it.messages + msg),
            )
        }

        // Persist — OUTSIDE update{}
        val sessionId = uiStateMutable.value.currentSessionId
        if (sessionId != null) {
            val acceptedRevision = repo.replacementGeneration(sessionId)
            viewModelScope.launch(io) {
                repo.persistMessage(msg, sessionId, acceptedRevision)
            }
        }
    }

    // ── Session management ───────────────────────────────────────────────

    fun interruptSession(onDispatched: (() -> Unit)? = null) {
        val sessionId = runtimeSessionId ?: return
        viewModelScope.launch(io) {
            wsClient.send(
                WsMethods.SESSION_INTERRUPT,
                mapOf("session_id" to sessionId),
                onSent = { id ->
                    trackRequest(id, WsMethods.SESSION_INTERRUPT)
                    onDispatched?.invoke()
                },
            )
        }
    }

    internal var sessionCreateCounter = 0L

    /** Liveness timer for the newest unacknowledged `session.create`. */
    internal var sessionCreateJob: Job? = null
    private var statusPillJob: Job? = null

    // ── Likivik patch: pinned sessions for the rail ──
    // Eagerly initialize this before init{} can receive a fast gateway.ready.
    // A lazy delegate here can be observed as unassigned during construction.
    private val _pinnedSessionIds =
        MutableStateFlow(
            preferences.getStringSet("pinned"),
        )
    val pinnedSessionIds: StateFlow<Set<String>> = _pinnedSessionIds.asStateFlow()

    // ── Likivik patch: persisted rail order (drag-to-reorder). Stored as a
    // comma-joined id string because SharedPreferences StringSet ordering is
    // not guaranteed. homeOrder holds the full displayed order (pinned +
    // fresh + stale, as last set by the rail). ──
    private val _homeOrder = MutableStateFlow(loadHomeOrder())
    val homeOrder: StateFlow<List<String>> = _homeOrder.asStateFlow()

    private fun loadHomeOrder(): List<String> =
        (
            preferences.getString("home_order")?.takeIf { it.isNotBlank() }
                ?.split(',')?.filter { it.isNotEmpty() }
        ) ?: emptyList()

    /** Persist the rail's full displayed order (pinned + fresh + stale). */
    fun setRailOrder(order: List<String>) {
        val clean = order.filter { it.isNotBlank() }
        preferences.putString("home_order", clean.joinToString(","))
        _homeOrder.value = clean
    }

    // ── Likivik patch: remember the last-open session so a fresh app launch
    // resumes it instead of blindly creating a new one each time. ──
    internal fun persistLastSession(id: String) {
        preferences.putString(lastSessionKey(), id)
    }

    /**
     * Keyed by profile: a session id from one profile is meaningless in another, and a single
     * global key made the chat resume the previous profile's conversation after a switch.
     * The legacy key is deliberately not migrated — it cannot be attributed to a profile.
     */
    private fun lastSessionKey(): String = SessionPreferenceKeys.lastSession(selectedProfileNow())

    private fun lastSession(): String? = preferences.getString(lastSessionKey())?.takeIf { it.isNotBlank() }

    /** Bounded, profile-keyed transcript cache. Owns its own keying and eviction (see the class). */
    private val transcriptCache = TranscriptCache()

    /** Wall-clock milliseconds, for "how long has this been out of date". */
    private fun nowMs(): Long = nowMillis()

    /**
     * Drop the open conversation when the profile changes.
     *
     * Mirrors `switchSession`'s invalidation so late answers for the old profile's session are
     * inert, then leaves the pane empty or on the new profile's own last session.
     */
    private fun resetConversationForProfileChange() {
        clearPrivilegedControls()
        invalidateHistoryRefresh()
        uiStateMutable.value.currentSessionId?.let(repo::invalidateReplacementWrites)
        conversationGeneration++
        invalidateResumeRequests()
        sessionCreateCounter++
        sessionCreateJob?.cancel()
        sessionCreateJob = null
        runtimeSessionId = null
        loadedMessageOffset = 0
        latestPaging = false
        streamingController.resetStreaming()
        streamingStateMutable.update { StreamingState() }
        ActiveSessionHolder.set(null, null)
        uiStateMutable.update {
            it.copy(
                transcript =
                    it.transcript.copy(
                        currentSessionId = null,
                        isSessionReady = false,
                        isLoading = false,
                        isLoadingOlder = false,
                        hasOlderMessages = false,
                        messages = emptyList(),
                        subagentIndicators = emptyList(),
                        todos = emptyList(),
                        chatTitle = "Hermes",
                        isAgentTyping = false,
                        contextUsage = null,
                    ),
                composer = it.composer.afterProfileChange(),
            )
        }
    }

    // ── Likivik patch: per-session rail customization (icon emoji only; the
    // name shown equals the real session title, renamed via session.title) ──
    // RailMeta is a top-level model in ui/chat/RailMeta.kt.

    private val _railMeta =
        MutableStateFlow(
            loadRailMeta(),
        )
    val railMeta: StateFlow<Map<String, RailMeta>> = _railMeta.asStateFlow()

    private fun loadRailMeta(): Map<String, RailMeta> {
        val all = preferences.allStrings()
        val map = mutableMapOf<String, RailMeta>()
        for ((key, value) in all) {
            if (key.startsWith("icon_") && value is String) {
                map[key.removePrefix("icon_")] = RailMeta(value.takeIf { it.isNotEmpty() })
            }
        }
        return map
    }

    fun saveRailMeta(
        sessionId: String,
        icon: String?,
    ) {
        val cleanIcon = icon?.takeIf { it.isNotEmpty() }
        if (cleanIcon == null) {
            preferences.remove("icon_$sessionId")
        } else {
            preferences.putString("icon_$sessionId", cleanIcon)
        }
        _railMeta.value = _railMeta.value + (sessionId to RailMeta(cleanIcon))
    }

    /**
     * Likivik patch: rename the session's real title on the server
     * (`session.title`). The rail name equals the actual session name — this
     * updates the header and every surface, not just the rail. Optionally sets
     * a rail icon (local decoration) in the same go.
     */
    fun renameSession(
        sessionId: String,
        newTitle: String,
        icon: String?,
    ) {
        saveRailMeta(sessionId, icon)
        // Icon-only edit (empty title = keep current): no server round-trip.
        val title =
            when (val plan = planRename(newTitle)) {
                RenamePlan.IconOnly -> return
                is RenamePlan.Title -> plan.title
            }
        // ALWAYS resume first — single deterministic path. The gateway reaps
        // detached runtimes (WS grace reaper), so a session the app still
        // considers "live" (cached runtime id) can already be reaped
        // server-side: even CURRENT-session renames 4001 with a bare
        // session.title ("detached/reaped runtime; client should resume the
        // stored session" — the gateway's own guidance). Resume is idempotent:
        // live sessions return the fast-path payload, detached ones are
        // re-registered; the ack then fires the title by the stable
        // session_key (storage id).
        val renameResumeFence = captureResumeFence(sessionId)
        wsClient.send(
            WsMethods.SESSION_RESUME,
            mapOf("session_id" to sessionId, "omit_messages" to true),
            onSent = { id ->
                pendingRequests[id] =
                    PendingRpcRequest(
                        method = WsMethods.SESSION_RESUME,
                        resumeSessionId = sessionId,
                        resumeFence = renameResumeFence,
                        pendingRenameTitle = title,
                        pendingRenameIcon = icon,
                    )
            },
        )
        // Reflect immediately in the local session list.
        uiStateMutable.update { state ->
            state.copy(
                transcript =
                    state.transcript.copy(
                        chatTitle = if (state.currentSessionId == sessionId) title else state.chatTitle,
                    ),
                rail =
                    state.rail.copy(
                        sessions =
                            state.sessions.map {
                                if (it.id == sessionId) it.copy(title = title) else it
                            },
                    ),
            )
        }
    }

    internal fun sendSessionTitle(
        runtimeSessionId: String,
        title: String,
    ) {
        viewModelScope.launch(io) {
            wsClient.send(
                WsMethods.SESSION_TITLE,
                mapOf("session_id" to runtimeSessionId, "title" to title),
                // Tracked so a rejection is attributed to session.title and
                // handled here instead of falling through to the reducer's raw
                // JsonRpcError banner.
                onSent = { id ->
                    pendingRequests[id] =
                        PendingRpcRequest(
                            method = WsMethods.SESSION_TITLE,
                            titleTargetId = runtimeSessionId,
                        )
                },
            )
        }
    }

    /**
     * `session.delete` is refused with 4023 while the target is LIVE on the
     * gateway (the agent's next flush would trip a foreign key), and
     * `session.close` — like every session-scoped RPC — resolves an EXACT live
     * runtime id (`_pop_session_by_id` pops the `_sessions` key). The stored id
     * the rail holds is not that key, so resume it once to learn the runtime
     * sid, close it, then retry the stored delete.
     */
    private fun startCloseThenDelete(storedId: String) {
        val closeResumeFence = captureResumeFence(storedId)
        wsClient.send(
            WsMethods.SESSION_RESUME,
            mapOf("session_id" to storedId, "omit_messages" to true),
            onSent = { id ->
                pendingRequests[id] =
                    PendingRpcRequest(
                        method = WsMethods.SESSION_RESUME,
                        resumeSessionId = storedId,
                        resumeFence = closeResumeFence,
                        deleteSessionId = storedId,
                        closeAfterResume = true,
                    )
            },
        )
    }

    internal fun sendCloseSession(
        runtimeSessionId: String,
        storedId: String,
    ) {
        wsClient.send(
            WsMethods.SESSION_CLOSE,
            mapOf("session_id" to runtimeSessionId),
            onSent = { id ->
                pendingRequests[id] =
                    PendingRpcRequest(
                        method = WsMethods.SESSION_CLOSE,
                        deleteSessionId = storedId,
                    )
            },
        )
    }

    internal fun sendDeleteSession(
        storedId: String,
        retried: Boolean = false,
    ) {
        wsClient.send(
            WsMethods.SESSION_DELETE,
            mapOf("session_id" to storedId),
            onSent = { id ->
                pendingRequests[id] =
                    PendingRpcRequest(
                        method = WsMethods.SESSION_DELETE,
                        deleteSessionId = storedId,
                        deleteRetried = retried,
                    )
            },
        )
    }

    fun togglePinSession(sessionId: String) {
        val next =
            if (sessionId in _pinnedSessionIds.value) {
                _pinnedSessionIds.value - sessionId
            } else {
                _pinnedSessionIds.value + sessionId
            }
        preferences.putStringSet("pinned", next)
        _pinnedSessionIds.value = next
    }

    /**
     * Likivik patch: delete a session from the rail's long-press menu.
     * Fire-and-forget over the WS socket, then drop it from the local list
     * (and pins) immediately so the rail reflects reality without waiting
     * for the next session.list round-trip.
     */
    fun deleteRailSession(sessionId: String) {
        // session.delete takes the STORED id (it deletes the DB row and refuses
        // only when that id belongs to a live runtime, 4023) — which is exactly
        // what the rail holds from session.list.
        sendDeleteSession(sessionId)
        _pinnedSessionIds.value.let { pins ->
            if (sessionId in pins) togglePinSession(sessionId)
        }
        uiStateMutable.update { state ->
            state.copy(
                rail = state.rail.copy(sessions = state.sessions.filter { it.id != sessionId }),
            )
        }
    }

    /**
     * Ask the gateway for a fresh conversation.
     *
     * `session.create` is fire-and-forget over a socket that may be mid-drop.
     * [HermesWsClient.send] queues the frame while the socket is down and drops
     * it outright once credentials were cleared, and neither path reports back
     * — so an unlucky tap could leave Chat with no session at all:
     * `currentSessionId` and `runtimeSessionId` already discarded,
     * `isSessionReady` false, Send disabled, and nothing in flight to recover
     * it. That is the "leave the chat screen and come back a few times" state;
     * re-entering only helped because a later `gateway.ready` happened to issue
     * another create.
     *
     * The old code only cleared the spinner after ten seconds, which hid the
     * symptom without producing a session. Retry the request on a deadline
     * instead, and surface an actionable error once the attempts are spent.
     */
    fun createNewSession(
        setLoading: Boolean = true,
        onDispatched: (() -> Unit)? = null,
    ) {
        clearPrivilegedControls()
        invalidateHistoryRefresh()
        uiStateMutable.value.currentSessionId?.let(repo::invalidateReplacementWrites)
        conversationGeneration++
        invalidateResumeRequests()
        val generation = ++sessionCreateCounter
        sessionCreateJob?.cancel()
        sessionCreateJob = null
        runtimeSessionId = null
        loadedMessageOffset = 0
        latestPaging = false
        ActiveSessionHolder.set(null)
        uiStateMutable.update { it.afterNewSession(setLoading) }
        streamingStateMutable.update { StreamingState() }
        streamingController.resetStreaming()
        sendSessionCreate(generation = generation, attempt = 1, onDispatched = onDispatched)
    }

    /**
     * Issue one `session.create` attempt and arm its liveness deadline.
     *
     * The deadline is fenced on [generation] so a superseded attempt can never
     * retry on behalf of a newer one, and it stands down as soon as the session
     * is ready — an ack, not a fixed sleep, ends the cycle.
     */
    private fun sendSessionCreate(
        generation: Long,
        attempt: Int,
        onDispatched: (() -> Unit)? = null,
    ) {
        viewModelScope.launch(io) {
            wsClient.send(
                WsMethods.SESSION_CREATE,
                params = mapOf("source" to "desktop"),
                onSent = { id ->
                    trackCreateRequest(id, generation)
                    onDispatched?.invoke()
                },
            )
        }

        // Historical behavior: unit tests never armed the safety timer. Keep
        // that unless a test opts into the retry path by injecting a delay.
        val injectedDelay = sessionCreateRetryDelayMs
        if (injectedDelay == null && isTestEnvironment()) return
        val delayMs = injectedDelay ?: SESSION_CREATE_TIMEOUT_MS

        sessionCreateJob =
            viewModelScope.launch {
                delay(delayMs)
                if (generation != sessionCreateCounter) return@launch
                if (uiStateMutable.value.isSessionReady) return@launch
                if (attempt < SESSION_CREATE_MAX_ATTEMPTS) {
                    HermesLog.w(TAG, "session.create unacknowledged — retrying (attempt ${attempt + 1})")
                    sendSessionCreate(generation = generation, attempt = attempt + 1)
                } else {
                    HermesLog.w(TAG, "session.create gave up after $attempt attempts")
                    val msg = resolver.get(Res.string.chat_new_session_failed)
                    uiStateMutable.update {
                        it.copy(
                            transcript = it.transcript.copy(isLoading = false, errorMessage = msg),
                        )
                    }
                }
            }
    }

    // ── Rail refresh (REST) ──────────────────────────────────────────────
    //
    // The rail reads REST, not the `session.list` RPC: REST returns the gateway's own
    // rows, so `last_active` — the activity timestamp the stale fold needs — survives.
    // The RPC builds a hand-picked summary and drops that field.
    //
    // Reconciliation is "newest response wins". The WS `session.list` reply is only a
    // signal that the list changed, never a second copy to merge: a monotonic token
    // keeps a slow response from painting over a newer list, and a failure keeps the
    // last-good rows instead of blanking the rail.

    fun loadSessions() {
        // The RPC round-trip stays as the *trigger* — the rest of the app still expects
        // this handshake — but its rows are ignored: the rail's rows come from REST,
        // which carries `last_active` (see fetchRail).
        viewModelScope.launch(io) {
            wsClient.send(
                WsMethods.SESSION_LIST,
                onSent = { id -> trackRequest(id, WsMethods.SESSION_LIST) },
            )
        }
        requestRailRefresh(force = false)
    }

    /**
     * User-triggered rail refresh (pull-to-refresh / the top-bar refresh icon).
     * Re-lists sessions so the rail reflects SERVER truth — titles, deletions and
     * ordering are owned by the gateway, and the rename/delete paths update the rail
     * optimistically.
     */
    fun refreshSessions() {
        uiStateMutable.update {
            it.copy(
                rail = it.rail.copy(railRefreshing = true),
            )
        }
        requestRailRefresh(force = true)
    }

    /** Foreground re-entry: refresh quietly — the user did not ask for a spinner. */
    fun onAppForegrounded() = requestRailRefresh(force = true)

    private val chatRail: ChatRail by lazy {
        ChatRail(
            io = io,
            now = { nowMillis() },
            launch = { block -> viewModelScope.launch(io) { block() } },
            fetchPage = { offset, limit ->
                safeApiCall {
                    api.hermesApi.getSessions(
                        limit = limit,
                        offset = offset,
                        order = "recent",
                        excludeSources = AUTOMATION_SOURCE,
                    )
                }
            },
            selectSessions = ::railSessionsOf,
            backoffMs = ::railBackoffMs,
            write = ::applyRailUpdate,
            pageSize = RAIL_PAGE_SIZE,
            maxPages = RAIL_MAX_PAGES,
            minIntervalMs = RAIL_MIN_INTERVAL_MS,
        )
    }

    internal fun requestRailRefresh(force: Boolean) {
        chatRail.request(force)
    }

    private fun applyRailUpdate(update: ChatRailUpdate) {
        when (update) {
            is ChatRailUpdate.Loaded -> {
                if (update.rows != update.sessions.size) {
                    HermesLog.i("ChatVM", "rail hid ${update.rows - update.sessions.size} automation sessions")
                }
                HermesLog.i(
                    "ChatVM",
                    "rail refreshed: ${update.sessions.size} sessions in " +
                        "${update.pages} page(s), ${update.elapsedMs}ms",
                )
                uiStateMutable.update { state ->
                    val newTitle = update.sessions.find { s -> s.id == state.currentSessionId }?.title
                    state.copy(
                        transcript = state.transcript.copy(chatTitle = newTitle ?: state.chatTitle),
                        rail =
                            state.rail.copy(
                                sessions = update.sessions,
                                railRefreshing = false,
                                railStatus = railLoadStatusOf(update.sessions.size, null),
                            ),
                    )
                }
            }

            is ChatRailUpdate.Failed -> {
                val rows = uiStateMutable.value.sessions.size
                val status = railLoadStatusOf(rows, update.error)
                // Keep the last-good rows and record *why* they are missing: the rail renders the reason
                // as a retry affordance instead of vanishing.
                HermesLog.w(
                    "ChatVM",
                    "rail refresh failed (${update.error}); keeping $rows rows, " +
                        "status=$status, next attempt in ${update.backoffMs / 1000}s",
                )
                uiStateMutable.update {
                    it.copy(rail = it.rail.copy(railRefreshing = false, railStatus = status))
                }
            }
        }
    }

    private fun railBackoffMs(failures: Int): Long =
        (RAIL_MIN_INTERVAL_MS shl (failures - 1).coerceIn(0, 5)).coerceAtMost(RAIL_BACKOFF_MAX_MS)

    private fun fetchCommandCatalog() {
        viewModelScope.launch(io) {
            wsClient.send(
                WsMethods.COMMANDS_CATALOG,
                onSent = { id -> trackRequest(id, WsMethods.COMMANDS_CATALOG) },
            )
        }
    }

    /**
     * Top up the context meter's denominator from `GET /api/model/info`.
     *
     * Only a fallback: it covers the window between opening a session and its
     * first turn, before the gateway has pushed a live `context_max`. The
     * numerator is never sourced from REST — see [ContextUsage].
     */
    private fun fetchModelContextLength() {
        viewModelScope.launch(io) {
            when (val result = safeApiCall { api.hermesApi.getModelInfo() }) {
                is NetworkResult.Success -> {
                    val length =
                        result.data.effectiveContextLength
                            ?: result.data.autoContextLength
                    if (length != null && length > 0L) {
                        uiStateMutable.update {
                            it.copy(
                                composer = it.composer.afterModelContextLength(length, result.data.qualifiedModel),
                            )
                        }
                    }
                }

                is NetworkResult.Failure -> Unit
            }
        }
    }

    /** Open the tappable context breakdown sheet. */
    fun openContextDetail() {
        uiStateMutable.update {
            it.copy(
                overlays = it.overlays.afterContextDetailOpened(),
            )
        }
    }

    /** Dismiss the context breakdown sheet. */
    fun closeContextDetail() {
        uiStateMutable.update {
            it.copy(
                overlays = it.overlays.afterContextDetailClosed(),
            )
        }
    }

    fun refreshCurrentSession() {
        val sessionId = uiStateMutable.value.currentSessionId ?: return
        loadSessionMessages(sessionId)
    }

    fun refreshSettings() {
        uiStateMutable.update { state ->
            state.copy(
                composer =
                    state.composer.afterTypingSettings(
                        authStore.isTypingEffectEnabled(),
                        authStore.getTypingEffectDelayMs(),
                    ),
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseCommandCatalog(map: Map<*, *>): CommandCatalog? =
        try {
            val jsonElement = map.toJsonElement()
            HermesJson.instance.decodeFromJsonElement<CommandCatalog>(jsonElement)
        } catch (e: Exception) {
            HermesLog.e(TAG, "Failed to parse command catalog (${e::class.simpleName})")
            null
        }

    // ── In-session model picker (issue #589) ─────────────────────────────

    // The "/model" rule itself lives in SendPlan.kt as `isBareModelCommand`, so it can be
    // tested without a ViewModel; this section only holds the picker's cache.

    /** Preload model options so the picker opens instantly (no spinner on /model). */
    private fun preloadModelOptions() {
        viewModelScope.launch(io) {
            val result =
                safeApiCall {
                    api.hermesApi.getModelOptions(refresh = false)
                }
            when (result) {
                is NetworkResult.Success -> {
                    cachedModelOptions = result.data.providers.orEmpty()
                    uiStateMutable.update {
                        it.copy(
                            overlays =
                                it.overlays.copy(
                                    modelPickerProviders = cachedModelOptions,
                                    modelPickerPinned = authStore.getPinnedModels(),
                                    modelInventoryResolved = true,
                                ),
                        )
                    }
                }

                is NetworkResult.Failure -> {
                    uiStateMutable.update {
                        it.copy(
                            overlays = it.overlays.afterModelInventoryResolved(),
                        )
                    }
                }
            }
        }
    }

    /**
     * Open the in-session model picker. Uses the preloaded options if available
     * (instant open); otherwise shows a loading state and fetches them. The
     * `/model` slash command is the supported session hot-swap mechanism per the
     * backend contract (issue #589).
     */
    fun openModelPicker() {
        val hasCached = cachedModelOptions.isNotEmpty()
        uiStateMutable.update {
            it.copy(
                overlays =
                    it.overlays.copy(
                        showModelPicker = true,
                        modelPickerProviders = if (hasCached) cachedModelOptions else emptyList(),
                        modelPickerPinned = authStore.getPinnedModels(),
                        modelPickerLoading = !hasCached,
                    ),
            )
        }
        if (!hasCached) {
            refreshModelOptions()
        }
    }

    /** Re-fetch options (pull-to-refresh style) when the picker is already open. */
    fun refreshModelOptions() {
        uiStateMutable.update {
            it.copy(
                overlays = it.overlays.afterModelRefreshStarted(),
            )
        }
        viewModelScope.launch(io) {
            val result =
                safeApiCall {
                    api.hermesApi.getModelOptions(refresh = true)
                }
            when (result) {
                is NetworkResult.Success -> {
                    cachedModelOptions = result.data.providers.orEmpty()
                    uiStateMutable.update {
                        it.copy(
                            overlays =
                                it.overlays.copy(
                                    modelPickerProviders = cachedModelOptions,
                                    modelPickerLoading = false,
                                    modelInventoryResolved = true,
                                ),
                        )
                    }
                }

                is NetworkResult.Failure -> {
                    uiStateMutable.update {
                        it.copy(
                            transcript =
                                it.transcript.copy(
                                    errorMessage = "Failed to load models: ${result.error.message}",
                                ),
                            overlays = it.overlays.copy(modelPickerLoading = false, modelInventoryResolved = true),
                        )
                    }
                }
            }
        }
    }

    fun closeModelPicker() {
        uiStateMutable.update {
            it.copy(
                overlays = it.overlays.afterModelPickerClosed(),
            )
        }
    }

    fun togglePinModel(
        providerSlug: String,
        modelName: String,
    ) {
        val currentPinned = authStore.getPinnedModels().toMutableList()
        val target = PinnedModel(providerSlug, modelName)
        if (currentPinned.contains(target)) {
            currentPinned.remove(target)
        } else {
            currentPinned.add(target)
        }
        authStore.savePinnedModels(currentPinned)
        uiStateMutable.update {
            it.copy(
                overlays = it.overlays.afterPinnedModelsChanged(currentPinned),
            )
        }
    }

    /** Hot-swap only the current live session through the awaited config.set RPC. */
    fun sendSlashModel(
        provider: String,
        model: String,
    ) {
        uiStateMutable.update {
            it.copy(
                overlays = it.overlays.afterModelPickerClosed(),
            )
        }
        applySessionModel(
            spec = "$model --provider $provider --session",
            displayLabel = "$provider/$model",
        )
    }

    fun confirmModelSwitch() {
        val pending = uiStateMutable.value.modelSwitchConfirmation ?: return
        if (runtimeSessionId != pending.sessionId) {
            // `resolver.get` is `suspend`; the `onClick` surface this
            // method is wired to is not, so we hoist the message
            // resolution into a one-shot launch before publishing the
            // error.
            viewModelScope.launch {
                val msg = resolver.get(Res.string.chat_model_switch_session_changed)
                uiStateMutable.update {
                    it.copy(
                        transcript = it.transcript.copy(errorMessage = msg),
                        overlays = it.overlays.afterModelSwitchConfirmationDismissed(),
                    )
                }
            }
            return
        }
        uiStateMutable.update {
            it.copy(
                overlays = it.overlays.afterModelSwitchConfirmationDismissed(),
            )
        }
        applySessionModel(
            spec = pending.spec,
            displayLabel = pending.displayLabel,
            confirmExpensive = true,
        )
    }

    fun cancelModelSwitchConfirmation() {
        uiStateMutable.update {
            it.copy(
                overlays = it.overlays.afterModelSwitchConfirmationDismissed(),
            )
        }
    }

    private fun applySessionModel(
        spec: String,
        displayLabel: String? = null,
        confirmExpensive: Boolean = false,
        recordUsage: Boolean = false,
    ) {
        val sessionId = runtimeSessionId
        if (sessionId == null) {
            // `resolver.get` is `suspend`; this method is wired to
            // non-suspend UI surfaces, so we resolve the error in a
            // one-shot launch before publishing it.
            viewModelScope.launch {
                val msg = resolver.get(Res.string.chat_model_switch_no_session)
                uiStateMutable.update {
                    it.copy(
                        transcript = it.transcript.copy(errorMessage = msg),
                    )
                }
            }
            return
        }

        viewModelScope.launch(io) {
            val params =
                mutableMapOf<String, Any>(
                    "session_id" to sessionId,
                    "key" to "model",
                    "value" to spec,
                )
            if (confirmExpensive) {
                params["confirm_expensive_model"] = true
            }

            try {
                val result = wsClient.request(WsMethods.CONFIG_SET, params).await()
                val resultMap = result as? Map<*, *>
                if (resultMap?.get("confirm_required") == true) {
                    val message =
                        (resultMap["confirm_message"] as? String)
                            ?.takeIf { it.isNotBlank() }
                            ?: resolver.get(Res.string.chat_model_confirm_default_message)
                    uiStateMutable.update {
                        if (runtimeSessionId != sessionId) return@update it
                        it.copy(
                            overlays =
                                it.overlays.copy(
                                    modelSwitchConfirmation =
                                        ModelSwitchConfirmation(
                                            message = message,
                                            spec = spec,
                                            displayLabel = displayLabel,
                                            sessionId = sessionId,
                                        ),
                                ),
                        )
                    }
                    return@launch
                }

                val provider = resultMap?.get("provider") as? String
                val model = resultMap?.get("model") as? String
                val confirmedLabel =
                    if (!provider.isNullOrBlank() && !model.isNullOrBlank()) {
                        "$provider/$model"
                    } else {
                        displayLabel
                    }
                uiStateMutable.update {
                    if (runtimeSessionId != sessionId) return@update it
                    it.copy(
                        transcript = it.transcript.copy(contextUsage = it.contextUsage?.copy(maxTokens = null)),
                        composer = it.composer.afterSessionModel(confirmedLabel),
                        overlays = it.overlays.afterModelSwitchConfirmationDismissed(),
                    )
                }
                if (recordUsage) recordAcceptedSlash("/model")
            } catch (e: HermesWsClient.HermesRpcException) {
                val msg = resolver.get(Res.string.chat_model_switch_failed, e.message ?: "")
                uiStateMutable.update {
                    if (runtimeSessionId != sessionId) return@update it
                    it.copy(
                        transcript = it.transcript.copy(errorMessage = msg),
                        overlays = it.overlays.afterModelSwitchConfirmationDismissed(),
                    )
                }
            }
        }
    }

    private fun recordAcceptedSlash(command: String) {
        val commandName = command.split(" ", limit = 2)[0].lowercase()
        viewModelScope.launch {
            slashUsageStore.recordUse(selectedGatewayId(), commandName)
        }
    }

    fun consumeOpenHistoryRequest() {
        uiStateMutable.update {
            it.copy(
                composer = it.composer.afterOpenHistoryRequest(false),
            )
        }
    }

    /**
     * Set the reasoning effort level for the current session.
     *
     * Updates the UI optimistically and sends a `config.set` RPC to the
     * backend. The level applies per-session via the runtime session ID.
     * If [level] is null it resets to the model's default.
     *
     * @param level One of "low", "medium", "high", or null for default.
     */
    fun setReasoningLevel(level: String?) {
        uiStateMutable.update {
            it.copy(
                composer = it.composer.afterReasoningLevel(level),
            )
        }
        val sessionId = runtimeSessionId ?: return
        if (level == null) return // null = model default, no need to send WS
        viewModelScope.launch(io) {
            wsClient.send(
                WsMethods.CONFIG_SET,
                mapOf(
                    "key" to "reasoning",
                    "value" to level,
                    "session_id" to sessionId,
                ),
                onSent = { id -> trackRequest(id, WsMethods.CONFIG_SET) },
            )
        }
    }

    fun switchSession(sessionId: String) {
        if (sessionId == uiStateMutable.value.currentSessionId) return

        clearPrivilegedControls()
        invalidateHistoryRefresh()
        uiStateMutable.value.currentSessionId?.let(repo::invalidateReplacementWrites)
        conversationGeneration++
        invalidateResumeRequests()
        // A pending session.create belongs to the conversation the user just
        // left. Retiring the generation makes any late answer inert, and the
        // timer must go with it or it would retry a create into this session.
        sessionCreateCounter++
        sessionCreateJob?.cancel()
        sessionCreateJob = null

        // Reset streaming and pagination state before resuming the Desktop session.
        runtimeSessionId = null
        loadedMessageOffset = 0
        latestPaging = false
        streamingController.resetStreaming()
        uiStateMutable.update { state ->
            val title = state.sessions.find { s -> s.id == sessionId }?.title ?: "Hermes"
            switchedState(
                state = state,
                sessionId = sessionId,
                cachedMessages = transcriptCache.get(selectedProfileNow(), sessionId),
                title = title,
            )
        }
        // Mirror the active session id app-wide (issue #532).
        ActiveSessionHolder.set(sessionId)
        // Likivik patch: remember this as the last-open session for next launch.
        persistLastSession(sessionId)
        streamingStateMutable.update { StreamingState() }
        // Likivik patch: paint from Room cache immediately instead of staring at
        // a blank loading screen while the network round-trip completes. The
        // authoritative refresh below (loadSessionMessages) converges the view.
        loadCachedMessages(sessionId)
        val resumeFence = captureResumeFence(sessionId)
        viewModelScope.launch {
            // Resume the selected desktop session, then load its complete transcript.
            launch(io) {
                if (resumeFence != null && isResumeFenceCurrent(resumeFence)) {
                    wsClient.send(
                        WsMethods.SESSION_RESUME,
                        mapOf("session_id" to sessionId, "omit_messages" to true),
                        onSent = { id -> trackResumeRequest(id, resumeFence) },
                    )
                }
            }
            loadSessionMessages(sessionId)
            loadSessions()
        }
    }

    private fun loadCachedMessages(sessionId: String) {
        val fence = captureCacheLoadFence(sessionId)
        val owner = ++cacheLoadCounter
        activeCacheLoadOwner = owner
        cacheLoadJob?.cancel()
        cacheLoadJob =
            launchHistoryLoad {
                val cachedMessages = repo.loadMessages(sessionId)
                uiStateMutable.update { state ->
                    // Cache is only a blank-transcript fallback. A live, resume,
                    // or server mutation that wins the race owns the transcript.
                    if (isCacheLoadCurrent(fence, owner) && state.messages.isEmpty()) {
                        activeCacheMessageIds = cachedMessages.mapTo(mutableSetOf()) { it.id }
                        state.copy(
                            transcript =
                                state.transcript.copy(
                                    messages = cachedMessages,
                                    // Likivik patch v2: do NOT clear isLoading here — the
                                    todos = restoredTodos(state.todos, cachedMessages),
                                    // network fetch (loadSessionMessages) owns the loading
                                    // state. Clearing it raced the fetch completion and
                                    // left freshly-switched sessions stuck on "empty chat".
                                    isLoading = state.isLoading,
                                ),
                        )
                    } else {
                        state
                    }
                }
                if (activeCacheLoadOwner == owner) cacheLoadJob = null
            }
    }

    internal fun loadSessionMessages(sessionId: String) {
        val baselineIds = uiStateMutable.value.messages.mapTo(mutableSetOf()) { it.id }
        retireCacheLoad()
        loadCachedMessages(sessionId)
        chatHistory.refresh(
            sessionId = sessionId,
            baselineIds = baselineIds,
            pageSize = MESSAGE_PAGE_SIZE,
            awaitSessionOperations = { id -> repo.awaitSessionOperations(id) },
            countProvider = {
                chatHistory.serverMessageCount(
                    sessionId = sessionId,
                    knownCount = uiStateMutable.value.sessions.find { it.id == sessionId }?.messageCount,
                    transcriptSize = uiStateMutable.value.messages.size,
                    isCurrent = { true },
                    fetchCount = {
                        when (val r = withContext(io) { safeApiCall { api.hermesApi.getSession(sessionId) } }) {
                            is NetworkResult.Success -> r.data.message_count
                            is NetworkResult.Failure -> null
                        }
                    },
                )
            },
        )
    }

    /**
     * Re-run the authoritative load for the open session.
     *
     * This is the only control behind the "out of date" chip: a failed refresh never blanks the
     * transcript, so the user needs a way to ask again without switching sessions.
     */
    fun retryHistory() {
        val state = uiStateMutable.value
        val sessionId = state.currentSessionId ?: return
        if (state.isLoading) return
        loadSessionMessages(sessionId)
    }

    fun loadOlderMessages() {
        val state = uiStateMutable.value
        val sessionId = state.currentSessionId ?: return
        chatHistory.loadOlder(
            sessionId = sessionId,
            hasOlderMessages = state.hasOlderMessages,
            isLoadingOlder = state.isLoadingOlder,
            latestPaging = latestPaging,
            loadedOffset = loadedMessageOffset,
            pageSize = MESSAGE_PAGE_SIZE,
        )
    }

    private fun retireOlderLoad() {
        chatHistory.retireOlder()
    }

    fun syncCurrentSession() {
        val state = uiStateMutable.value
        val sessionId = state.currentSessionId ?: return
        chatHistory.sync(
            sessionId = sessionId,
            isLoading = state.isLoading,
            isLoadingOlder = state.isLoadingOlder,
            isAgentTyping = state.isAgentTyping,
            isStreaming = streamingStateMutable.value.streamingMessage != null,
            latestPaging = latestPaging,
            serverIndexes = state.messages.mapNotNull { serverMessageIndex(it.id, sessionId) },
            loadedOffset = loadedMessageOffset,
            pageSize = MESSAGE_PAGE_SIZE,
        )
    }

    private fun retireSync() {
        chatHistory.retireSync()
    }

    private fun restoredTodos(
        currentTodos: List<TodoItem>,
        messages: List<ChatMessage>,
    ): List<TodoItem> = hydrateTodosFromMessages(messages).ifEmpty { currentTodos }

    private suspend fun fetchMessagePage(
        sessionId: String,
        offset: Int,
        limit: Int,
        order: String? = null,
    ) = historyLoader.page(sessionId, offset, limit, order)

    private fun mapServerMessages(
        sessionId: String,
        messages: List<SessionMessage>,
        offset: Int,
        useLatestPaging: Boolean = latestPaging,
    ): List<ChatMessage> =
        ServerMessageMapper.map(
            sessionId = sessionId,
            messages = messages,
            existingMessages = uiStateMutable.value.messages,
            offset = offset,
            useLatestPaging = useLatestPaging,
            nowMillis = nowMillis(),
        )

    internal fun hydrateResumeMessages(
        sessionId: String,
        payload: Any?,
        replaceExisting: Boolean,
    ) {
        val rawMessages = payload as? List<*> ?: return
        val resumedMessages = parseResumeMessages(sessionId, rawMessages, nowMillis())
        if (resumedMessages.isEmpty()) return

        uiStateMutable.update { state ->
            if (state.currentSessionId != sessionId) return@update state
            val hasTranscript =
                !replaceExisting &&
                    state.messages.any { message ->
                        serverMessageIndex(message.id, sessionId) != null ||
                            message.role != MessageRole.SYSTEM
                    }
            if (hasTranscript) {
                state.copy(
                    transcript = state.transcript.copy(isLoading = false),
                )
            } else {
                state.copy(
                    transcript =
                        state.transcript.copy(
                            messages = resumedMessages,
                            isLoading = false,
                            hasOlderMessages = false,
                            isLoadingOlder = false,
                        ),
                )
            }
        }
        val hasResumeHistory =
            uiStateMutable.value.messages.any {
                it.id.startsWith("resume-$sessionId-")
            }
        if (hasResumeHistory) {
            loadedMessageOffset = 0
        }
    }

    // ── Issue #724: attach host-path MEDIA: files as real attachments ────
    //
    // The gateway's WebSocket stream delivers the raw `MEDIA:<path>` directive.
    // We retain the path as opaque gateway state and fetch through
    // [GatewayFileClient], which uses the normal authenticated Retrofit client.
    // The directive text is stripped from the message body. Pure parsing lives
    // in [HostMediaExtractor].

    /**
     * ViewModel-side handler for [ReducerEffect.AttachHostMedia]: find the local
     * message by id, convert any `MEDIA:<path>` directives into [Attachment]s
     * (via the authenticated gateway client) and strip them from the text. Role,
     * reasoning, timestamp and existing attachments are preserved; new gateway
     * attachments are appended. Idempotent — skips if gateway attachments for
     * the same paths already exist.
     */
    internal suspend fun attachHostMedia(
        sessionId: String,
        messageId: String,
        acceptedRevision: Long,
    ) {
        val current = uiStateMutable.value.messages.find { it.id == messageId } ?: return
        val content = current.content
        val items = HostMediaExtractor.extract(content)
        if (items.isEmpty()) {
            repo.persistMessage(current, sessionId, acceptedRevision)
            return
        }

        val existingPaths =
            current.attachments
                .orEmpty()
                .mapNotNull { it.gatewayPath }
                .toSet()
        val newAttachments =
            items.mapNotNull { item ->
                val path = mediaStore.normalizePath(item.path) ?: return@mapNotNull null
                if (path in existingPaths) return@mapNotNull null
                Attachment(
                    uri = path,
                    name = mediaNameFromPath(item.path),
                    mimeType = mediaMimeForPath(item.path),
                    size = 0,
                    gatewayPath = path,
                    source = AttachmentSource.GATEWAY,
                )
            }
        if (newAttachments.isEmpty()) {
            repo.persistMessage(current, sessionId, acceptedRevision)
            return
        }

        val updatedMessage =
            current.copy(
                content = HostMediaExtractor.strip(content),
                attachments =
                    (current.attachments.orEmpty() + newAttachments)
                        .distinctBy { it.gatewayPath ?: it.uri },
            )
        uiStateMutable.update { state ->
            state.copy(
                transcript =
                    state.transcript.copy(
                        messages =
                            state.messages.map { msg ->
                                if (msg.id == messageId) updatedMessage else msg
                            },
                    ),
            )
        }
        repo.persistMessage(updatedMessage, sessionId, acceptedRevision)
    }

    /**
     * Open an attachment when its chip/thumbnail is tapped.
     *
     * - LOCAL (user-picked) files: open the original `content://` URI
     *   directly via [android.content.Intent.ACTION_VIEW] — the resolver
     *   already grants read access for the picked document. If that fails
     *   (e.g. the permission lapsed), we copy to cache and retry via
     *   FileProvider so the tap is never a silent no-op.
     * - GATEWAY audio/video: hand a profile-fenced range session to the in-app
     *   player, without materializing the full file or exposing credentials.
     * - Other GATEWAY (agent `MEDIA:`) files: fetch the bytes via
     *   [GatewayFileClient], write them to a cache file, and open with
     *   [android.content.Intent.ACTION_VIEW] through FileProvider.
     *
     * Failures surface through [ChatUiState.openError] (non-blocking
     * snackbar); the tap is never swallowed.
     */
    fun openAttachment(attachment: Attachment) {
        viewModelScope.launch {
            val outcome =
                attachmentOpener.open(attachment) { path ->
                    uiStateMutable.update {
                        it.copy(
                            overlays = it.overlays.afterAttachmentOpening(path),
                        )
                    }
                }
            when (outcome) {
                is AttachmentOpenOutcome.PlayMedia ->
                    uiStateMutable.update {
                        it.copy(
                            overlays = it.overlays.afterMediaPlayerRequested(outcome.request),
                        )
                    }

                AttachmentOpenOutcome.Handled -> Unit

                is AttachmentOpenOutcome.Failed ->
                    showOpenError(
                        when (outcome.error) {
                            AttachmentOpenError.OPEN -> Res.string.attachment_error_open
                            AttachmentOpenError.NOT_FOUND -> Res.string.attachment_error_not_found
                            AttachmentOpenError.ACCESS_DENIED -> Res.string.attachment_error_access_denied
                            AttachmentOpenError.TOO_LARGE -> Res.string.attachment_error_too_large
                            AttachmentOpenError.SESSION_EXPIRED -> Res.string.attachment_error_session_expired
                            AttachmentOpenError.MISSING_GATEWAY_PATH -> Res.string.attachment_error_missing_gateway_path
                        },
                        outcome.name,
                    )
            }
        }
    }

    private suspend fun showOpenError(
        key: org.jetbrains.compose.resources.StringResource,
        vararg args: Any,
    ) {
        // Same pattern as the other Context-facing call sites in this
        // ViewModel: read the localized text through the StringResolver
        // seam (backed by Compose Resources) instead of calling
        // `getApplication<Application>().getString(R.string.x)`.
        val message = resolver.get(key, *args)
        uiStateMutable.update {
            it.copy(
                overlays = it.overlays.afterOpenErrorShown(message),
            )
        }
    }

    fun clearOpenError() {
        uiStateMutable.update {
            it.copy(
                overlays = it.overlays.afterOpenErrorCleared(),
            )
        }
    }

    fun closeMediaPlayer() {
        uiStateMutable.update {
            it.copy(
                overlays = it.overlays.afterMediaPlayerClosed(),
            )
        }
    }

    private fun serverMessageIndex(
        id: String,
        sessionId: String,
    ): Int? = id.removePrefix("rest-$sessionId-").takeIf { it != id }?.toIntOrNull()

    // ── UI actions ───────────────────────────────────────────────────────

    /**
     * Dismiss the active clarify prompt and reject it (tell the agent no answer
     * was given).
     *
     * The backend's clarify tool blocks the agent thread waiting for a response
     * (CLI timeout is 120s). A silent dismiss would leave the agent hanging
     * until that timeout, so we send a cancel sentinel
     * ([CLARIFY_DISMISS_RESPONSE]) over `clarify.respond` to unblock it.
     *
     * This is a *reject*, not an instruction to proceed — the agent is told no
     * answer was provided and should re-ask or back off, NOT charge ahead.
     *
     * Unlike [respondToClarify] we do NOT append a user chat bubble: a dismiss
     * is not something the user typed, so faking a USER message would be
     * dishonest. We instead surface a short SYSTEM note so the dismissal is
     * visible in the transcript.
     */
    fun dismissClarify() {
        val state = uiStateMutable.value
        // Hoisted: smart casts do not cross module boundaries, and
        // ChatUiState now lives in :shared.
        val currentSessionId = state.currentSessionId
        if (currentSessionId == null) return
        val runtimeId = runtimeSessionId ?: return
        val expected = state.clarifyRequest ?: return
        val requestId = expected.clarifyId ?: return
        val profileId = expected.sourceProfileId ?: return
        val generation = expected.connectionGeneration ?: return
        if (expected.sessionId != runtimeId) return
        viewModelScope.launch(io) {
            expected.serverRequestBinding?.let { serverBinding ->
                if (wsClient.respondToServerRequest(serverBinding, buildJsonObject {})) {
                    if (replaceClarifyIfCurrent(expected, null)) {
                        addSystemMessage("Clarify dismissed — no answer sent", persist = true)
                    }
                }
                return@launch
            }
            val dismissedIds = mutableSetOf<String>()
            for (question in expected.resolvedQuestions) {
                if (!clarifyRequestIsCurrent(expected, currentSessionId, runtimeId)) break
                val sent =
                    wsClient.respondToClarify(
                        sessionId = runtimeId,
                        clarifyRequestId = requestId,
                        questionId = expected.wireQuestionId(question),
                        answer = CLARIFY_DISMISS_RESPONSE,
                        sourceProfileId = profileId,
                        sourceConnectionGeneration = generation,
                    )
                if (!sent) break
                dismissedIds += question.qid
            }
            if (dismissedIds.isEmpty()) return@launch

            val remaining = expected.resolvedQuestions.filterNot { it.qid in dismissedIds }
            val completedCurrentRequest =
                replaceClarifyIfCurrent(expected, expected.withRemainingQuestions(remaining))
            if (completedCurrentRequest && remaining.isEmpty()) {
                addSystemMessage("Clarify dismissed — no answer sent", persist = true)
            }
        }
    }

    fun respondToClarify(option: String) {
        val clarify = uiStateMutable.value.clarifyRequest ?: return
        val qid = clarify.questionId ?: clarify.questions.singleOrNull()?.qid
        respondToClarifyBatch(clarify, mapOf((qid ?: "q0") to option))
    }

    fun respondToClarifyBatch(
        expected: ClarifyUi,
        answers: Map<String, String>,
    ) {
        val state = uiStateMutable.value
        val sessionId = state.currentSessionId ?: return
        val runtimeId = runtimeSessionId ?: return
        val profileId = expected.sourceProfileId ?: return
        val generation = expected.connectionGeneration ?: return
        if (state.clarifyRequest != expected ||
            expected.sessionId != runtimeId
        ) {
            return
        }
        val questions = expected.resolvedQuestions
        val normalized = answers.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
        if (normalized.isEmpty() || normalized.keys.any { answerId -> questions.none { it.qid == answerId } }) return
        val requestId = expected.clarifyId ?: return
        val acceptedRevision = repo.replacementGeneration(sessionId)
        viewModelScope.launch(io) {
            expected.serverRequestBinding?.let { serverBinding ->
                val merged = normalized + expected.lockedAnswers
                val result =
                    if (expected.questions.isNotEmpty()) {
                        buildJsonObject {
                            put("answers", buildJsonObject { merged.forEach { (id, answer) -> put(id, answer) } })
                        }
                    } else {
                        buildJsonObject { put("answer", normalized.values.first()) }
                    }
                if (!wsClient.respondToServerRequest(serverBinding, result)) return@launch
                val displayAnswer = normalized.values.joinToString("\n")
                val userMessage = ChatMessage(role = MessageRole.USER, content = displayAnswer)
                if (replaceClarifyIfCurrent(expected, null)) {
                    uiStateMutable.update {
                        it.copy(
                            transcript = it.transcript.copy(messages = it.messages + userMessage, isAgentTyping = true),
                        )
                    }
                    repo.persistMessage(userMessage, sessionId, acceptedRevision)
                }
                return@launch
            }
            val answeredIds = mutableSetOf<String>()
            for (question in questions) {
                val answer = normalized[question.qid] ?: continue
                if (!clarifyRequestIsCurrent(expected, sessionId, runtimeId)) break
                val sent =
                    wsClient.respondToClarify(
                        sessionId = runtimeId,
                        clarifyRequestId = requestId,
                        questionId = expected.wireQuestionId(question),
                        answer = answer,
                        sourceProfileId = profileId,
                        sourceConnectionGeneration = generation,
                    )
                if (!sent) break
                answeredIds += question.qid
            }
            if (answeredIds.isEmpty()) return@launch

            val remaining = questions.filterNot { it.qid in answeredIds }
            val displayAnswer =
                questions.filter { it.qid in answeredIds }.joinToString(
                    "\n",
                ) { normalized.getValue(it.qid) }
            val userMessage = ChatMessage(role = MessageRole.USER, content = displayAnswer)
            uiStateMutable.update { current ->
                current.copy(
                    transcript =
                        current.transcript.copy(
                            messages = current.messages + userMessage,
                            isAgentTyping = remaining.isEmpty(),
                        ),
                    overlays =
                        OverlaysReducer.reduce(
                            current.overlays,
                            OverlaysIntent.ClarifyReplaced(
                                expected = expected,
                                replacement =
                                    if (remaining.isEmpty()) {
                                        null
                                    } else {
                                        expected.withRemainingQuestions(remaining)
                                    },
                            ),
                        ),
                )
            }
            if (uiStateMutable.value.messages.any { it.id == userMessage.id }) {
                repo.persistMessage(userMessage, sessionId, acceptedRevision)
            }
        }
    }

    private fun clarifyRequestIsCurrent(
        expected: ClarifyUi,
        sessionId: String,
        runtimeId: String,
    ): Boolean =
        uiStateMutable.value.clarifyRequest == expected &&
            uiStateMutable.value.currentSessionId == sessionId &&
            runtimeSessionId == runtimeId

    private fun replaceClarifyIfCurrent(
        expected: ClarifyUi,
        replacement: ClarifyUi?,
    ): Boolean {
        while (true) {
            val current = uiStateMutable.value
            if (current.clarifyRequest != expected) return false
            if (uiStateMutable.compareAndSet(
                    current,
                    current.copy(
                        overlays =
                            OverlaysReducer.reduce(
                                current.overlays,
                                OverlaysIntent.ClarifyReplaced(expected = expected, replacement = replacement),
                            ),
                    ),
                )
            ) {
                return true
            }
        }
    }

    fun clearError() {
        uiStateMutable.update {
            it.copy(
                transcript = it.transcript.copy(errorMessage = null),
            )
        }
    }

    fun clearBackgroundComplete() {
        uiStateMutable.update {
            it.copy(
                overlays = it.overlays.afterBackgroundCompleteCleared(),
            )
        }
    }

    // ── Approval flow ───────────────────────────────────────────────────

    internal fun serverCancellationMatches(
        binding: ServerRequestBinding?,
        event: WsEvent.ServerRequestCancelled,
    ): Boolean =
        binding?.requestId == event.id &&
            binding.runtimeSessionId == event.sessionId &&
            binding.profileId == event.sourceProfileId &&
            binding.connectionGeneration == event.connectionGeneration

    /**
     * Retire the controls once the gateway's own approval deadline has elapsed
     * locally. The gateway publishes the exact lifetime its waiting thread uses
     * (`timeout_seconds`), so the buttons disappear when the request they would
     * answer is already gone, rather than lingering and resolving nothing.
     */
    internal fun scheduleApprovalExpiry(
        messageId: String,
        binding: PrivilegedRequestBinding,
        timeoutSeconds: Double,
    ) {
        approvalExpiry.schedule(messageId, binding, timeoutSeconds) { id, owner -> clearApprovalControls(id, owner) }
    }

    /** Approve exactly once. Session-wide and permanent allows are not offered. */
    fun respondToApproval(
        messageId: String,
        binding: PrivilegedRequestBinding,
        action: String,
    ) {
        val choice =
            when (action) {
                "approve" -> "once"
                "deny" -> "deny"
                // "session" / "always" are deliberately unreachable: a phone
                // cannot show what a standing allow would later authorize.
                else -> return
            }
        submitApproval(messageId, binding, WsMethods.APPROVAL_RESPOND, mapOf("choice" to choice))
    }

    /** Explicit Cancel — a typed `approval.cancel`, not a denial and not a dismissal. */
    fun cancelApproval(
        messageId: String,
        binding: PrivilegedRequestBinding,
    ) {
        submitApproval(messageId, binding, WsMethods.APPROVAL_CANCEL, emptyMap())
    }

    private fun submitApproval(
        messageId: String,
        binding: PrivilegedRequestBinding,
        method: String,
        params: Map<String, String>,
    ) {
        if (!claimApprovalSubmission(messageId, binding)) return

        viewModelScope.launch(io) {
            val serverBinding =
                uiStateMutable.value.messages.firstOrNull { it.id == messageId }
                    ?.approvalInfo?.serverRequestBinding
            var failureMessage: String? = null
            val accepted =
                if (serverBinding != null) {
                    wsClient.respondToServerRequest(
                        serverBinding,
                        buildJsonObject {
                            put(
                                "choice",
                                params["choice"] ?: if (method == WsMethods.APPROVAL_CANCEL) "deny" else "once",
                            )
                            put("all", false)
                        },
                    ).also { if (!it) failureMessage = "Request was not accepted" }
                } else {
                    // The failure is surfaced to the user as `failureMessage`, so this one
                    // is handled rather than silent (inventory: handled).
                    runCatching {
                        wsClient.privilegedRequest(method = method, binding = binding, params = params).await()
                    }.fold(
                        onSuccess = { true },
                        onFailure = {
                            failureMessage = it.message
                            false
                        },
                    )
                }
            if (accepted) {
                approvalExpiry.cancel(messageId, binding)
                clearApprovalControls(messageId, binding)
            } else {
                restoreApprovalControls(messageId, binding, failureMessage)
            }
        }
    }

    private fun claimApprovalSubmission(
        messageId: String,
        binding: PrivilegedRequestBinding,
    ): Boolean {
        while (true) {
            val state = uiStateMutable.value
            var matched = false
            val messages =
                state.messages.map { message ->
                    val info = message.approvalInfo
                    if (message.id == messageId && info?.privilegedBinding == binding && !info.isSubmitting) {
                        matched = true
                        message.copy(approvalInfo = info.copy(isSubmitting = true))
                    } else {
                        message
                    }
                }
            if (!matched) return false
            if (uiStateMutable.compareAndSet(
                    state,
                    state.copy(
                        transcript = state.transcript.copy(messages = messages),
                    ),
                )
            ) {
                return true
            }
        }
    }

    private fun restoreApprovalControls(
        messageId: String,
        binding: PrivilegedRequestBinding,
        errorMessage: String?,
    ) {
        uiStateMutable.update { state ->
            state.copy(
                transcript =
                    state.transcript.copy(
                        messages =
                            state.messages.map { message ->
                                val info = message.approvalInfo
                                if (message.id == messageId && info?.privilegedBinding == binding) {
                                    message.copy(approvalInfo = info.copy(isSubmitting = false))
                                } else {
                                    message
                                }
                            },
                        errorMessage = errorMessage,
                    ),
            )
        }
    }

    private fun clearApprovalControls(
        messageId: String,
        binding: PrivilegedRequestBinding,
    ) {
        uiStateMutable.update { state ->
            state.copy(
                transcript =
                    state.transcript.copy(
                        messages =
                            state.messages.map { message ->
                                if (message.id == messageId && message.approvalInfo?.privilegedBinding == binding) {
                                    message.copy(approvalInfo = null)
                                } else {
                                    message
                                }
                            },
                    ),
            )
        }
    }

    // ── Sudo / secret prompt flow (issue #524) ──────────────────────────

    // The request handlers themselves moved to ChatServerRequests.kt; what remains here is the
    // dialog dismissal, which is deliberately a no-op (see below).

    /**
     * Back gesture or a tap outside the dialog. Deliberately a no-op: an
     * incidental dismissal is neither an answer nor a cancellation, and hiding
     * the dialog would strand a gateway thread that is still blocked. The user
     * cancels through the explicit Cancel action.
     */
    fun dismissSudo() = Unit

    /** Ordinary dismissal of the secret dialog. A no-op, for the same reason as [dismissSudo]. */
    fun dismissSecret() = Unit

    /** Send the user's sudo password back to the gateway on its exact bound request. */
    fun respondToSudo(password: String) {
        val prompt = uiStateMutable.value.sudoPrompt ?: return
        if (password.isBlank()) return
        submitSudo(prompt, WsMethods.SUDO_RESPOND, mapOf("password" to password))
    }

    /** Explicit Cancel — a typed `sudo.cancel`, awaited, never a silent dismissal. */
    fun cancelSudo() {
        val prompt = uiStateMutable.value.sudoPrompt ?: return
        submitSudo(prompt, WsMethods.SUDO_CANCEL, emptyMap())
    }

    /** Send the user's secret value back to the gateway on its exact bound request. */
    fun respondToSecret(value: String) {
        val prompt = uiStateMutable.value.secretPrompt ?: return
        if (value.isBlank()) return
        submitSecret(prompt, WsMethods.SECRET_RESPOND, mapOf("value" to value))
    }

    /** Explicit Cancel — a typed `secret.cancel`, awaited, never a silent dismissal. */
    fun cancelSecret() {
        val prompt = uiStateMutable.value.secretPrompt ?: return
        submitSecret(prompt, WsMethods.SECRET_CANCEL, emptyMap())
    }

    fun respondToVault(value: String) {
        val prompt = uiStateMutable.value.vaultPrompt ?: return
        if (value.isBlank()) return
        submitVault(prompt, value)
    }

    fun respondToVaultLogin(
        identifier: String,
        password: String,
    ) {
        val prompt = uiStateMutable.value.vaultPrompt ?: return
        if (prompt.method != "vault.save_login" ||
            !prompt.hasValidRequestedOrigin ||
            identifier.isBlank() ||
            password.isBlank()
        ) {
            return
        }
        submitVault(
            prompt,
            buildJsonObject {
                put("identifier", identifier)
                put("password", password)
            }.toString(),
        )
    }

    fun cancelVault() {
        uiStateMutable.value.vaultPrompt?.let { submitVault(it, "") }
    }

    fun dismissVault() = Unit

    private fun submitVault(
        expected: VaultPromptUi,
        value: String,
    ) {
        val claimed = expected.copy(isSubmitting = true)
        while (true) {
            val state = uiStateMutable.value
            if (state.vaultPrompt != expected || expected.isSubmitting) return
            if (uiStateMutable.compareAndSet(
                    state,
                    state.copy(
                        overlays = state.overlays.afterVaultPromptClaimed(expected),
                    ),
                )
            ) {
                break
            }
        }
        viewModelScope.launch(io) {
            val accepted = wsClient.respondToServerRequest(claimed.binding, buildJsonObject { put("value", value) })
            uiStateMutable.update { state ->
                if (state.vaultPrompt != claimed) {
                    state
                } else if (accepted) {
                    state.copy(
                        overlays = state.overlays.afterVaultPromptAnswered(claimed),
                    )
                } else {
                    state.copy(
                        transcript = state.transcript.copy(errorMessage = privilegedFailureMessage()),
                        overlays = state.overlays.afterVaultPromptReleased(claimed),
                    )
                }
            }
        }
    }

    private fun submitSudo(
        prompt: SudoPromptUi,
        method: String,
        params: Map<String, String>,
    ) {
        val claimed = claimSudoSubmission(prompt) ?: return
        viewModelScope.launch(io) {
            val accepted =
                claimed.serverRequestBinding?.let {
                    wsClient.respondToServerRequest(
                        it,
                        buildJsonObject { put("value", params["password"].orEmpty()) },
                    )
                } ?: runCatching {
                    wsClient.privilegedRequest(method = method, binding = claimed.binding, params = params).await()
                }.onFailure {
                    // The prompt stays up on failure, which is right, but without this line
                    // nobody can tell a rejected password from a broken socket.
                    HermesLog.w(TAG, "privileged request failed while answering a prompt: ${it.message}")
                }.isSuccess
            if (accepted) {
                uiStateMutable.update {
                    if (it.sudoPrompt == claimed) {
                        it.copy(
                            overlays = it.overlays.afterSudoPromptAnswered(claimed),
                        )
                    } else {
                        it
                    }
                }
            } else {
                uiStateMutable.update {
                    if (it.sudoPrompt == claimed) {
                        it.copy(
                            transcript = it.transcript.copy(errorMessage = privilegedFailureMessage()),
                            overlays = it.overlays.afterSudoPromptReleased(claimed),
                        )
                    } else {
                        it
                    }
                }
            }
        }
    }

    private fun claimSudoSubmission(expected: SudoPromptUi): SudoPromptUi? {
        val claimed = expected.copy(isSubmitting = true)
        while (true) {
            val state = uiStateMutable.value
            if (state.sudoPrompt != expected || expected.isSubmitting) return null
            if (uiStateMutable.compareAndSet(
                    state,
                    state.copy(
                        overlays = state.overlays.afterSudoPromptClaimed(expected),
                    ),
                )
            ) {
                return claimed
            }
        }
    }

    private fun submitSecret(
        prompt: SecretPromptUi,
        method: String,
        params: Map<String, String>,
    ) {
        val claimed = claimSecretSubmission(prompt) ?: return
        viewModelScope.launch(io) {
            val accepted =
                claimed.serverRequestBinding?.let {
                    wsClient.respondToServerRequest(
                        it,
                        buildJsonObject { put("value", params["value"].orEmpty()) },
                    )
                } ?: runCatching {
                    wsClient.privilegedRequest(method = method, binding = claimed.binding, params = params).await()
                }.onFailure {
                    // The prompt stays up on failure, which is right, but without this line
                    // nobody can tell a rejected password from a broken socket.
                    HermesLog.w(TAG, "privileged request failed while answering a prompt: ${it.message}")
                }.isSuccess
            if (accepted) {
                uiStateMutable.update {
                    if (it.secretPrompt == claimed) {
                        it.copy(
                            overlays = it.overlays.afterSecretPromptAnswered(claimed),
                        )
                    } else {
                        it
                    }
                }
            } else {
                uiStateMutable.update {
                    if (it.secretPrompt == claimed) {
                        it.copy(
                            transcript = it.transcript.copy(errorMessage = privilegedFailureMessage()),
                            overlays = it.overlays.afterSecretPromptReleased(claimed),
                        )
                    } else {
                        it
                    }
                }
            }
        }
    }

    private fun claimSecretSubmission(expected: SecretPromptUi): SecretPromptUi? {
        val claimed = expected.copy(isSubmitting = true)
        while (true) {
            val state = uiStateMutable.value
            if (state.secretPrompt != expected || expected.isSubmitting) return null
            if (uiStateMutable.compareAndSet(
                    state,
                    state.copy(
                        overlays = state.overlays.afterSecretPromptClaimed(expected),
                    ),
                )
            ) {
                return claimed
            }
        }
    }

    /**
     * Fixed failure text for the two secret-bearing verbs.
     *
     * A gateway error string is attacker- or bug-reachable and could echo the
     * submitted value; `errorMessage` is durable UI state. So sudo and secret
     * failures never render the throwable — only this constant.
     */
    private suspend fun privilegedFailureMessage(): String = resolver.get(Res.string.chat_privileged_not_acknowledged)

    /**
     * Bind a privileged event to the exact request it can answer.
     *
     * Every component has to be present and current at dispatch: the opaque
     * request id, the runtime session showing the request, the profile whose
     * connection delivered it, and that connection's generation. Anything
     * missing or stale yields `null`, and the caller drops the event rather than
     * surfacing controls that would resolve some other request.
     */
    internal fun privilegedBinding(
        requestId: String,
        eventSessionId: String?,
        profileId: String?,
        generation: Int?,
    ): PrivilegedRequestBinding? {
        if (requestId.isBlank()) return null
        if (generation == null) return null
        if (profileId.isNullOrBlank() || profileId != selectedGatewayId()) return null
        val runtimeId = eventSessionId?.takeIf { it.isNotBlank() } ?: return null
        if (runtimeId != runtimeSessionId) return null
        return PrivilegedRequestBinding(
            requestId = requestId,
            runtimeSessionId = runtimeId,
            profileId = profileId,
            connectionGeneration = generation,
        )
    }

    /**
     * A server expiry clears a live prompt only when it names that same exact
     * request on the same session, profile, and socket generation.
     */

    fun relogin(
        username: String,
        password: String,
        onResult: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch(io) {
            val endpoint =
                try {
                    authStore.endpoint()
                } catch (e: IllegalArgumentException) {
                    withContext(Dispatchers.Main) {
                        onResult(false, e.message)
                    }
                    return@launch
                }
            val jsonBody = AuthPayloads.passwordLogin(username, password)

            try {
                val status = api.loginWithPassword(jsonBody = jsonBody)
                if (status !in 200..299) {
                    val msg =
                        when (status) {
                            401 -> "Invalid username or password (401)"
                            403 -> "Forbidden (403)"
                            else -> "HTTP error code: $status"
                        }
                    withContext(Dispatchers.Main) {
                        onResult(false, msg)
                    }
                    return@launch
                }

                // The login response updated the shared CookieJar. Keep the
                // one-use WebSocket ticket out of persistent token state;
                // reconnect() lets HermesWsClient mint it for the handshake.
                authStore.setToken(null)
                authStore.setWsAuthParam("ticket")
                AuthSessionState.markAuthenticated()

                withContext(Dispatchers.Main) {
                    onResult(true, null)
                    reconnect()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                withContext(Dispatchers.Main) {
                    onResult(false, "Connection failed: ${e.message}")
                }
            }
        }
    }

    internal fun addSystemMessage(
        text: String,
        persist: Boolean = false,
        transient: Boolean = false,
    ) {
        // Likivik patch: connection/resume noise ("Session resumed", "Connected
        // to Hermes") used to be appended as list items, which bumped the whole
        // chat via tail-follow scrolling. These are surfaced as a transient
        // status pill instead and never enter the message list.
        if (transient) {
            uiStateMutable.update { it.copy(connection = it.connection.afterStatusPill(text)) }
            statusPillJob?.cancel()
            statusPillJob =
                viewModelScope.launch {
                    kotlinx.coroutines.delay(2500)
                    uiStateMutable.update { it.copy(connection = it.connection.afterStatusPillCleared()) }
                }
            return
        }
        val msg = ChatMessage(role = MessageRole.SYSTEM, content = text)
        val sessionId = uiStateMutable.value.currentSessionId

        uiStateMutable.update {
            it.copy(
                transcript = it.transcript.copy(messages = it.messages + msg),
            )
        }

        // Persist — OUTSIDE update{}
        if (persist && sessionId != null) {
            val acceptedRevision = repo.replacementGeneration(sessionId)
            viewModelScope.launch(io) {
                repo.persistMessage(msg, sessionId, acceptedRevision)
            }
        }
    }

    // ── Pending request tracking ─────────────────────────────────────────

    private fun trackRequest(
        id: String,
        method: String,
    ) {
        pendingRequests[id] = PendingRpcRequest(method)
    }

    /**
     * Track a `session.redirect` alongside the text it carried, so a `4010`
     * rejection (running agent cannot be redirected) can resend the same text
     * as a normal prompt instead of dropping it.
     */
    private fun trackRedirectRequest(
        id: String,
        sessionId: String,
        text: String,
        connectionBinding: ConnectionBinding,
    ) {
        pendingRequests[id] =
            PendingRpcRequest(
                method = WsMethods.SESSION_REDIRECT,
                redirectSessionId = sessionId,
                redirectText = text,
                redirectConnectionBinding = connectionBinding,
            )
    }

    private fun captureResumeFence(sessionId: String): ResumeFence? {
        val profileId = selectedGatewayId()
        val binding = wsClient.connectionBinding(profileId) ?: return null
        return ResumeFence(
            storageSessionId = sessionId,
            runtimeSessionId = runtimeSessionId,
            profileId = profileId,
            connectionBinding = binding,
            generation = conversationGeneration,
            transcriptRevision = repo.replacementGeneration(sessionId),
        )
    }

    internal fun isResumeFenceCurrent(
        fence: ResumeFence,
        allowRuntimeChange: Boolean = false,
        allowSessionChange: Boolean = false,
    ): Boolean =
        fence.generation == conversationGeneration &&
            fence.profileId == selectedGatewayId() &&
            (allowSessionChange || fence.storageSessionId == uiStateMutable.value.currentSessionId) &&
            (allowRuntimeChange || fence.runtimeSessionId == runtimeSessionId) &&
            fence.transcriptRevision == repo.replacementGeneration(fence.storageSessionId) &&
            wsClient.isConnectionBindingCurrent(fence.connectionBinding)

    private fun trackResumeRequest(
        id: String,
        fence: ResumeFence,
    ) {
        synchronizedOn(resumeRequestLock) {
            invalidateResumeRequestsLocked()
            pendingRequests[id] =
                PendingRpcRequest(
                    method = WsMethods.SESSION_RESUME,
                    resumeSessionId = fence.storageSessionId,
                    resumeFence = fence,
                )
        }
    }

    private fun invalidateResumeRequests() {
        synchronizedOn(resumeRequestLock) {
            invalidateResumeRequestsLocked()
        }
    }

    private fun invalidateResumeRequestsLocked() {
        val superseded =
            pendingRequests.entries
                .filter { (_, request) -> request.method == WsMethods.SESSION_RESUME }
                .map { it.key }
        for (id in superseded) {
            retiredResumeRequests += id
            while (retiredResumeRequests.size > MAX_RETIRED_RESUME_REQUESTS) {
                retiredResumeRequests.remove(retiredResumeRequests.first())
            }
            pendingRequests.remove(id)
        }
    }

    internal fun consumeRetiredResumeRequest(id: String): Boolean =
        synchronizedOn(resumeRequestLock) {
            retiredResumeRequests.remove(id)
        }

    /**
     * Track a `session.create` alongside the attempt generation that issued it,
     * so a late answer from a superseded attempt cannot install itself over a
     * newer session, and so an answered attempt can stand its retry timer down.
     */
    private fun trackCreateRequest(
        id: String,
        generation: Long,
    ) {
        pendingRequests[id] =
            PendingRpcRequest(
                method = WsMethods.SESSION_CREATE,
                createGeneration = generation,
            )
    }

    private fun trackBranchRequest(
        id: String,
        sessionId: String,
    ) {
        pendingRequests[id] =
            PendingRpcRequest(
                method = WsMethods.SESSION_BRANCH,
                branchSessionId = sessionId,
            )
    }

    // ── Search ────────────────────────────────────────────────────────────
    // Compatibility façade: stable public API around ChatSearchDelegate.
    // These thin delegates keep ChatViewModel's public surface intact while
    // the search logic now lives in the delegate. Safe to remove once all
    // callers migrate directly to the delegate.

    fun toggleSearch() = searchDelegate.toggleSearch()

    fun setSearchQuery(query: String) = searchDelegate.setSearchQuery(query)

    fun navigateSearchMatch(direction: Int) = searchDelegate.navigateSearchMatch(direction)

    fun clearSearch() = searchDelegate.clearSearch()

    private var isTestEnv: Boolean? = null

    private fun isTestEnvironment(): Boolean = isTestEnv ?: isRunningUnderTest().also { isTestEnv = it }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        // PERF-16: Don't disconnect the global HermesWsClient singleton when
        // leaving the Chat screen — it's used by background notification reply.
    }

    companion object {
    }

    fun reconnect() {
        retireSync()
        AuthSessionState.markAuthenticated()
        uiStateMutable.update { state ->
            state.copy(
                transcript = TranscriptReducer.reduce(state.transcript, TranscriptIntent.LoadStarted),
            )
        }
        viewModelScope.launch(io) {
            wsClient.rejectAllPending()
            wsClient.disconnect()
        }
        viewModelScope.launch {
            delay(500)
            connectWebSocket(setLoading = true)
        }
    }
}
