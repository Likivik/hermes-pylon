package com.m57.hermescontrol.ui.chat.components

import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.timeline_marker_auto_continue
import com.m57.hermescontrol.generated.resources.timeline_marker_delegation_complete
import com.m57.hermescontrol.generated.resources.timeline_marker_internal_notification
import com.m57.hermescontrol.generated.resources.timeline_marker_max_iterations
import com.m57.hermescontrol.generated.resources.timeline_marker_model_switch
import com.m57.hermescontrol.generated.resources.timeline_marker_personality_switch
import com.m57.hermescontrol.generated.resources.timeline_marker_skill_invocation
import com.m57.hermescontrol.generated.resources.timeline_marker_system_notice
import com.m57.hermescontrol.ui.chat.modelFromTimelineContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemTimelineMarkerTest {
    @Test
    fun everySupportedKindHasExpectedFriendlyLabel() {
        assertEquals(Res.string.timeline_marker_model_switch, timelineEventLabel("model_switch"))
        assertEquals(
            Res.string.timeline_marker_personality_switch,
            timelineEventLabel("personality_switch"),
        )
        assertEquals(Res.string.timeline_marker_auto_continue, timelineEventLabel("auto_continue"))
        assertEquals(
            Res.string.timeline_marker_delegation_complete,
            timelineEventLabel("async_delegation_complete"),
        )
        assertEquals(Res.string.timeline_marker_skill_invocation, timelineEventLabel("skill_invocation"))
        assertEquals(
            Res.string.timeline_marker_internal_notification,
            timelineEventLabel("internal_notification"),
        )
        assertEquals(
            Res.string.timeline_marker_max_iterations,
            timelineEventLabel("max_iterations_reached"),
        )
        assertEquals(Res.string.timeline_marker_system_notice, timelineEventLabel("future_kind"))
    }

    @Test
    fun modelNameIsParsedWithoutSentencePunctuation() {
        assertEquals(
            "gpt-5.6-sol",
            modelFromTimelineContent(
                "[System: The active model for this chat has changed to gpt-5.6-sol via provider openai.]",
            ),
        )
        assertNull(modelFromTimelineContent("Model changed without a target"))
    }
}
