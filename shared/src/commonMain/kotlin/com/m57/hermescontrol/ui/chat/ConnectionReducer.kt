package com.m57.hermescontrol.ui.chat

/**
 * The connection slice's half of state changes: the status pill and the first-connect latch.
 *
 * Pure and total. Every branch delegates to a rule in [ConnectionStateUpdates], which answers "what changes and
 * what is deliberately kept"; this answers only "which rule runs". Intents that are not about the connection are
 * ignored here and answered by another slice.
 */
object ConnectionReducer {
    fun reduce(
        state: ConnectionState,
        intent: ChatIntent,
    ): ConnectionState =
        when (intent) {
            ChatIntent.FirstConnected -> state.afterFirstConnected()

            is ChatIntent.StatusPillChanged -> state.afterStatusPill(intent.text)

            ChatIntent.StatusPillCleared -> state.afterStatusPillCleared()

            // Not this slice's business.
            else -> state
        }
}
