package com.m57.hermescontrol.ui.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.timeline_marker_auto_continue
import com.m57.hermescontrol.generated.resources.timeline_marker_delegation_complete
import com.m57.hermescontrol.generated.resources.timeline_marker_internal_notification
import com.m57.hermescontrol.generated.resources.timeline_marker_max_iterations
import com.m57.hermescontrol.generated.resources.timeline_marker_model_switch
import com.m57.hermescontrol.generated.resources.timeline_marker_model_switch_to
import com.m57.hermescontrol.generated.resources.timeline_marker_personality_switch
import com.m57.hermescontrol.generated.resources.timeline_marker_skill_invocation
import com.m57.hermescontrol.generated.resources.timeline_marker_system_notice
import com.m57.hermescontrol.theme.Spacing
import com.m57.hermescontrol.ui.chat.SystemTimelineEvent
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun timelineEventLabel(kind: String): StringResource =
    when (kind) {
        "model_switch" -> Res.string.timeline_marker_model_switch
        "personality_switch" -> Res.string.timeline_marker_personality_switch
        "auto_continue" -> Res.string.timeline_marker_auto_continue
        "async_delegation_complete" -> Res.string.timeline_marker_delegation_complete
        "skill_invocation" -> Res.string.timeline_marker_skill_invocation
        "internal_notification" -> Res.string.timeline_marker_internal_notification
        "max_iterations_reached" -> Res.string.timeline_marker_max_iterations
        else -> Res.string.timeline_marker_system_notice
    }

@Composable
fun SystemTimelineMarker(
    event: SystemTimelineEvent,
    modifier: Modifier = Modifier,
) {
    val text =
        if (event.kind == "model_switch" && event.model != null) {
            stringResource(Res.string.timeline_marker_model_switch_to, requireNotNull(event.model))
        } else {
            stringResource(timelineEventLabel(event.kind))
        }
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("system_timeline_event"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = Spacing.xs),
            )
        }
    }
}
