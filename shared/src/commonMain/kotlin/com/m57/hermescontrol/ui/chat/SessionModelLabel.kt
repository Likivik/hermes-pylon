package com.m57.hermescontrol.ui.chat

/**
 * Provider-qualified model identity: `openai/gpt-5`, not `gpt-5`.
 *
 * The rule was written twice, byte for byte, in two places in `ChatViewModel` — once when a message
 * completes, once when a session is branched. Two copies of a formatting rule drift silently: the
 * composer chip shows one label and the model picker shows another, and nothing fails.
 *
 * Kept pure and separate so the rule can be tested without a ViewModel, a socket, or a session.
 */
fun qualifyModelLabel(
    provider: String,
    model: String,
): String = if (provider.isEmpty() || model.startsWith("$provider/")) model else "$provider/$model"
