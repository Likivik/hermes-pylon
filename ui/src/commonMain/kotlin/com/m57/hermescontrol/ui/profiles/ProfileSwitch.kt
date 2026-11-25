package com.m57.hermescontrol.ui.profiles

/**
 * What a profile selection actually achieved.
 *
 * The gateway's `active` is a preference and `current` is the profile its process serves;
 * they do not follow each other (measured against 0.23.1), so "switched" is only true when
 * the two agree. Every other case must stay visible as pending rather than be reported as
 * success.
 */
sealed interface ProfileSwitch {
    data class Activated(val profile: String) : ProfileSwitch

    data class Pending(val selected: String, val serving: String?) : ProfileSwitch
}

fun profileSwitchOutcome(
    selected: String,
    activated: String?,
): ProfileSwitch =
    if (activated == selected) ProfileSwitch.Activated(selected) else ProfileSwitch.Pending(selected, activated)
