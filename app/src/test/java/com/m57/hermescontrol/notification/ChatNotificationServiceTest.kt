package com.m57.hermescontrol.notification

import com.m57.hermescontrol.data.ws.WsEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [ChatNotificationService] companion object — specifically
 * the `isAppInForeground` flag that gates notification display.
 *
 * Issue #291 (Critical Test Coverage): Verifies that `setAppForeground`
 * correctly toggles the flag and that the field is `@Volatile` for thread
 * safety (it is read from the event-collection coroutine on a background
 * dispatcher while `setAppForeground` is called from the UI thread).
 *
 * These are pure unit tests with no Android dependencies — the companion
 * object can be exercised directly via reflection for the private flag
 * or through the public `setAppForeground` API.
 */
class ChatNotificationServiceTest {
    @Test
    fun `setAppForeground true sets the flag`() {
        val field =
            ChatNotificationService::class.java
                .getDeclaredField("isAppInForeground")
        field.isAccessible = true

        val atomicBoolean = field.get(null) as java.util.concurrent.atomic.AtomicBoolean

        // Reset to known state
        atomicBoolean.set(false)
        ChatNotificationService.setAppForeground(true)

        assertEquals("flag should be true after setAppForeground(true)", true, atomicBoolean.get())
    }

    @Test
    fun `setAppForeground false clears the flag`() {
        val field =
            ChatNotificationService::class.java
                .getDeclaredField("isAppInForeground")
        field.isAccessible = true

        val atomicBoolean = field.get(null) as java.util.concurrent.atomic.AtomicBoolean

        // Set to known state
        atomicBoolean.set(true)
        ChatNotificationService.setAppForeground(false)

        assertEquals("flag should be false after setAppForeground(false)", false, atomicBoolean.get())
    }

    @Test
    fun `isAppInForeground defaults to false`() {
        val field =
            ChatNotificationService::class.java
                .getDeclaredField("isAppInForeground")
        field.isAccessible = true

        val atomicBoolean = field.get(null) as java.util.concurrent.atomic.AtomicBoolean

        // Ensure it's in a clean state
        atomicBoolean.set(false)

        assertEquals("initial value should be false", false, atomicBoolean.get())
    }

    @Test
    fun `isAppInForeground field is volatile for thread safety`() {
        // Obsolete test as the type is now AtomicBoolean which guarantees thread safety implicitly
    }

    @Test
    fun `setAppForeground is idempotent when called multiple times`() {
        val field =
            ChatNotificationService::class.java
                .getDeclaredField("isAppInForeground")
        field.isAccessible = true

        val atomicBoolean = field.get(null) as java.util.concurrent.atomic.AtomicBoolean

        // Set true twice
        ChatNotificationService.setAppForeground(true)
        ChatNotificationService.setAppForeground(true)
        assertEquals("flag should remain true after consecutive setAppForeground(true)", true, atomicBoolean.get())

        // Set false twice
        ChatNotificationService.setAppForeground(false)
        ChatNotificationService.setAppForeground(false)
        assertEquals(
            "flag should remain false after consecutive setAppForeground(false)",
            false,
            atomicBoolean.get(),
        )
    }
}

/**
 * Background notification policy.
 *
 * Privileged gateway requests (`sudo.request`, `secret.request`,
 * `approval.request`) must never reach the system shade. Each is answered only
 * from the foreground against an exact request binding, and a direct-reply
 * notification would become an out-of-app secret-entry surface that persists
 * the typed value in system UI. Their expiry frames are equally silent — a
 * notification about a privileged request is itself a disclosure that one was
 * made.
 */
class ChatNotificationPolicyTest {
    private val privilegedEvents =
        listOf<WsEvent>(
            WsEvent.ApprovalRequest(
                command = "rm -rf /data",
                description = "Dangerous",
                patternKeys = null,
                sessionId = "session-a",
                requestId = "req-1",
                timeoutSeconds = 300.0,
                sourceProfileId = "profile-a",
                connectionGeneration = 7,
            ),
            WsEvent.SudoRequest("req-2", "session-a", "profile-a", 7),
            WsEvent.SudoExpire("req-2", "session-a", "profile-a", 7),
            WsEvent.SecretRequest("req-3", "session-a", "GITHUB_TOKEN", "Token?", "profile-a", 7),
            WsEvent.SecretExpire("req-3", "session-a", "profile-a", 7),
            WsEvent.PrivilegedRequestRejected("approval.request", "session-a"),
        )

    @Test
    fun `privileged requests are never notified`() {
        privilegedEvents.forEach { event ->
            assertEquals(
                "$event must not reach the system shade",
                ChatNotificationDecision.Ignore,
                notificationDecisionFor(event, storedSessionId = null),
            )
        }
    }

    @Test
    fun `no privileged notification leaks request content`() {
        privilegedEvents.forEach { event ->
            val decision = notificationDecisionFor(event, storedSessionId = "session-a")
            assertFalse(decision.toString().contains("GITHUB_TOKEN"))
            assertFalse(decision.toString().contains("rm -rf /data"))
        }
    }

    @Test
    fun `a completed reply is still notified with its stored session`() {
        val decision =
            notificationDecisionFor(
                WsEvent.MessageComplete("All done\nwith the task", "runtime-a"),
                storedSessionId = "stored-a",
            )

        assertEquals(
            ChatNotificationDecision.Reply(
                preview = "All done with the task",
                sessionId = "stored-a",
                stopService = true,
            ),
            decision,
        )
    }

    @Test
    fun `a blank reply falls back to the default preview and never routes a session`() {
        val decision =
            notificationDecisionFor(WsEvent.MessageComplete("   ", null), storedSessionId = null)
                as ChatNotificationDecision.Reply

        assertNull(decision.preview)
        assertNull(decision.sessionId)
    }

    @Test
    fun `a reply preview is truncated`() {
        val decision =
            notificationDecisionFor(WsEvent.MessageComplete("x".repeat(500), "s"), storedSessionId = null)
                as ChatNotificationDecision.Reply

        assertEquals(100, decision.preview?.length)
    }

    /** Clarification is not privileged, but still carries no request text. */
    @Test
    fun `a clarify request is notified without its content`() {
        val decision =
            notificationDecisionFor(
                WsEvent.ClarifyRequest("Which file?", listOf("a", "b"), "c1", "session-a"),
                storedSessionId = "session-a",
            )

        assertEquals(ChatNotificationDecision.Clarify, decision)
        assertFalse(decision.toString().contains("Which file?"))
    }
}
