package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.ModelProvider
import com.m57.hermescontrol.data.model.PinnedModel
import com.m57.hermescontrol.data.ws.PrivilegedRequestBinding
import com.m57.hermescontrol.data.ws.ServerRequestBinding
import com.m57.hermescontrol.ui.common.SecureGatewayMediaKind
import com.m57.hermescontrol.ui.common.SecureGatewayMediaRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Overlay updates change the field the action names and keep the rest.
 *
 * The split matters: a closed model picker must not lose the loaded providers, and a released prompt must
 * not lose its binding.
 */
class OverlaysStateUpdatesTest {
    private val provider = ModelProvider(slug = "openai", name = "OpenAI")
    private val pinned = listOf(PinnedModel(providerSlug = "openai", modelName = "gpt"))

    private val binding =
        PrivilegedRequestBinding(
            requestId = "req-1",
            runtimeSessionId = "sess-1",
            profileId = "profile-1",
            connectionGeneration = 3,
        )

    private val sudoPrompt = SudoPromptUi(binding = binding)

    private fun stateWithOverlays(): OverlaysState =
        OverlaysState(
            backgroundCompleteMessage = "done",
            openError = "boom",
            openingAttachmentPath = "/tmp/a.png",
            mediaPlayerRequest =
                SecureGatewayMediaRequest(
                    path = "/tmp/a.mp4",
                    title = "a.mp4",
                    mimeType = "video/mp4",
                    kind = SecureGatewayMediaKind.VIDEO,
                ),
            clarifyRequest = ClarifyUi(text = "which?", options = listOf("a", "b")),
            sudoPrompt = sudoPrompt,
            isSearchActive = true,
            searchQuery = "needle",
            searchMatchIndices = listOf(2, 5),
            currentSearchMatchIndex = 1,
            showModelPicker = true,
            modelPickerProviders = listOf(provider),
            modelPickerPinned = pinned,
            modelPickerLoading = true,
            modelInventoryResolved = true,
            modelSwitchConfirmation =
                ModelSwitchConfirmation(
                    message = "switch?",
                    spec = "gpt --provider openai",
                    displayLabel = "openai/gpt",
                    sessionId = "sess-1",
                ),
            showContextDetail = true,
        )

    @Test
    fun theContextDetailOpensAndClosesAlone() {
        val closed = stateWithOverlays().afterContextDetailClosed()
        assertFalse(closed.showContextDetail)
        assertTrue(closed.isSearchActive)
        assertTrue(closed.showModelPicker)

        val opened = closed.afterContextDetailOpened()
        assertTrue(opened.showContextDetail)
        assertEquals(listOf(provider), opened.modelPickerProviders)
    }

    @Test
    fun theModelPickerOpensWithASpinnerOnlyWhenNothingIsCached() {
        val cached =
            OverlaysState().afterModelPickerOpened(
                cachedProviders = listOf(provider),
                pinned = pinned,
                hasCached = true,
            )
        assertTrue(cached.showModelPicker)
        assertFalse(cached.modelPickerLoading)
        assertEquals(listOf(provider), cached.modelPickerProviders)
        assertEquals(pinned, cached.modelPickerPinned)

        val empty =
            OverlaysState().afterModelPickerOpened(
                cachedProviders = listOf(provider),
                pinned = pinned,
                hasCached = false,
            )
        assertTrue(empty.showModelPicker)
        assertTrue(empty.modelPickerLoading)
        assertTrue(empty.modelPickerProviders.isEmpty())
    }

    @Test
    fun closingTheModelPickerStopsTheSpinnerAndKeepsTheProviders() {
        val after = stateWithOverlays().afterModelPickerClosed()
        assertFalse(after.showModelPicker)
        assertFalse(after.modelPickerLoading)
        assertEquals(listOf(provider), after.modelPickerProviders)
        assertEquals(pinned, after.modelPickerPinned)
    }

    @Test
    fun aFailedModelRefreshStopsTheSpinnerButKeepsTheProviders() {
        val after = stateWithOverlays().afterModelRefreshFailed()
        assertFalse(after.modelPickerLoading)
        assertTrue(after.modelInventoryResolved)
        assertEquals(listOf(provider), after.modelPickerProviders)
        assertTrue(after.showModelPicker)
    }

    @Test
    fun aSuccessfulModelRefreshReplacesTheProvidersAndStopsTheSpinner() {
        val fresh = ModelProvider(slug = "anthropic", name = "Anthropic")
        val after = stateWithOverlays().afterModelRefreshSucceeded(listOf(fresh))
        assertEquals(listOf(fresh), after.modelPickerProviders)
        assertFalse(after.modelPickerLoading)
        assertTrue(after.modelInventoryResolved)
        assertEquals(pinned, after.modelPickerPinned)

        val started = after.afterModelRefreshStarted()
        assertTrue(started.modelPickerLoading)
        assertEquals(listOf(fresh), started.modelPickerProviders)

        val loaded = started.afterModelInventoryLoaded(listOf(fresh), pinned)
        assertTrue(loaded.modelInventoryResolved)
        assertEquals(pinned, loaded.modelPickerPinned)
        assertEquals(listOf(fresh), loaded.modelPickerProviders)

        val failedInventory = loaded.afterModelInventoryResolved()
        assertTrue(failedInventory.modelInventoryResolved)
        assertEquals(listOf(fresh), failedInventory.modelPickerProviders)

        val repinned = loaded.afterPinnedModelsChanged(emptyList())
        assertTrue(repinned.modelPickerPinned.isEmpty())
        assertEquals(listOf(fresh), repinned.modelPickerProviders)
    }

    @Test
    fun theModelSwitchConfirmationIsShownAndDismissed() {
        val confirmation =
            ModelSwitchConfirmation(
                message = "switch?",
                spec = "gpt --provider openai",
                displayLabel = "openai/gpt",
                sessionId = "sess-1",
            )
        // The confirmation is a layer on top of an open picker, so open the picker first: without it the
        // "preserved" assertion below would pass or fail on a value nobody set.
        val shown =
            OverlaysState()
                .afterModelPickerOpened(
                    cachedProviders = emptyList(),
                    pinned = emptyList(),
                    hasCached = true,
                ).afterModelSwitchRequested(confirmation)
        assertEquals(confirmation, shown.modelSwitchConfirmation)

        val dismissed = shown.afterModelSwitchConfirmationDismissed()
        assertNull(dismissed.modelSwitchConfirmation)
        assertTrue(dismissed.showModelPicker)
    }

    @Test
    fun theSearchOpensAndClosesAsOneCoherentChange() {
        val opened = stateWithOverlays().afterSearchOpened()
        assertTrue(opened.isSearchActive)
        assertEquals("", opened.searchQuery)

        val closed = opened.afterSearchClosed()
        assertFalse(closed.isSearchActive)
        assertEquals("", closed.searchQuery)
        assertTrue(closed.searchMatchIndices.isEmpty())
        assertEquals(-1, closed.currentSearchMatchIndex)
        assertTrue(closed.showModelPicker)
    }

    @Test
    fun aBlankSearchQueryDropsTheStaleMatchesAndANonBlankOneKeepsThem() {
        val blank = stateWithOverlays().afterSearchQueryChanged("")
        assertEquals("", blank.searchQuery)
        assertTrue(blank.searchMatchIndices.isEmpty())
        assertEquals(-1, blank.currentSearchMatchIndex)
        assertTrue(blank.isSearchActive)

        val typed = stateWithOverlays().afterSearchQueryChanged("other")
        assertEquals("other", typed.searchQuery)
        assertEquals(listOf(2, 5), typed.searchMatchIndices)
        assertEquals(1, typed.currentSearchMatchIndex)
    }

    @Test
    fun theFirstMatchBecomesTheCurrentOneAndCanBeMoved() {
        val after = stateWithOverlays().afterSearchMatchesFound(listOf(7, 9))
        assertEquals(listOf(7, 9), after.searchMatchIndices)
        assertEquals(0, after.currentSearchMatchIndex)

        val none = stateWithOverlays().afterSearchMatchesFound(emptyList())
        assertEquals(-1, none.currentSearchMatchIndex)

        val moved = after.afterSearchMatchMoved(1)
        assertEquals(1, moved.currentSearchMatchIndex)
        assertEquals(listOf(7, 9), moved.searchMatchIndices)
    }

    @Test
    fun theErrorTheMediaPlayerAndTheBackgroundNoticeAreClearedAlone() {
        val shown = OverlaysState().afterOpenErrorShown("cannot open")
        assertEquals("cannot open", shown.openError)

        val cleared = stateWithOverlays().afterOpenErrorCleared()
        assertNull(cleared.openError)
        assertEquals("/tmp/a.png", cleared.openingAttachmentPath)

        val player = stateWithOverlays().afterMediaPlayerClosed()
        assertNull(player.mediaPlayerRequest)
        assertEquals("boom", player.openError)

        val background = stateWithOverlays().afterBackgroundCompleteCleared()
        assertNull(background.backgroundCompleteMessage)
        assertTrue(background.showModelPicker)

        val request =
            SecureGatewayMediaRequest(
                path = "/tmp/b.mp4",
                title = "b.mp4",
                mimeType = "video/mp4",
                kind = SecureGatewayMediaKind.VIDEO,
            )
        val opening = OverlaysState().afterAttachmentOpening("/tmp/b.mp4")
        assertEquals("/tmp/b.mp4", opening.openingAttachmentPath)
        val playing = opening.afterMediaPlayerRequested(request)
        assertEquals(request, playing.mediaPlayerRequest)
        assertEquals("/tmp/b.mp4", playing.openingAttachmentPath)
    }

    @Test
    fun aPromptIsClaimedAndReleasedWithoutLosingItsBinding() {
        val claimed = stateWithOverlays().afterSudoPromptClaimed(sudoPrompt)
        assertTrue(claimed.sudoPrompt!!.isSubmitting)
        assertEquals(binding, claimed.sudoPrompt!!.binding)

        val released = claimed.afterSudoPromptReleased(claimed.sudoPrompt!!)
        assertFalse(released.sudoPrompt!!.isSubmitting)
        assertEquals(binding, released.sudoPrompt!!.binding)

        val answered = claimed.afterSudoPromptAnswered(claimed.sudoPrompt!!)
        assertNull(answered.sudoPrompt)
        assertEquals("boom", answered.openError)
    }

    @Test
    fun aStaleClaimDoesNotTouchALivePromptAndTheOtherPromptsFollowTheRule() {
        val claimed = stateWithOverlays().afterSudoPromptClaimed(sudoPrompt)
        val other = SudoPromptUi(binding = binding.copy(requestId = "req-2"))

        assertEquals(claimed, claimed.afterSudoPromptClaimed(other))
        assertEquals(claimed, claimed.afterSudoPromptAnswered(other))
        assertEquals(claimed, claimed.afterSudoPromptReleased(other))

        val vault =
            VaultPromptUi(
                binding =
                    ServerRequestBinding(
                        requestId = "req-3",
                        runtimeSessionId = "sess-1",
                        profileId = "profile-1",
                        connectionGeneration = 3,
                    ),
                method = "vault.save_login",
            )
        val vaultClaimed = OverlaysState(vaultPrompt = vault).afterVaultPromptClaimed(vault)
        assertTrue(vaultClaimed.vaultPrompt!!.isSubmitting)
        assertNull(vaultClaimed.afterVaultPromptAnswered(vaultClaimed.vaultPrompt!!).vaultPrompt)

        val secret = SecretPromptUi(binding = binding, envVar = "TOKEN")
        val secretClaimed = OverlaysState(secretPrompt = secret).afterSecretPromptClaimed(secret)
        assertTrue(secretClaimed.secretPrompt!!.isSubmitting)
        val released = secretClaimed.afterSecretPromptReleased(secretClaimed.secretPrompt!!)
        assertFalse(released.secretPrompt!!.isSubmitting)
        assertEquals("TOKEN", released.secretPrompt!!.envVar)
    }
}
