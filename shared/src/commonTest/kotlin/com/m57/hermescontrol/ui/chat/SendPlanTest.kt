package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The send pipeline's admission and delivery rules, tested without a ViewModel.
 *
 * The same six decisions are also pinned end-to-end in `ChatSendPipelineCharacterizationTest`; these
 * tests are the cheap half — they fail in isolation and name the rule that broke.
 */
class SendPlanTest {
    private fun plan(
        text: String = "hello",
        hasAttachments: Boolean = false,
        sessionReady: Boolean = true,
        hasStorageSessionId: Boolean = true,
        hasRuntimeSessionId: Boolean = true,
        hasConnection: Boolean = true,
    ) = planSend(
        text = text,
        hasAttachments = hasAttachments,
        sessionReady = sessionReady,
        hasStorageSessionId = hasStorageSessionId,
        hasRuntimeSessionId = hasRuntimeSessionId,
        hasConnection = hasConnection,
    )

    @Test
    fun aReadySessionWithTextIsSent() {
        assertEquals(SendStart.Prepare, plan())
    }

    @Test
    fun aSessionThatIsNotInstalledRefusesTheSend() {
        assertEquals(SendStart.Refused, plan(sessionReady = false))
    }

    @Test
    fun blankTextWithNoAttachmentsIsRefused() {
        assertEquals(SendStart.Refused, plan(text = "   "))
        assertEquals(SendStart.Prepare, plan(text = "   ", hasAttachments = true))
    }

    @Test
    fun anUnroutableSessionRefusesTheSend() {
        // Each of these alone makes the prompt unsendable; together they are one guard.
        assertEquals(SendStart.Refused, plan(hasStorageSessionId = false))
        assertEquals(SendStart.Refused, plan(hasRuntimeSessionId = false))
        assertEquals(SendStart.Refused, plan(hasConnection = false))
    }

    @Test
    fun aSlashCommandIsDispatchedNotSubmitted() {
        assertEquals(SendStart.DispatchCommand("/status"), plan(text = "/status"))
        assertEquals(SendStart.DispatchCommand("/model gpt-5"), plan(text = "/model gpt-5"))
    }

    @Test
    fun aBareModelCommandOpensThePicker() {
        assertEquals(SendStart.OpenModelPicker, plan(text = "/model"))
    }

    @Test
    fun bareModelDetectionToleratesWhitespaceAndCase() {
        assertTrue(isBareModelCommand("/model"))
        assertTrue(isBareModelCommand("  /model  "))
        assertTrue(isBareModelCommand("/MODEL"))
        assertTrue(isBareModelCommand("/model   "))
    }

    @Test
    fun bareModelDetectionRejectsArgumentsAndOtherCommands() {
        assertFalse(isBareModelCommand("/model gpt-5"))
        assertFalse(isBareModelCommand("/models"))
        assertFalse(isBareModelCommand("model"))
        assertFalse(isBareModelCommand("/status"))
    }

    @Test
    fun aRunningTurnSteersAnAttachmentFreeSend() {
        assertEquals(Delivery.Redirect, chooseDelivery(wasStreaming = true, hasAttachments = false))
    }

    @Test
    fun aRunningTurnStillSubmitsWhenTheSendCarriesAttachments() {
        // session.redirect is text-only; redirecting would drop the file silently.
        assertEquals(Delivery.Submit, chooseDelivery(wasStreaming = true, hasAttachments = true))
    }

    @Test
    fun anIdleSessionSubmits() {
        assertEquals(Delivery.Submit, chooseDelivery(wasStreaming = false, hasAttachments = false))
    }
}
