package com.m57.hermescontrol

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.m57.hermescontrol.data.model.WebhookSubscription
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.theme.ThemePreference
import com.m57.hermescontrol.theme.ThemePreset
import com.m57.hermescontrol.ui.webhooks.WebhooksScreen
import com.m57.hermescontrol.ui.webhooks.WebhooksUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pixel captures of the Webhooks screen from state alone.
 *
 * Record: ./gradlew recordRoborazziDebug --tests "*WebhooksScreenRoboTest*"
 * Verify: ./gradlew verifyRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w460dp-h920dp-420dpi",
    application = android.app.Application::class,
)
class WebhooksScreenRoboTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        FakeAndroidKeyStore.setup
    }

    private fun capture(
        name: String,
        state: WebhooksUiState,
    ) {
        composeRule.setContent {
            HermesControlTheme(
                themePreference = ThemePreference.DARK,
                useDynamicColors = false,
                themePreset = ThemePreset.DEFAULT,
            ) {
                WebhooksScreen(
                    state = state,
                    onRefresh = {},
                    onRetry = {},
                    onToggleGlobal = { },
                    onToggleSubscription = { _, _ -> },
                    onShowCreateDialog = {},
                    onDismissCreateDialog = {},
                    onCreateFormNameChange = {},
                    onCreateFormDescriptionChange = {},
                    onCreateFormEventsChange = {},
                    onCreateFormDeliverChange = {},
                    onCreateFormSecretChange = {},
                    onCreateSubscription = {},
                    onPromptDeleteSubscription = {},
                    onDismissDeleteDialog = {},
                    onConfirmDeleteSubscription = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/assets/roborazzi/$name.png")
    }

    @Test
    fun webhooks_populated() =
        capture(
            "webhooks-populated",
            WebhooksUiState(
                isLoading = false,
                enabled = true,
                baseUrl = "https://hooks.hermes.example.com/api/webhooks",
                subscriptions = FakeSubscriptions,
            ),
        )

    @Test
    fun webhooks_empty() =
        capture(
            "webhooks-empty",
            WebhooksUiState(
                isLoading = false,
                enabled = false,
                baseUrl = "https://hooks.hermes.example.com/api/webhooks",
                subscriptions = emptyList(),
            ),
        )

    @Test
    fun webhooks_loading() = capture("webhooks-loading", WebhooksUiState(isLoading = true))
}

private val FakeSubscriptions =
    listOf(
        WebhookSubscription(
            name = "message.created",
            description = "Notifies the ops channel whenever a new message arrives.",
            events = listOf("message.created", "message.updated", "message.deleted"),
            deliver = "telegram",
            deliver_only = true,
            created_at = "2026-02-11T09:14:00Z",
            url = "https://hooks.hermes.example.com/api/webhooks/message.created",
            secret_set = true,
            enabled = true,
        ),
        WebhookSubscription(
            name = "session.finished",
            description = "Reports finished agent sessions back to the dashboard.",
            events = listOf("session.finished", "session.failed"),
            deliver = "log",
            created_at = "2026-03-02T17:41:00Z",
            url = "https://hooks.hermes.example.com/api/webhooks/session.finished",
            secret_set = false,
            enabled = true,
        ),
        WebhookSubscription(
            name = "tool.failed",
            description = "Alerts when a tool call fails after retries.",
            events = listOf("tool.failed"),
            deliver = "slack",
            created_at = "2026-04-19T11:02:00Z",
            url = "https://hooks.hermes.example.com/api/webhooks/tool.failed",
            secret_set = true,
            enabled = false,
        ),
    )
