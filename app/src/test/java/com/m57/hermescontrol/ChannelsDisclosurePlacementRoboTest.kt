package com.m57.hermescontrol

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.theme.ThemePreference
import com.m57.hermescontrol.theme.ThemePreset
import com.m57.hermescontrol.ui.icons.MaterialSymbol
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Throwaway: where else can a disclosure affordance sit, if not at the far trailing edge. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w460dp-h920dp-420dpi", application = android.app.Application::class)
class ChannelsDisclosurePlacementRoboTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun channels_disclosure_placement() {
        composeRule.setContent {
            HermesControlTheme(
                themePreference = ThemePreference.DARK,
                useDynamicColors = false,
                themePreset = ThemePreset.DEFAULT,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Card("1 · disclosure between text and switch") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Telegram", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.width(6.dp))
                            Chevron()
                            Spacer(modifier = Modifier.weight(1f))
                            Switch(checked = true, onCheckedChange = {})
                        }
                    }
                    Card("2 · leading triangle before the text (macOS/outline)") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Chevron()
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Telegram", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.weight(1f))
                            Switch(checked = true, onCheckedChange = {})
                        }
                    }
                    Card("3 · disclosure under the text block (secondary line)") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Telegram", style = MaterialTheme.typography.bodyLarge)
                                Row(
                                    modifier = Modifier.clickable {}.padding(top = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Connection details",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Chevron()
                                }
                            }
                            Switch(checked = true, onCheckedChange = {})
                        }
                    }
                    Card("4 · expander strip under the row (full width)") {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Telegram", style = MaterialTheme.typography.bodyLarge)
                                Spacer(modifier = Modifier.weight(1f))
                                Switch(checked = true, onCheckedChange = {})
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {}.padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Advanced",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Chevron()
                            }
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/assets/roborazzi/channels-disclosure-placement.png")
    }
}

@Composable
private fun Chevron() {
    MaterialSymbol(
        name = "chevron_right",
        contentDescription = "Open",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Card(
    label: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
        )
        Surface(
            modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}
