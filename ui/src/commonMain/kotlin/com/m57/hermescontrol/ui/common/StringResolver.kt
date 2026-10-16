package com.m57.hermescontrol.ui.common

import org.jetbrains.compose.resources.StringResource

/**
 * Resolves a Compose [StringResource] key to its locale-resolved text.
 *
 * This is the multiplatform-friendly seam that lets ViewModels drop their
 * `Application.getString(Int)` calls: the production implementation reads
 * from `org.jetbrains.compose.resources` (no `Context` required), the JVM
 * test double returns whatever the test pre-registered for each key.
 *
 * The API is intentionally `suspend`: Compose Resources' own `getString`
 * is `suspend` to allow platform-specific resource readers (Android
 * `Resources`, JVM `ResourceReader`) to perform their lookups without
 * blocking the caller's thread. ViewModels that need to populate UI state
 * synchronously from a non-suspending lambda (e.g. an early return inside
 * `connect()`) hoist the call above the lambda or wrap it in a small
 * `viewModelScope.launch { ... }` so the result is captured by the time
 * `_uiState.update { ... }` runs.
 */
interface StringResolver {
    /**
     * Resolve [key] to the user's locale text. Optional [args] are
     * substituted into positional format placeholders (`%1$s`, `%2$d`,
     * …) the same way `Context.getString(Int, vararg Any)` did.
     */
    suspend fun get(
        key: StringResource,
        vararg args: Any,
    ): String
}
