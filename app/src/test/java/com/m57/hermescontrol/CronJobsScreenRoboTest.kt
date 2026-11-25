package com.m57.hermescontrol

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.m57.hermescontrol.data.model.CronJob
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.theme.ThemePreference
import com.m57.hermescontrol.theme.ThemePreset
import com.m57.hermescontrol.ui.cron.CronJobsScreen
import com.m57.hermescontrol.ui.cron.CronJobsUiState
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pixel captures of the Cron Jobs screen from state alone.
 *
 * Record: ./gradlew recordRoborazziDebug --tests "*CronJobsScreenRoboTest*"
 * Verify: ./gradlew verifyRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w460dp-h920dp-420dpi",
    application = android.app.Application::class,
)
class CronJobsScreenRoboTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        FakeAndroidKeyStore.setup
    }

    private fun capture(
        name: String,
        state: CronJobsUiState,
    ) {
        composeRule.setContent {
            HermesControlTheme(
                themePreference = ThemePreference.DARK,
                useDynamicColors = false,
                themePreset = ThemePreset.DEFAULT,
            ) {
                CronJobsScreen(
                    state = state,
                    onRefresh = {},
                    onRetry = {},
                    onAddJob = {},
                    onEditJob = { },
                    onToggleEnabled = { _, _ -> },
                    onRunJob = {},
                    onRequestDelete = {},
                    onConfirmDelete = {},
                    onDismissDelete = {},
                    onEditorFieldChange = { _, _ -> },
                    onEditorToggleNoAgent = {},
                    onEditorToggleRunContinuity = {},
                    onEditorSetMonitorMode = {},
                    onEditorSave = {},
                    onEditorDismiss = {},
                    onEditorClearToast = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/assets/roborazzi/$name.png")
    }

    @Test
    fun cron_populated() = capture("cron-populated", CronJobsUiState(jobs = FakeJobs))

    @Test
    fun cron_empty() = capture("cron-empty", CronJobsUiState())

    @Test
    fun cron_loading() = capture("cron-loading", CronJobsUiState(isLoading = true))
}

private val FakeJobs =
    listOf(
        CronJob(
            id = "job-nw-quota",
            name = "nw-quota-watch",
            schedule = JsonPrimitive("0 9 * * *"),
            state = "active",
            last_run_status = "ok",
            last_run_at = "2026-11-12T09:00:00",
            next_run_at = "2026-11-13T09:00:00",
            enabled = true,
            deliver = "telegram",
        ),
        CronJob(
            id = "job-weekly-review",
            name = "weekly-review",
            schedule = JsonPrimitive("0 18 * * 5"),
            state = "active",
            last_run_status = "no_change",
            last_run_at = "2026-11-06T18:00:00",
            next_run_at = "2026-11-13T18:00:00",
            enabled = true,
            context_from = listOf("self"),
        ),
        CronJob(
            id = "job-nightly-backup",
            name = "nightly-backup",
            schedule = JsonPrimitive("30 2 * * *"),
            state = "paused",
            last_run_status = "blocked_config",
            last_run_at = "2026-11-11T02:30:00",
            enabled = false,
            deliver = "local",
        ),
    )
