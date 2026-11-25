package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.ws.ConnectionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The connection state moves in three small steps: latch the first connect, show a pill, clear the pill.
 *
 * Each rule moves exactly one field. The other two are carried over untouched, and that is what these tests pin.
 */
class ConnectionStateUpdatesTest {
    private fun state(
        connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
        statusPill: String? = null,
        hasConnectedOnce: Boolean = false,
    ): ConnectionState =
        ConnectionState(
            connectionStatus = connectionStatus,
            statusPill = statusPill,
            hasConnectedOnce = hasConnectedOnce,
        )

    @Test
    fun theFirstConnectIsLatched() {
        assertTrue(state().afterFirstConnected().hasConnectedOnce)
    }

    @Test
    fun theLatchStaysSetOnALaterConnect() {
        val after = state(hasConnectedOnce = true).afterFirstConnected()
        assertTrue(after.hasConnectedOnce)
    }

    @Test
    fun theLatchKeepsTheStatusAndThePill() {
        val after =
            state(
                connectionStatus = ConnectionStatus.CONNECTED,
                statusPill = "Connected to Hermes",
            ).afterFirstConnected()
        assertEquals(ConnectionStatus.CONNECTED, after.connectionStatus)
        assertEquals("Connected to Hermes", after.statusPill)
    }

    @Test
    fun thePillShowsTheGivenText() {
        assertEquals("Session resumed", state().afterStatusPill("Session resumed").statusPill)
    }

    @Test
    fun aLaterPillReplacesTheEarlierOne() {
        val after = state(statusPill = "Session resumed").afterStatusPill("Connected to Hermes")
        assertEquals("Connected to Hermes", after.statusPill)
    }

    @Test
    fun thePillKeepsTheStatusAndTheLatch() {
        val after =
            state(
                connectionStatus = ConnectionStatus.RECONNECTING,
                hasConnectedOnce = true,
            ).afterStatusPill("Reconnecting")
        assertEquals(ConnectionStatus.RECONNECTING, after.connectionStatus)
        assertTrue(after.hasConnectedOnce)
    }

    @Test
    fun thePillClearsAndKeepsTheStatusAndTheLatch() {
        val after =
            state(
                connectionStatus = ConnectionStatus.CONNECTED,
                statusPill = "Session resumed",
                hasConnectedOnce = true,
            ).afterStatusPillCleared()
        assertNull(after.statusPill)
        assertEquals(ConnectionStatus.CONNECTED, after.connectionStatus)
        assertTrue(after.hasConnectedOnce)
    }

    @Test
    fun anUnconnectedStateHasNoPillAndNoLatch() {
        val fresh = state()
        assertNull(fresh.statusPill)
        assertFalse(fresh.hasConnectedOnce)
        assertEquals(ConnectionStatus.DISCONNECTED, fresh.connectionStatus)
    }
}
