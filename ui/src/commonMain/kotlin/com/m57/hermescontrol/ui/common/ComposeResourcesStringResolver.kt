package com.m57.hermescontrol.ui.common

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Compose-Resources backed [StringResolver] for production.
 *
 * A one-liner wrapper around the suspend
 * `org.jetbrains.compose.resources.getString` — the same call the
 * composables make via `stringResource(Res.string.x)`, just hoisted
 * out of composition so a ViewModel can use it.
 *
 * Lives in `androidMain` so future platform-specific work (e.g.
 * locale-aware caching) can branch on the target without affecting
 * `commonMain`'s API surface. The jvm target of `:ui` (used only by
 * tests) has no `Context`-facing ViewModels and therefore does not
 * need a counterpart implementation.
 *
 * ViewModels that want to be testable should accept a [StringResolver]
 * via their constructor and pass [Default] in production.
 */
class ComposeResourcesStringResolver : StringResolver {
    override suspend fun get(
        key: StringResource,
        vararg args: Any,
    ): String {
        if (args.isEmpty()) {
            return getString(key)
        }
        // getString(StringResource, vararg Any) does the same positional
        // `%1$s` / `%2$d` substitution the old `Context.getString(Int, vararg)`
        // did, so existing Android resource definitions carry over
        // without translation.
        return getString(key, *args)
    }

    companion object Default {
        /**
         * Shared resolver for callers that don't manage their own
         * lifecycle. Production code should prefer a per-instance
         * resolver so tests can substitute a fake.
         */
        val shared: StringResolver = ComposeResourcesStringResolver()
    }
}
