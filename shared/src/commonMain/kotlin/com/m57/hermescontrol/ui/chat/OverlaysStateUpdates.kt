package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.ModelProvider
import com.m57.hermescontrol.data.model.PinnedModel
import com.m57.hermescontrol.ui.common.SecureGatewayMediaRequest

/*
 * Overlay transitions as pure functions over [OverlaysState].
 *
 * Every overlay write used to be an inline `uiStateMutable.update { it.overlays.copy(...) }` block. Each
 * block was one line long and each carried a rule the reader had to infer: a spinner must stop when the
 * picker closes, a stale match list must not survive a blank query, a claimed prompt must only be
 * released if it is still the claimed one. Those rules are named here and tested.
 *
 * **Rule:** each function returns the next state and mutates nothing. Every other field is preserved.
 *
 * **Preserved on purpose:** the fields that do not belong to the action. Closing the model picker keeps
 * the loaded providers and the pinned models. Clearing the open error keeps the media player request. A
 * prompt that is released keeps its identity and binding. Only the fields the action names change.
 *
 * **Guarded writes:** prompt writes check identity. A privileged answer arrives asynchronously, so a
 * release for a prompt the user has already moved past must not touch the newer one.
 */

/**
 * Open the context breakdown sheet.
 *
 * **Changes:** `showContextDetail` becomes true.
 *
 * **Preserved:** every other overlay, including the model picker and the search state.
 */
fun OverlaysState.afterContextDetailOpened(): OverlaysState = copy(showContextDetail = true)

/**
 * Close the context breakdown sheet.
 *
 * **Changes:** `showContextDetail` becomes false.
 *
 * **Preserved:** every other overlay.
 */
fun OverlaysState.afterContextDetailClosed(): OverlaysState = copy(showContextDetail = false)

/**
 * Open the in-session model picker.
 *
 * **Changes:** the picker becomes visible, the pinned models are shown, and the providers are either the
 * cached ones or none. The picker shows a spinner only when there is no cache to draw from.
 *
 * **Preserved:** the model switch confirmation and the rest of the overlays.
 */
fun OverlaysState.afterModelPickerOpened(
    cachedProviders: List<ModelProvider>,
    pinned: List<PinnedModel>,
    hasCached: Boolean,
): OverlaysState =
    copy(
        showModelPicker = true,
        modelPickerProviders = if (hasCached) cachedProviders else emptyList(),
        modelPickerPinned = pinned,
        modelPickerLoading = !hasCached,
    )

/**
 * Close the in-session model picker.
 *
 * **Changes:** the picker is hidden and its spinner stops.
 *
 * **Preserved:** the loaded providers, the pinned models, and the model switch confirmation. A closed
 * picker can be reopened without a refetch.
 */
fun OverlaysState.afterModelPickerClosed(): OverlaysState =
    copy(
        showModelPicker = false,
        modelPickerLoading = false,
    )

/**
 * Record a successful model inventory preload.
 *
 * **Changes:** the providers and the pinned models are stored, and the inventory counts as resolved.
 *
 * **Preserved:** the picker's visibility and its spinner. A preload happens in the background.
 */
fun OverlaysState.afterModelInventoryLoaded(
    providers: List<ModelProvider>,
    pinned: List<PinnedModel>,
): OverlaysState =
    copy(
        modelPickerProviders = providers,
        modelPickerPinned = pinned,
        modelInventoryResolved = true,
    )

/**
 * Record a failed model inventory preload.
 *
 * **Changes:** the inventory counts as resolved. There is nothing to show, so the attempt is over.
 *
 * **Preserved:** the providers and the pinned models already held.
 */
fun OverlaysState.afterModelInventoryResolved(): OverlaysState = copy(modelInventoryResolved = true)

/**
 * Start a model options refresh.
 *
 * **Changes:** the picker shows its spinner.
 *
 * **Preserved:** the providers already loaded and the picker's visibility.
 */
fun OverlaysState.afterModelRefreshStarted(): OverlaysState = copy(modelPickerLoading = true)

/**
 * Record a successful model options refresh.
 *
 * **Changes:** the providers are replaced with the fresh ones, the spinner stops, and the inventory
 * counts as resolved.
 *
 * **Preserved:** the pinned models and the picker's visibility.
 */
fun OverlaysState.afterModelRefreshSucceeded(providers: List<ModelProvider>): OverlaysState =
    copy(
        modelPickerProviders = providers,
        modelPickerLoading = false,
        modelInventoryResolved = true,
    )

/**
 * Record a failed model options refresh.
 *
 * **Changes:** the spinner stops and the inventory counts as resolved. The failure itself is reported on
 * the transcript, not here.
 *
 * **Preserved:** the providers already loaded. A failed refresh must not empty the picker.
 */
fun OverlaysState.afterModelRefreshFailed(): OverlaysState =
    copy(
        modelPickerLoading = false,
        modelInventoryResolved = true,
    )

/**
 * Store a new list of pinned models.
 *
 * **Changes:** the pinned models are replaced.
 *
 * **Preserved:** the providers, the picker's visibility, and its spinner.
 */
fun OverlaysState.afterPinnedModelsChanged(pinned: List<PinnedModel>): OverlaysState = copy(modelPickerPinned = pinned)

/**
 * Ask the user to confirm a model switch.
 *
 * **Changes:** the confirmation is shown.
 *
 * **Preserved:** the picker and the search state. The confirmation is a separate layer on top.
 */
fun OverlaysState.afterModelSwitchRequested(confirmation: ModelSwitchConfirmation): OverlaysState =
    copy(modelSwitchConfirmation = confirmation)

/**
 * Dismiss the model switch confirmation.
 *
 * **Changes:** the confirmation is cleared. It is cleared when the user confirms, when the user cancels,
 * and when the switch itself finishes.
 *
 * **Preserved:** every other overlay, including the picker.
 */
fun OverlaysState.afterModelSwitchConfirmationDismissed(): OverlaysState = copy(modelSwitchConfirmation = null)

/**
 * Open the in-message search.
 *
 * **Changes:** the search becomes active and the query starts empty.
 *
 * **Preserved:** the match list and the current match index. A fresh search starts with no matches.
 */
fun OverlaysState.afterSearchOpened(): OverlaysState =
    copy(
        isSearchActive = true,
        searchQuery = "",
    )

/**
 * Change the search query.
 *
 * **Changes:** the query is replaced. A blank query also drops the matches and the current match index,
 * because matches found for the previous query no longer mean anything.
 *
 * **Preserved:** the search stays active and every other overlay.
 */
fun OverlaysState.afterSearchQueryChanged(query: String): OverlaysState =
    if (query.isBlank()) {
        copy(
            searchQuery = query,
            searchMatchIndices = emptyList(),
            currentSearchMatchIndex = -1,
        )
    } else {
        copy(searchQuery = query)
    }

/**
 * Record the matches found for the current query.
 *
 * **Changes:** the match list is replaced. The first match becomes the current one. No match means no
 * current match.
 *
 * **Preserved:** the query and the search's active flag.
 */
fun OverlaysState.afterSearchMatchesFound(indices: List<Int>): OverlaysState =
    copy(
        searchMatchIndices = indices,
        currentSearchMatchIndex = if (indices.isNotEmpty()) 0 else -1,
    )

/**
 * Move the current search match.
 *
 * **Changes:** the current match index is replaced.
 *
 * **Preserved:** the match list, the query, and every other overlay.
 */
fun OverlaysState.afterSearchMatchMoved(index: Int): OverlaysState = copy(currentSearchMatchIndex = index)

/**
 * Close the in-message search.
 *
 * **Changes:** the search becomes inactive, the query is cleared, the matches are dropped, and there is
 * no current match.
 *
 * **Preserved:** every other overlay.
 */
fun OverlaysState.afterSearchClosed(): OverlaysState =
    copy(
        isSearchActive = false,
        searchQuery = "",
        searchMatchIndices = emptyList(),
        currentSearchMatchIndex = -1,
    )

/**
 * Note that an attachment is being opened.
 *
 * **Changes:** the opening path is recorded.
 *
 * **Preserved:** every other overlay, including the media player request.
 */
fun OverlaysState.afterAttachmentOpening(path: String?): OverlaysState = copy(openingAttachmentPath = path)

/**
 * Open the media player for an attachment.
 *
 * **Changes:** the media request is stored.
 *
 * **Preserved:** every other overlay, including the opening path.
 */
fun OverlaysState.afterMediaPlayerRequested(request: SecureGatewayMediaRequest): OverlaysState =
    copy(mediaPlayerRequest = request)

/**
 * Close the media player.
 *
 * **Changes:** the media request is cleared.
 *
 * **Preserved:** every other overlay, including the opening path.
 */
fun OverlaysState.afterMediaPlayerClosed(): OverlaysState = copy(mediaPlayerRequest = null)

/**
 * Show an error that happened while opening an attachment.
 *
 * **Changes:** the error message is stored.
 *
 * **Preserved:** every other overlay, including the media player request.
 */
fun OverlaysState.afterOpenErrorShown(message: String): OverlaysState = copy(openError = message)

/**
 * Dismiss the open error.
 *
 * **Changes:** the error message is cleared.
 *
 * **Preserved:** every other overlay, including the media player request.
 */
fun OverlaysState.afterOpenErrorCleared(): OverlaysState = copy(openError = null)

/**
 * Dismiss the notice that a background task finished.
 *
 * **Changes:** the background completion message is cleared.
 *
 * **Preserved:** every other overlay.
 */
fun OverlaysState.afterBackgroundCompleteCleared(): OverlaysState = copy(backgroundCompleteMessage = null)

/**
 * Claim the vault prompt for submission.
 *
 * **Changes:** the prompt is marked as submitting.
 *
 * **Guard:** the claim applies only to the prompt that is still shown and has not been claimed yet.
 *
 * **Preserved:** every other overlay.
 */
fun OverlaysState.afterVaultPromptClaimed(expected: VaultPromptUi): OverlaysState =
    if (vaultPrompt == expected && !expected.isSubmitting) {
        copy(vaultPrompt = expected.copy(isSubmitting = true))
    } else {
        this
    }

/**
 * Close the vault prompt because the answer was accepted.
 *
 * **Changes:** the prompt is cleared.
 *
 * **Guard:** the clear applies only if the claimed prompt is still the one shown.
 *
 * **Preserved:** every other overlay.
 */
fun OverlaysState.afterVaultPromptAnswered(claimed: VaultPromptUi): OverlaysState =
    if (vaultPrompt == claimed) copy(vaultPrompt = null) else this

/**
 * Release the vault prompt because the answer was rejected.
 *
 * **Changes:** the prompt stops being marked as submitting, so the user can try again.
 *
 * **Guard:** the release applies only if the claimed prompt is still the one shown.
 *
 * **Preserved:** the prompt's identity and binding, and every other overlay.
 */
fun OverlaysState.afterVaultPromptReleased(claimed: VaultPromptUi): OverlaysState =
    if (vaultPrompt == claimed) {
        copy(vaultPrompt = claimed.copy(isSubmitting = false))
    } else {
        this
    }

/**
 * Claim the sudo prompt for submission.
 *
 * **Changes:** the prompt is marked as submitting.
 *
 * **Guard:** the claim applies only to the prompt that is still shown and has not been claimed yet.
 *
 * **Preserved:** every other overlay.
 */
fun OverlaysState.afterSudoPromptClaimed(expected: SudoPromptUi): OverlaysState =
    if (sudoPrompt == expected && !expected.isSubmitting) {
        copy(sudoPrompt = expected.copy(isSubmitting = true))
    } else {
        this
    }

/**
 * Close the sudo prompt because the answer was accepted.
 *
 * **Changes:** the prompt is cleared.
 *
 * **Guard:** the clear applies only if the claimed prompt is still the one shown.
 *
 * **Preserved:** every other overlay.
 */
fun OverlaysState.afterSudoPromptAnswered(claimed: SudoPromptUi): OverlaysState =
    if (sudoPrompt == claimed) copy(sudoPrompt = null) else this

/**
 * Release the sudo prompt because the answer was rejected.
 *
 * **Changes:** the prompt stops being marked as submitting, so the user can try again.
 *
 * **Guard:** the release applies only if the claimed prompt is still the one shown.
 *
 * **Preserved:** the prompt's identity and binding, and every other overlay.
 */
fun OverlaysState.afterSudoPromptReleased(claimed: SudoPromptUi): OverlaysState =
    if (sudoPrompt == claimed) {
        copy(sudoPrompt = claimed.copy(isSubmitting = false))
    } else {
        this
    }

/**
 * Claim the secret prompt for submission.
 *
 * **Changes:** the prompt is marked as submitting.
 *
 * **Guard:** the claim applies only to the prompt that is still shown and has not been claimed yet.
 *
 * **Preserved:** every other overlay.
 */
fun OverlaysState.afterSecretPromptClaimed(expected: SecretPromptUi): OverlaysState =
    if (secretPrompt == expected && !expected.isSubmitting) {
        copy(secretPrompt = expected.copy(isSubmitting = true))
    } else {
        this
    }

/**
 * Close the secret prompt because the answer was accepted.
 *
 * **Changes:** the prompt is cleared.
 *
 * **Guard:** the clear applies only if the claimed prompt is still the one shown.
 *
 * **Preserved:** every other overlay.
 */
fun OverlaysState.afterSecretPromptAnswered(claimed: SecretPromptUi): OverlaysState =
    if (secretPrompt == claimed) copy(secretPrompt = null) else this

/**
 * Release the secret prompt because the answer was rejected.
 *
 * **Changes:** the prompt stops being marked as submitting, so the user can try again.
 *
 * **Guard:** the release applies only if the claimed prompt is still the one shown.
 *
 * **Preserved:** the prompt's identity and binding, and every other overlay.
 */
fun OverlaysState.afterSecretPromptReleased(claimed: SecretPromptUi): OverlaysState =
    if (secretPrompt == claimed) {
        copy(secretPrompt = claimed.copy(isSubmitting = false))
    } else {
        this
    }
