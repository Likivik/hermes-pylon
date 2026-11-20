package com.m57.hermescontrol.ui.chat

// Pure operations on a clarify request's questions.
//
// These were private extensions inside `ChatViewModel`, which made the one piece of clarify logic
// that is not about transport or state impossible to test on its own. They are the two places a
// batch clarify can silently go wrong: sending the wrong question id to the gateway, and leaving a
// dialog on screen for questions that were already answered.

/**
 * The id the gateway knows a question by.
 *
 * A single-question request carries its id on the request (`questionId`); a batch carries one per
 * question (`qid`). Getting this backwards sends an answer that resolves nothing.
 */
internal fun ClarifyUi.wireQuestionId(question: ClarifyQuestionUi): String? =
    if (questions.isNotEmpty()) question.qid else questionId

/**
 * The request left after [remaining] questions, or null when nothing is left to ask.
 *
 * Null — not an empty clarify — is the signal that the dialog must close: an empty clarify would
 * still render a frame with nothing to answer.
 */
internal fun ClarifyUi.withRemainingQuestions(remaining: List<ClarifyQuestionUi>): ClarifyUi? =
    when {
        remaining.isEmpty() -> null
        questions.isEmpty() -> this
        else -> copy(questions = remaining)
    }
