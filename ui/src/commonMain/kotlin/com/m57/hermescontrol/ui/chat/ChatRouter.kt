package com.m57.hermescontrol.ui.chat

// The event router, out of ChatViewModel but not yet out of its object.
//
// handleWsEvent (338 lines) and handleRpcResult (291) were 12% of the ViewModel and the two functions
// every later change had to read. Moving them to extension functions in their own file is a file split,
// not a decomposition: they still reach the ViewModel's internals, which is why a number of members
// below became `internal` instead of `private`.
//
// That is a deliberate, temporary trade. Batch 4 of docs/plans/chat-architecture.md replaces the
// widened members with an explicit host interface, at which point the router becomes a real
// collaborator and this file can be tested without a ViewModel at all.

import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.model.SessionInfo
import com.m57.hermescontrol.data.session.ActiveSessionHolder
import com.m57.hermescontrol.data.ws.WsEvent
import com.m57.hermescontrol.data.ws.WsMethods
import com.m57.hermescontrol.util.HermesLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun ChatViewModel.handleWsEvent(event: WsEvent) {
    val rpcId =
        when (event) {
            is WsEvent.RpcResult -> event.id
            is WsEvent.RpcError -> event.id
            else -> null
        }
    if (rpcId != null && consumeRetiredResumeRequest(rpcId)) return
    val pending = rpcId?.let(pendingRequests::get)
    // The fork's two utility resumes (`renameSession`, `startCloseThenDelete`)
    // deliberately target a session that is NOT the currently selected one —
    // the resume's only job is to learn a live runtime sid. The inner
    // SESSION_RESUME dispatch already relaxes the fence's SESSION axis for
    // them; the outer guard must do the same so a "stale-looking" fence
    // (mismatched storageSessionId) isn't dropped before the ack handler
    // can run. An absent fence is also tolerated for utility resumes — they
    // were just sent by an explicit user action rather than restored.
    if (pending?.method == WsMethods.SESSION_RESUME) {
        val utilityResume = pending.pendingRenameTitle != null || pending.closeAfterResume
        val fence = pending.resumeFence
        if (fence == null) {
            if (!utilityResume) {
                pendingRequests.remove(rpcId)
                return
            }
        } else if (!isResumeFenceCurrent(fence, allowSessionChange = utilityResume)) {
            pendingRequests.remove(rpcId)
            return
        }
    }
    if (
        pending?.method == WsMethods.SESSION_BRANCH &&
        pending.branchSessionId != null &&
        pending.branchSessionId != runtimeSessionId
    ) {
        pendingRequests.remove(rpcId)
        return
    }

    val transitionSessionId =
        when (event) {
            is WsEvent.MessageStart -> event.sessionId
            is WsEvent.MessageComplete -> event.sessionId
            is WsEvent.MessageDone -> event.sessionId
            is WsEvent.ToolStart -> event.sessionId
            else -> null
        }
    if (transitionSessionId != null && !isCurrentSession(transitionSessionId)) {
        return
    }

    when (event) {
        is WsEvent.MessageStart,
        is WsEvent.MessageComplete,
        is WsEvent.MessageDone,
        is WsEvent.ToolStart,
        -> streamingController.flushPendingReasoning()

        else -> Unit
    }

    // First, let the reducer compute the new state and any effects
    val result =
        ChatWsEventReducer.reduce(
            uiStateMutable.value,
            streamingStateMutable.value,
            event,
            runtimeSessionId ?: uiStateMutable.value.currentSessionId,
        )

    // Apply the new state
    uiStateMutable.update { result.state }
    streamingStateMutable.update { result.streamingState }

    // Process side-effects from the reducer
    for (effect in result.effects) {
        when (effect) {
            is ReducerEffect.PersistMessage -> {
                val acceptedRevision = repo.replacementGeneration(effect.sessionId)
                viewModelScope.launch(io) {
                    repo.persistMessage(effect.message, effect.sessionId, acceptedRevision)
                }
            }

            is ReducerEffect.CreateNewSession -> {
                createNewSession()
            }

            is ReducerEffect.LoadSessions -> {
                loadSessions()
            }

            is ReducerEffect.RefreshSessions -> {
                loadSessions()
            }
            is ReducerEffect.AttachHostMedia -> {
                // Issue #724: turn host-path MEDIA: directives into real
                // attachments (images inline, every other file tappable)
                // via the gateway /api/files/download endpoint. Works on a
                // remote phone too.
                val acceptedRevision = repo.replacementGeneration(effect.sessionId)
                viewModelScope.launch(io) {
                    attachHostMedia(effect.sessionId, effect.messageId, acceptedRevision)
                }
            }
        }
    }

    // Handle complex events that need ViewModel-specific context
    when (event) {
        is WsEvent.ServerRequest -> handleServerRequest(event)

        is WsEvent.ServerRequestCancelled -> handleServerRequestCancelled(event)

        is WsEvent.GatewayReady -> {
            handleGatewayReady()
        }

        is WsEvent.SessionInfo -> {
            if (isCurrentSession(event.sessionId)) {
                val info = event.data

                val usage = info?.get("usage") as? Map<*, *>
                val model = (info?.get("model") as? String)?.trim().orEmpty()
                val provider = (info?.get("provider") as? String)?.trim().orEmpty()
                val reasoningEffort = (info?.get("reasoning_effort") as? String)?.trim()
                val terminalBackend = (info?.get("terminal_backend") as? String)?.trim()
                uiStateMutable.update { state ->
                    state.copy(
                        transcript =
                            state.transcript.copy(
                                contextUsage = parseContextUsage(usage, state.contextUsage),
                            ),
                        composer =
                            state.composer.copy(
                                currentSessionModel =
                                    if (model.isNotEmpty()) {
                                        qualifyModelLabel(provider, model)
                                    } else {
                                        state.currentSessionModel
                                    },
                                reasoningLevel =
                                    reasoningEffort?.takeIf {
                                        it.isNotEmpty()
                                    },
                                terminalBackend =
                                    terminalBackend?.takeIf { it.isNotEmpty() } ?: state.terminalBackend,
                            ),
                    )
                }
            }
        }

        is WsEvent.MessageToken -> {
            streamingController.handleMessageToken(event)
        }

        is WsEvent.ThinkingDelta -> {
            streamingController.handleThinkingDelta(event)
        }

        is WsEvent.ReasoningDelta -> {
            streamingController.handleReasoningDelta(event)
        }

        is WsEvent.MessageStart -> {
            streamingController.beginStreamingMessage()
        }

        is WsEvent.MessageComplete -> {
            // Buffers cleared before reduce; ViewModel resets them after
            streamingController.resetStreaming()
            if (isCurrentSession(event.sessionId)) {
                // End of turn is when the gateway recomputes window
                // occupancy, so this frame carries the freshest gauge the
                // client will see before the next `session.info`.
                uiStateMutable.update { state ->
                    state.copy(
                        transcript =
                            state.transcript.copy(
                                contextUsage = parseContextUsage(event.usage, state.contextUsage),
                            ),
                    )
                }
            }
        }

        is WsEvent.MessageDone -> {
            streamingController.resetStreaming()
        }

        is WsEvent.ToolStart -> {
            // Reset streaming state when a tool starts
            streamingController.resetStreaming()
        }

        is WsEvent.RpcResult -> {
            handleRpcResult(event.id, event.result)
        }

        is WsEvent.RpcError -> {
            handleRpcError(event.id, event.error)
        }

        is WsEvent.SessionUpdated -> {
            loadSessions()
        }

        is WsEvent.SessionUsage -> {
            if (isCurrentSession(event.sessionId)) {
                val usage =
                    (event.data?.get("usage") as? Map<*, *>)
                        ?: event.data
                uiStateMutable.update { state ->
                    state.copy(
                        transcript =
                            state.transcript.copy(
                                contextUsage =
                                    parseContextUsage(usage, state.contextUsage),
                            ),
                    )
                }
            }
        }

        is WsEvent.ClarifyRequest -> {
            uiStateMutable.update {
                it.copy(
                    transcript = it.transcript.copy(isAgentTyping = false),
                )
            }
            streamingStateMutable.update { StreamingState() }
            streamingController.resetStreaming()
        }

        is WsEvent.ApprovalRequest -> {
            handleApprovalRequest(event)
        }

        is WsEvent.SudoRequest -> {
            handleSudoRequest(event)
        }

        is WsEvent.SudoExpire -> {
            uiStateMutable.update { state ->
                val expired =
                    state.sudoPrompt?.binding?.let {
                        expiryMatches(
                            binding = it,
                            requestId = event.requestId,
                            sessionId = event.sessionId,
                            profileId = event.sourceProfileId,
                            generation = event.connectionGeneration,
                        )
                    } ?: false
                if (expired) {
                    state.copy(
                        overlays = state.overlays.copy(sudoPrompt = null),
                    )
                } else {
                    state
                }
            }
        }

        is WsEvent.SecretRequest -> {
            handleSecretRequest(event)
        }

        is WsEvent.SecretExpire -> {
            uiStateMutable.update { state ->
                val expired =
                    state.secretPrompt?.binding?.let {
                        expiryMatches(
                            binding = it,
                            requestId = event.requestId,
                            sessionId = event.sessionId,
                            profileId = event.sourceProfileId,
                            generation = event.connectionGeneration,
                        )
                    } ?: false
                if (expired) {
                    state.copy(
                        overlays = state.overlays.copy(secretPrompt = null),
                    )
                } else {
                    state
                }
            }
        }

        is WsEvent.PrivilegedRequestRejected -> {
            // A privileged frame without a bindable request id. Never
            // surfaced and never answered — the parser already dropped it.
        }

        is WsEvent.GatewayError -> {
            // Reducer already set errorMessage; no extra VM work needed.
        }

        is WsEvent.BackgroundComplete -> {
            // Reducer already set backgroundCompleteMessage; the UI observes
            // it via a LaunchedEffect and triggers the snackbar.
        }

        is WsEvent.ReactionEvent -> {
            // Cancel any previous auto-clear to avoid race (agy finding #1)
            reactionClearJob?.cancel()
            uiStateMutable.update {
                it.copy(
                    transcript =
                        it.transcript.copy(
                            reactionKind = event.kind,
                            reactionTriggerId = it.reactionTriggerId + 1L,
                        ),
                )
            }
            // Auto-clear after the animation duration
            reactionClearJob =
                viewModelScope.launch {
                    delay(2_000L)
                    uiStateMutable.update {
                        it.copy(
                            transcript = it.transcript.copy(reactionKind = null),
                        )
                    }
                }
        }

        else -> { /* reducer handles these */ }
    }
}

internal fun ChatViewModel.handleRpcResult(
    id: String,
    result: Any?,
) {
    val request = pendingRequests.remove(id) ?: return
    val method = request.method
    when (method) {
        WsMethods.SESSION_CREATE -> {
            // A retried create can be answered twice, and a create issued
            // before a session switch can be answered after it. Only the
            // newest attempt may install a session.
            val generation = request.createGeneration
            if (generation != null && generation != sessionCreateCounter) {
                HermesLog.d(TAG, "Ignoring session.create result from a superseded attempt")
                return
            }
            val resultMap = result as? Map<String, Any?> ?: return
            val runtimeId = resultMap["session_id"] as? String ?: return
            val storageId = resultMap["stored_session_id"] as? String ?: runtimeId
            // The request is answered — stand the retry deadline down.
            sessionCreateJob?.cancel()
            sessionCreateJob = null
            runtimeSessionId = runtimeId
            uiStateMutable.update {
                it.copy(
                    transcript = it.transcript.withInstalledSession(storageId = storageId),
                    composer = it.composer.withNoSessionModel(),
                    overlays = it.overlays.copy(modelSwitchConfirmation = null),
                )
            }
            // Mirror the active session id app-wide so session-scoped
            // drawer screens (e.g. Processes, issue #532) can issue
            // session-scoped RPCs. See ActiveSessionHolder.
            ActiveSessionHolder.set(runtimeId, storageId)
            // Likivik patch: remember the freshly created session too.
            persistLastSession(storageId)
            streamingStateMutable.update { StreamingState() }
            addSystemMessage("Session created", persist = true)
            loadSessions()
        }

        WsMethods.SESSION_BRANCH -> {
            val resultMap = result as? Map<String, Any?> ?: return
            val newId = resultMap["session_id"] as? String ?: return

            val info = resultMap["info"] as? Map<*, *>
            val usage = info?.get("usage") as? Map<*, *>

            val model = (info?.get("model") as? String)?.trim().orEmpty()
            val provider = (info?.get("provider") as? String)?.trim().orEmpty()
            val reasoningEffort = (info?.get("reasoning_effort") as? String)?.trim()
            val terminalBackend = (info?.get("terminal_backend") as? String)?.trim()
            clearPrivilegedControls()
            runtimeSessionId = newId
            uiStateMutable.update {
                it.copy(
                    transcript =
                        it.transcript.withInstalledSession(
                            storageId = newId,
                            title =
                                (resultMap["title"] as? String)?.takeIf { title ->
                                    title.isNotBlank()
                                } ?: "Hermes",
                            // Never use the parent snapshot as the previous value:
                            // the branch response carries child-scoped info.
                            contextUsage = parseContextUsage(usage, previous = null),
                            replaceContextUsage = true,
                        ),
                    composer =
                        it.composer.withSessionModel(
                            provider = provider,
                            model = model,
                            reasoningLevel = reasoningEffort,
                            terminalBackend = terminalBackend,
                        ),
                )
            }
            ActiveSessionHolder.set(newId)
            streamingStateMutable.update { StreamingState() }
            addSystemMessage("Session branched", persist = true)
            loadSessionMessages(newId)
            loadSessions()
        }

        WsMethods.SESSION_LIST -> {
            // Signal only. REST is the source: its rows carry `last_active`, and a
            // second ordered copy here would need merging — the bug class this avoids.
            requestRailRefresh(force = true)
        }

        WsMethods.SESSION_RESUME -> {
            // Upstream fences resume acks so a stale answer cannot paint
            // over a session the user has since left. The fork's two UTILITY
            // resumes are a different shape: `renameSession` resumes purely
            // to learn a live runtime sid so it can address session.title,
            // and `startCloseThenDelete` does the same to close a runtime
            // before retrying a refused delete. Both legitimately target a
            // session that is NOT the selected one (a background rename), so
            // the fence's session axis is relaxed for them — and an absent
            // fence is tolerated, because such a request was just sent by an
            // explicit user action rather than restored from a previous life.
            val utilityResume = request.pendingRenameTitle != null || request.closeAfterResume
            val resumeFence = request.resumeFence
            if (resumeFence != null) {
                if (!isResumeFenceCurrent(resumeFence, allowSessionChange = utilityResume)) return
            } else if (!utilityResume) {
                return
            }
            val resultMap = result as? Map<String, Any?>
            val requestedSessionId = request.resumeSessionId
            val selectedSessionId = uiStateMutable.value.currentSessionId
            // Delete recovery: session.delete was refused (4023) because the
            // target is still LIVE server-side. This resume exists only to
            // learn its runtime sid so we can close it, then retry the
            // stored delete. Never touches the visible chat.
            if (request.closeAfterResume) {
                val runtimeToClose = resultMap?.get("session_id") as? String
                val stored = request.deleteSessionId
                if (runtimeToClose != null && stored != null) {
                    sendCloseSession(runtimeToClose, stored)
                } else {
                    HermesLog.w(
                        TAG,
                        "delete recovery: resume ack gave no runtime id for stored=$stored",
                    )
                }
                return
            }
            if (
                requestedSessionId != null &&
                selectedSessionId != requestedSessionId &&
                // Likivik patch: a rename-resume targets a rail item the
                // user ISN'T currently viewing. Never replace the active
                // chat for it, but do NOT drop it — the ack handler below
                // fires the queued session.title via the runtime id this
                // response carries (storage ids 4001 on session.title).
                request.pendingRenameTitle == null
            ) {
                return
            }
            val sessionId =
                (resultMap?.get("resumed") as? String)
                    ?: requestedSessionId
                    ?: selectedSessionId
            // Capture the PREVIOUS runtime id BEFORE the ack clobbers
            // runtimeSessionId below. A current-session rename (resume
            // fast-path) may ack WITHOUT session_id — we need the id the
            // chat already holds to fire session.title on it.
            val preAckRuntimeId = runtimeSessionId
            runtimeSessionId = resultMap?.get("session_id") as? String

            // Parse the session-scoped snapshot returned by the backend.
            val infoMap = resultMap?.get("info") as? Map<*, *>
            val model = infoMap?.get("model") as? String
            val provider = infoMap?.get("provider") as? String
            val reasoningEffort = infoMap?.get("reasoning_effort") as? String
            val terminalBackend = infoMap?.get("terminal_backend") as? String
            val usage = infoMap?.get("usage") as? Map<*, *>

            // B8 (Jun 20 2026, kanban t_session_resume): do NOT reload
            // cached messages here — switchSession() already did so before
            // the WS round-trip. Calling loadCachedMessages() here would
            // overwrite any message the user sent between switchSession() and
            // the server ack, making the chat appear to go blank.
            //
            // Likivik patch: a rename-resume (pendingRenameTitle != null)
            // MUST NOT hijack the UI onto the renamed background session —
            // it only exists to obtain a runtime id. Keep the current view.
            val renameOnly =
                request.pendingRenameTitle != null &&
                    requestedSessionId != null &&
                    selectedSessionId != requestedSessionId
            if (!renameOnly) {
                uiStateMutable.update {
                    it.copy(
                        transcript =
                            it.transcript.withResumedSession(
                                sessionId = sessionId,
                                // `switchSession()` cleared the previous session's reading;
                                // hydrate only from this resume response.
                                runtimeReady = runtimeSessionId != null,
                                contextUsage = parseContextUsage(usage, previous = null),
                            ),
                        composer =
                            it.composer.withResumedModel(
                                provider = provider,
                                model = model,
                                reasoningLevel = reasoningEffort,
                                terminalBackend = terminalBackend,
                            ),
                    )
                }
            }
            if (!renameOnly) {
                // Mirror the active runtime session id app-wide (issue #532).
                ActiveSessionHolder.set(runtimeSessionId ?: sessionId, sessionId)
                // No "Session resumed" notice: a resume ack is protocol plumbing, and
                // printing it made every rail click look like an event. The rail highlight
                // and the title already say which conversation is open.
            }
            if (sessionId != null && !renameOnly) {
                hydrateResumeMessages(
                    sessionId = sessionId,
                    payload = resultMap?.get("messages"),
                    replaceExisting =
                        requestedSessionId != null &&
                            requestedSessionId != sessionId,
                )
            }
            // A rename queued on this resume fires now that the runtime
            // session is live.
            //
            // ADDRESSING (root-caused against the gateway source,
            // tui_gateway/server.py `_sess_nowait`): `session.title` is a
            // SESSION-SCOPED RPC — it resolves params["session_id"] as an
            // EXACT key in the live `_sessions` map. That key is the RUNTIME
            // sid (the ack's "session_id"), never the stored id. The ack's
            // "session_key" is `_session_lookup_key` = agent.session_id =
            // the STORED key, so addressing the title with it always missed
            // and returned 4001 "session not found" — the rename silently
            // never applied. Always address the RUNTIME sid.
            //
            // Fast-path acks (already-live session) may omit session_id;
            // fall back to the cached runtime id ONLY for a current-session
            // rename — for a background rename that id belongs to the
            // session the user is viewing and would title the WRONG one.
            val ackRuntimeId = runtimeSessionId
            // Fallback to the cached runtime id ONLY for a current-session
            // rename (renameOnly == false). For a BACKGROUND rename the
            // cached id belongs to the session the user is viewing — using
            // it would title the WRONG session. If such an ack carries no
            // addressable id we send nothing (and log it) rather than
            // mis-address.
            val fallbackRuntimeId = if (!renameOnly) preAckRuntimeId else null
            val liveRuntimeId = ackRuntimeId ?: fallbackRuntimeId
            if (liveRuntimeId == null) {
                HermesLog.w(
                    TAG,
                    "rename-resume ack carried no session_id/session_key for " +
                        "requested=$requestedSessionId (renameOnly=$renameOnly) — title not sent",
                )
            }
            val renameTarget = liveRuntimeId
            if (renameTarget != null && request.pendingRenameTitle != null) {
                sendSessionTitle(renameTarget, request.pendingRenameTitle)
            }
        }

        WsMethods.SESSION_CLOSE -> {
            // Close-then-delete recovery: the runtime is down, so the stored
            // delete can now succeed. Marked retried so a second refusal
            // surfaces instead of looping.
            request.deleteSessionId?.let { sendDeleteSession(it, retried = true) }
        }

        WsMethods.SESSION_INTERRUPT -> {
            uiStateMutable.update {
                it.copy(
                    transcript = it.transcript.copy(isAgentTyping = false),
                )
            }
            streamingStateMutable.update { StreamingState() }
            streamingController.resetStreaming()
            addSystemMessage("Session interrupted")
        }

        WsMethods.COMMANDS_CATALOG -> {
            val map = result as? Map<*, *> ?: return
            val catalog = parseCommandCatalog(map)
            if (catalog != null) {
                uiStateMutable.update {
                    it.copy(
                        composer = it.composer.copy(commandCatalog = catalog),
                    )
                }
            }
        }

        WsMethods.COMMAND_DISPATCH -> {
            handleDispatchResult(result)
        }

        WsMethods.APPROVAL_RESPOND -> {
            val map = result as? Map<*, *>
            val resolved = (map?.get("resolved") as? Number)?.toInt() ?: 0
            if (resolved > 0) {
                addSystemMessage("Approval submitted")
            }
        }
    }
}
