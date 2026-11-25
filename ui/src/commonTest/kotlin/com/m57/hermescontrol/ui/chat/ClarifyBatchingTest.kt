package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ClarifyBatchingTest {
    private fun question(id: String) = ClarifyQuestionUi(qid = id, question = "Q $id", choices = listOf("a", "b"))

    @Test
    fun aBatchRequestUsesEachQuestionsOwnId() {
        val clarify =
            ClarifyUi(
                text = "two questions",
                options = emptyList(),
                clarifyId = "c-1",
                questionId = "legacy-id",
                questions = listOf(question("q1"), question("q2")),
            )

        assertEquals("q1", clarify.wireQuestionId(question("q1")))
        assertEquals("q2", clarify.wireQuestionId(question("q2")))
    }

    @Test
    fun aSingleQuestionRequestUsesTheRequestId() {
        val clarify =
            ClarifyUi(
                text = "one question",
                options = listOf("a", "b"),
                clarifyId = "c-1",
                questionId = "wire-id",
            )

        assertEquals("wire-id", clarify.wireQuestionId(ClarifyQuestionUi(qid = "q0", question = "")))
    }

    @Test
    fun resolvingEveryQuestionClosesTheDialog() {
        val clarify =
            ClarifyUi(
                text = "two questions",
                options = emptyList(),
                clarifyId = "c-1",
                questions = listOf(question("q1"), question("q2")),
            )

        assertNull(clarify.withRemainingQuestions(emptyList()))
    }

    @Test
    fun resolvingSomeQuestionsKeepsTheRequestWithTheRest() {
        val clarify =
            ClarifyUi(
                text = "two questions",
                options = emptyList(),
                clarifyId = "c-1",
                questions = listOf(question("q1"), question("q2")),
            )

        val remaining = clarify.withRemainingQuestions(listOf(question("q2")))

        assertEquals(listOf("q2"), remaining?.questions?.map { it.qid })
        assertEquals("c-1", remaining?.clarifyId)
    }

    @Test
    fun aSingleQuestionRequestIsReturnedUnchangedWhileItStillHasItsQuestion() {
        val clarify = ClarifyUi(text = "one", options = listOf("a"), clarifyId = "c-1", questionId = "wire-id")

        assertSame(clarify, clarify.withRemainingQuestions(listOf(question("q0"))))
    }
}
