package com.m57.hermescontrol.ui.chat.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import com.m57.hermescontrol.data.ws.PrivilegedRequestBinding
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PrivilegedPromptDialogTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sudoReplacementClearsPasswordAndCannotSubmitOldValue() {
        var binding by mutableStateOf(binding("sudo-1", "session-1", "profile-1", 1))
        val submitted = mutableListOf<String>()
        composeTestRule.setContent {
            SudoPromptDialog(
                binding = binding,
                onConfirm = submitted::add,
                onCancel = {},
                onDismiss = {},
            )
        }

        val passwordInput = composeTestRule.onNodeWithTag("sudo_password_input")
        passwordInput.performTextInput("old-password")
        passwordInput.assert(inputTextEquals("old-password"))
        composeTestRule.runOnIdle {
            binding = binding("sudo-2", "session-2", "profile-2", 2)
        }

        passwordInput.assert(inputTextEquals(""))
        composeTestRule.onNodeWithTag("sudo_send_button").assertIsNotEnabled()
        composeTestRule.runOnIdle { assertTrue(submitted.isEmpty()) }
    }

    @Test
    fun secretReplacementClearsValueAndCannotSubmitOldValue() {
        var binding by mutableStateOf(binding("secret-1", "session-1", "profile-1", 1))
        val submitted = mutableListOf<String>()
        composeTestRule.setContent {
            SecretPromptDialog(
                binding = binding,
                onConfirm = submitted::add,
                onCancel = {},
                onDismiss = {},
            )
        }

        val secretInput = composeTestRule.onNodeWithTag("secret_value_input")
        secretInput.performTextInput("old-secret")
        secretInput.assert(inputTextEquals("old-secret"))
        composeTestRule.runOnIdle {
            binding = binding("secret-2", "session-2", "profile-2", 2)
        }

        secretInput.assert(inputTextEquals(""))
        composeTestRule.onNodeWithTag("secret_send_button").assertIsNotEnabled()
        composeTestRule.runOnIdle { assertTrue(submitted.isEmpty()) }
    }

    private fun binding(
        requestId: String,
        runtimeSessionId: String,
        profileId: String,
        connectionGeneration: Int,
    ) = PrivilegedRequestBinding(
        requestId = requestId,
        runtimeSessionId = runtimeSessionId,
        profileId = profileId,
        connectionGeneration = connectionGeneration,
    )

    private fun inputTextEquals(value: String) =
        SemanticsMatcher.expectValue(
            SemanticsProperties.InputText,
            AnnotatedString(value),
        )
}
