package com.m57.hermescontrol.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m57.hermescontrol.ui.chat.components.ReasoningCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownUpstreamRenderingTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tableCellsRenderInlineMarkdown() {
        composeTestRule.setContent {
            MaterialTheme {
                MarkdownText(
                    text = "| **Header** |\n| --- |\n| [**bold `code`**](https://example.com) |",
                    textColor = Color.Black,
                )
            }
        }
        composeTestRule.onNodeWithText("Header").assertIsDisplayed()
        composeTestRule.onNodeWithText("bold code").assertIsDisplayed()
        composeTestRule.onNodeWithText("**Header**").assertDoesNotExist()
    }

    @Test
    fun completedReasoningRendersMarkdown() {
        composeTestRule.setContent {
            MaterialTheme {
                ReasoningCard(reasoningText = "**Important** completed thought")
            }
        }
        composeTestRule.onNodeWithTag("reasoning_card").performClick()
        composeTestRule.onNodeWithText("Important completed thought").assertIsDisplayed()
        composeTestRule.onNodeWithText("**Important** completed thought").assertDoesNotExist()
    }

    @Test
    fun streamingReasoningRemainsRaw() {
        composeTestRule.setContent {
            MaterialTheme {
                ReasoningCard(reasoningText = "**Thinking**", isStreaming = true)
            }
        }
        composeTestRule.onNodeWithTag("reasoning_card").performClick()
        composeTestRule.onNodeWithText("**Thinking**").assertIsDisplayed()
        composeTestRule.onNodeWithText("Thinking").assertDoesNotExist()
    }
}
