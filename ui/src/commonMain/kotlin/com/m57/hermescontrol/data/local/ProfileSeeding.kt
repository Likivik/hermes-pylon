package com.m57.hermescontrol.data.local

import com.m57.hermescontrol.data.config.ConnectionProfile

/** What the profile list should become, or nothing to change. */
internal data class ProfileSeeding(
    val profiles: List<ConnectionProfile>,
    val selectedId: String,
)

/**
 * Decide what Settings → Connections must show after signing in to [origin].
 *
 * Pure on purpose: the web entry point has to call it with live store values, and
 * this is the only part of browser-session adoption that is worth a test — the
 * rest is side effects on singletons.
 *
 * Three outcomes, in order:
 * - a profile already points at this origin: select it, or return null when it is
 *   already selected;
 * - the store holds only the untouched build-default placeholder (a fresh install):
 *   adopt that row by renaming it, instead of leaving a dead "Default" beside a
 *   new one;
 * - anything else: append a new profile for the origin.
 */
internal fun profileSeedingFor(
    profiles: List<ConnectionProfile>,
    selectedId: String?,
    origin: String,
    displayName: String,
    defaultProfileId: String,
    defaultBaseUrl: String,
    wsAuthParam: String = "ticket",
): ProfileSeeding? {
    val normalisedOrigin = origin.trimEnd('/')
    val match = profiles.firstOrNull { it.resolvedBaseUrl.trimEnd('/') == normalisedOrigin }
    if (match != null) {
        return if (selectedId == match.id) null else ProfileSeeding(profiles, match.id)
    }

    val pristine =
        profiles.singleOrNull()?.takeIf {
            it.id == defaultProfileId &&
                it.resolvedBaseUrl.trimEnd('/') == defaultBaseUrl.trimEnd('/')
        }
    if (pristine != null) {
        val adopted =
            pristine.copy(
                name = displayName,
                baseUrl = origin,
                wsAuthParam = wsAuthParam,
            )
        return ProfileSeeding(listOf(adopted), adopted.id)
    }

    val created =
        ConnectionProfile(
            name = displayName,
            baseUrl = origin,
            wsAuthParam = wsAuthParam,
        )
    return ProfileSeeding(profiles + created, created.id)
}
