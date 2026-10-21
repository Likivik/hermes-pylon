package com.m57.hermescontrol.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * Phase 2 toolchain proof. Renders [CmpProof] inside `runComposeUiTest` and
 * asserts the label reaches the semantics tree. Runs on the JVM target (Skiko
 * headless, driven by the devshell's Skiko system libraries).
 *
 * If `runComposeUiTest` resolves and the assertion passes, the CMP plugin
 * and adaptive fork are wired correctly and CMP Phase 3 can start moving
 * screens from `:ui/androidMain` into `commonMain`.
 *
 * The tag deliberately sits on the container while the assertion targets the
 * text node: a `Box` does not merge its descendants' semantics, so
 * `assertTextEquals` against the tagged container fails even when the text
 * renders correctly.
 */
@OptIn(ExperimentalTestApi::class)
class CmpProofTest {
    @Test
    fun cmpProofComposableRendersItsLabel() =
        runComposeUiTest {
            setContent {
                CmpProof(
                    label = "CMP proof",
                    modifier = Modifier.testTag("cmp-proof"),
                )
            }
            onNodeWithTag("cmp-proof").assertIsDisplayed()
            onNodeWithText("CMP proof").assertIsDisplayed()
        }
}
