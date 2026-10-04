package com.m57.hermescontrol.ui.sessions

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.m57.hermescontrol.data.model.SessionSearchResult
import org.junit.Rule
import org.junit.Test

class SessionSearchDisplayCacheTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun titleOnlyChangeInvalidatesTheProductionRememberCache() {
        val state =
            mutableStateOf(
                SessionsUiState(
                    searchQuery = "a",
                    searchResults = listOf(SessionSearchResult("a")),
                    searchTitles = mapOf("a" to "Before"),
                ),
            )
        val sections = SessionSections(emptyList(), emptyList())
        composeTestRule.setContent {
            Text(rememberSessionsToDisplay(state.value, sections).single().displayTitle)
        }
        composeTestRule.onNodeWithText("Before").assertIsDisplayed()
        composeTestRule.runOnIdle {
            state.value = state.value.copy(searchTitles = mapOf("a" to "After"))
        }
        composeTestRule.onNodeWithText("After").assertIsDisplayed()
        composeTestRule.onNodeWithText("Before").assertDoesNotExist()
    }
}
