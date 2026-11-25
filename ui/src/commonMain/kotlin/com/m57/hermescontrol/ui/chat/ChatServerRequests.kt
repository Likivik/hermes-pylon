package com.m57.hermescontrol.ui.chat

// Gateway-initiated requests: approvals, sudo prompts, secrets, and their expiry.
//
// These arrived as one 152-line dispatcher plus five handlers inside ChatViewModel. They are the
// boundary where the *server* asks the *user* for something, which makes them the one place in the
// chat layer where a dropped event means a hung agent rather than a stale pixel.
//
// Split out with the router (Batch 1): a file split, not yet a decomposition. Batch 4 gives the router
// and this file a host interface instead of reaching into the ViewModel.

import com.m57.hermescontrol.data.ws.PrivilegedRequestBinding
import com.m57.hermescontrol.data.ws.ServerRequestBinding
import com.m57.hermescontrol.data.ws.WsEvent
import kotlinx.coroutines.flow.update

internal fun ChatViewModel.handleServerRequest(request: WsEvent.ServerRequest) {
    val sessionId = request.params["session_id"] as? String ?: return
    val binding =
        privilegedBinding(
            request.id,
            sessionId,
            request.sourceProfileId,
            request.connectionGeneration,
        ) ?: return
    when (request.method) {
        "sudo" -> {
            val serverBinding =
                ServerRequestBinding(request.id, sessionId, binding.profileId, binding.connectionGeneration)
            uiStateMutable.update { state ->
                if (state.sudoPrompt?.serverRequestBinding == serverBinding) {
                    state
                } else {
                    state.copy(
                        transcript = state.transcript.copy(isAgentTyping = false),
                        overlays =
                            state.overlays.copy(
                                sudoPrompt = SudoPromptUi(binding = binding, serverRequestBinding = serverBinding),
                            ),
                    )
                }
            }
        }
        "secret" -> {
            val serverBinding =
                ServerRequestBinding(request.id, sessionId, binding.profileId, binding.connectionGeneration)
            uiStateMutable.update { state ->
                if (state.secretPrompt?.serverRequestBinding == serverBinding) {
                    state
                } else {
                    state.copy(
                        transcript = state.transcript.copy(isAgentTyping = false),
                        overlays =
                            state.overlays.copy(
                                secretPrompt =
                                    SecretPromptUi(
                                        binding = binding,
                                        envVar = request.params["env_var"] as? String,
                                        prompt = request.params["prompt"] as? String,
                                        serverRequestBinding = serverBinding,
                                    ),
                            ),
                    )
                }
            }
        }
        "vault.code", "vault.unlock_prompt", "vault.save_login" -> {
            val serverBinding =
                ServerRequestBinding(request.id, sessionId, binding.profileId, binding.connectionGeneration)
            uiStateMutable.update { state ->
                val prompt = state.vaultPrompt
                if (prompt?.binding == serverBinding && prompt.method == request.method) {
                    state
                } else {
                    state.copy(
                        transcript = state.transcript.copy(isAgentTyping = false),
                        overlays =
                            state.overlays.copy(
                                vaultPrompt =
                                    VaultPromptUi(
                                        binding = serverBinding,
                                        method = request.method,
                                        title = request.params["title"] as? String,
                                        prompt = request.params["prompt"] as? String,
                                        identifier = request.params["identifier"] as? String,
                                        requestedOrigin =
                                            (request.params["origin"] as? String)
                                                ?: (request.params["site"] as? String),
                                    ),
                            ),
                    )
                }
            }
        }
        "approval" -> {
            val serverBinding =
                ServerRequestBinding(request.id, sessionId, binding.profileId, binding.connectionGeneration)
            val duplicate =
                uiStateMutable.value.messages.any {
                    it.approvalInfo?.serverRequestBinding == serverBinding
                }
            if (duplicate) return
            uiStateMutable.update { state ->
                state.copy(
                    transcript =
                        state.transcript.copy(
                            messages =
                                state.messages.filterNot {
                                    val prior = it.approvalInfo?.serverRequestBinding
                                    prior?.requestId == request.id && prior != serverBinding
                                },
                        ),
                )
            }
            handleApprovalRequest(
                WsEvent.ApprovalRequest(
                    command = request.params["command"] as? String,
                    description = request.params["description"] as? String,
                    patternKeys = (request.params["pattern_keys"] as? List<*>)?.filterIsInstance<String>(),
                    sessionId = sessionId,
                    requestId = request.params["request_id"] as? String ?: request.id,
                    timeoutSeconds = (request.params["timeout_seconds"] as? Number)?.toDouble() ?: 120.0,
                    sourceProfileId = binding.profileId,
                    connectionGeneration = binding.connectionGeneration,
                    serverRequestId = request.id,
                ),
            )
        }
        "clarify" -> {
            val locked =
                (request.params["answers"] as? Map<*, *>)?.entries
                    ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }?.toMap().orEmpty()
            val questions =
                (request.params["questions"] as? List<*>)?.mapIndexedNotNull { index, raw ->
                    val map = raw as? Map<*, *> ?: return@mapIndexedNotNull null
                    val text = map["question"] as? String ?: return@mapIndexedNotNull null
                    WsEvent.ClarifyQuestion(
                        qid = map["qid"] as? String ?: "q$index",
                        question = text,
                        choices = (map["choices"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                        multiSelect = map["multi_select"] as? Boolean ?: false,
                    )
                }.orEmpty()
            val first = questions.firstOrNull()
            val current = uiStateMutable.value.clarifyRequest
            val serverBinding =
                ServerRequestBinding(request.id, sessionId, binding.profileId, binding.connectionGeneration)
            if (current?.serverRequestBinding == serverBinding) {
                if (!request.replayed || current.questions.isNotEmpty()) return
            }
            handleWsEvent(
                WsEvent.ClarifyRequest(
                    text = first?.question ?: request.params["question"] as? String,
                    options = first?.choices ?: (request.params["choices"] as? List<*>)?.filterIsInstance<String>(),
                    clarifyId = request.params["request_id"] as? String ?: request.id,
                    sessionId = sessionId,
                    questionId = first?.qid,
                    multiSelect = first?.multiSelect ?: false,
                    questions = questions,
                    sourceProfileId = binding.profileId,
                    connectionGeneration = binding.connectionGeneration,
                    serverRequestId = request.id,
                    lockedAnswers = locked,
                ),
            )
        }
    }
}

internal fun ChatViewModel.handleServerRequestCancelled(event: WsEvent.ServerRequestCancelled) {
    if (event.sourceProfileId != selectedGatewayId() || event.connectionGeneration == null) return
    when (event.method) {
        "approval" ->
            uiStateMutable.update { state ->
                state.copy(
                    transcript =
                        state.transcript.copy(
                            messages =
                                state.messages.map { message ->
                                    val binding = message.approvalInfo?.serverRequestBinding
                                    if (binding?.requestId == event.id &&
                                        binding.runtimeSessionId == event.sessionId &&
                                        binding.profileId == event.sourceProfileId &&
                                        binding.connectionGeneration == event.connectionGeneration
                                    ) {
                                        message.copy(
                                            approvalInfo = null,
                                        )
                                    } else {
                                        message
                                    }
                                },
                        ),
                )
            }
        "clarify" ->
            uiStateMutable.update { state ->
                state.copy(
                    overlays =
                        OverlaysReducer.reduce(
                            state.overlays,
                            OverlaysIntent.ClarifyCancelled(
                                requestId = event.id,
                                sessionId = event.sessionId,
                                sourceProfileId = event.sourceProfileId,
                                connectionGeneration = event.connectionGeneration,
                            ),
                        ),
                )
            }
        "vault.code", "vault.unlock_prompt", "vault.save_login" ->
            uiStateMutable.update { state ->
                val prompt = state.vaultPrompt
                if (prompt?.method == event.method && serverCancellationMatches(prompt.binding, event)) {
                    state.copy(
                        overlays = state.overlays.copy(vaultPrompt = null),
                    )
                } else {
                    state
                }
            }
        "sudo" ->
            uiStateMutable.update { state ->
                if (serverCancellationMatches(state.sudoPrompt?.serverRequestBinding, event)) {
                    state.copy(
                        overlays = state.overlays.copy(sudoPrompt = null),
                    )
                } else {
                    state
                }
            }
        "secret" ->
            uiStateMutable.update { state ->
                if (serverCancellationMatches(state.secretPrompt?.serverRequestBinding, event)) {
                    state.copy(
                        overlays = state.overlays.copy(secretPrompt = null),
                    )
                } else {
                    state
                }
            }
    }
}

internal fun ChatViewModel.clearPrivilegedControls() {
    approvalExpiry.cancelAll()
    uiStateMutable.update { state ->
        state.copy(
            transcript =
                state.transcript.copy(
                    messages =
                        state.messages.map {
                                message ->
                            message.copy(approvalInfo = null)
                        },
                ),
            overlays = state.overlays.copy(sudoPrompt = null, secretPrompt = null, vaultPrompt = null),
        )
    }
}

internal fun ChatViewModel.handleApprovalRequest(event: WsEvent.ApprovalRequest) {
    val binding =
        privilegedBinding(
            requestId = event.requestId,
            eventSessionId = event.sessionId,
            profileId = event.sourceProfileId,
            generation = event.connectionGeneration,
        ) ?: return
    val description = event.description ?: event.command ?: "Unknown command"
    val content = "**Approval Required**\n$description"
    val msg =
        ChatMessage(
            role = MessageRole.SYSTEM,
            content = content,
            approvalInfo =
                ApprovalInfo(
                    command = event.command,
                    description = event.description,
                    patternKeys = event.patternKeys,
                    privilegedBinding = binding,
                    serverRequestBinding =
                        event.serverRequestId?.let {
                            ServerRequestBinding(
                                it,
                                binding.runtimeSessionId,
                                binding.profileId,
                                binding.connectionGeneration,
                            )
                        },
                ),
        )
    uiStateMutable.update { state ->
        state.copy(
            transcript = state.transcript.copy(messages = state.messages + msg, isAgentTyping = false),
        )
    }
    scheduleApprovalExpiry(msg.id, binding, event.timeoutSeconds)
}

internal fun ChatViewModel.handleSudoRequest(event: WsEvent.SudoRequest) {
    val binding =
        privilegedBinding(
            requestId = event.requestId,
            eventSessionId = event.sessionId,
            profileId = event.sourceProfileId,
            generation = event.connectionGeneration,
        ) ?: return
    uiStateMutable.update {
        it.copy(
            transcript = it.transcript.copy(isAgentTyping = false),
            overlays = it.overlays.copy(sudoPrompt = SudoPromptUi(binding)),
        )
    }
}

internal fun ChatViewModel.handleSecretRequest(event: WsEvent.SecretRequest) {
    val binding =
        privilegedBinding(
            requestId = event.requestId,
            eventSessionId = event.sessionId,
            profileId = event.sourceProfileId,
            generation = event.connectionGeneration,
        ) ?: return
    uiStateMutable.update {
        it.copy(
            transcript = it.transcript.copy(isAgentTyping = false),
            overlays = it.overlays.copy(secretPrompt = SecretPromptUi(binding, event.envVar, event.prompt)),
        )
    }
}

internal fun ChatViewModel.expiryMatches(
    binding: PrivilegedRequestBinding,
    requestId: String,
    sessionId: String?,
    profileId: String?,
    generation: Int?,
): Boolean =
    requestId == binding.requestId &&
        profileId == binding.profileId &&
        generation == binding.connectionGeneration &&
        !sessionId.isNullOrBlank() &&
        sessionId == binding.runtimeSessionId
