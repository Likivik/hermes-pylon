package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import com.m57.hermescontrol.MainActivity

/**
 * Compose test rule for [MainActivity] that seeds
 * [com.m57.hermescontrol.data.local.AuthManager] BEFORE the activity launches.
 *
 * Wraps [E2eScenarioRule] with the v1 (deprecated) [AndroidComposeTestRule]
 * constructor that `createAndroidComposeRule<MainActivity>()` uses internally —
 * `activityRule = E2eScenarioRule(), activityProvider = { it.activity() }`.
 * The `@Suppress("DEPRECATION")` mirrors what the Compose library itself
 * applies in its `createAndroidComposeRule` factory
 * (`androidx.compose.ui.test.junit4.AndroidComposeTestRule.android.kt:131`).
 */
@Suppress("DEPRECATION")
fun e2eAndroidComposeRule(seed: () -> Unit = { E2eHarness.seedServerProfile() }):
    AndroidComposeTestRule<E2eScenarioRule, MainActivity> {
    val scenarioRule = E2eScenarioRule(seed)
    return AndroidComposeTestRule(
        activityRule = scenarioRule,
        activityProvider = { rule -> rule.activity() },
    )
}