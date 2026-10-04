package com.m57.hermescontrol.ui.sessions

import com.m57.hermescontrol.data.model.LiveSessionSnapshot
import com.m57.hermescontrol.data.model.SessionLiveStatus
import com.m57.hermescontrol.data.ws.ConnectionStatus
import com.m57.hermescontrol.data.ws.SessionLiveConnection
import com.m57.hermescontrol.data.ws.SessionLiveStatusSource
import com.m57.hermescontrol.data.ws.SourcedSessionLiveEvent
import com.m57.hermescontrol.data.ws.WsEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionsLiveStatusViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `suspended profile A snapshot cannot decorate profile B`() {
        val source = FakeLiveStatusSource()
        val staleSnapshot = CompletableDeferred<LiveSessionSnapshot?>()
        source.snapshots.add(staleSnapshot)
        source.connections["profile-a"] = SessionLiveConnection("profile-a", 1)
        source.connections["profile-b"] = SessionLiveConnection("profile-b", 2)
        val viewModel = testViewModel(source)

        viewModel.startLiveStatusTracking()
        source.profiles.value = "profile-a"
        source.status.value = ConnectionStatus.CONNECTED
        dispatcher.scheduler.runCurrent()

        source.profiles.value = "profile-b"
        dispatcher.scheduler.runCurrent()
        staleSnapshot.complete(snapshot("runtime-a", "stored-a", SessionLiveStatus.WORKING))
        dispatcher.scheduler.runCurrent()

        assertNull(viewModel.uiState.value.liveStatuses["stored-a"])
        assertEquals("profile-b", source.requestedConnections.last().profileId)
    }

    @Test
    fun `event received during snapshot wins and schedules authoritative refresh`() {
        val source = FakeLiveStatusSource()
        val staleSnapshot = CompletableDeferred<LiveSessionSnapshot?>()
        source.snapshots.add(staleSnapshot)
        source.snapshots.add(CompletableDeferred(LiveSessionSnapshot(emptyMap(), emptyMap())))
        source.connections["profile-a"] = SessionLiveConnection("profile-a", 7)
        val viewModel = testViewModel(source)

        viewModel.startLiveStatusTracking()
        source.profiles.value = "profile-a"
        source.status.value = ConnectionStatus.CONNECTED
        dispatcher.scheduler.runCurrent()
        source.events.tryEmit(
            SourcedSessionLiveEvent(
                event =
                    WsEvent.SessionInfo(
                        mapOf(
                            "session_id" to "runtime-a",
                            "stored_session_id" to "stored-a",
                            "running" to false,
                        ),
                    ),
                profileId = "profile-a",
                connectionGeneration = 7,
            ),
        )
        dispatcher.scheduler.runCurrent()
        staleSnapshot.complete(snapshot("runtime-a", "stored-a", SessionLiveStatus.WORKING))
        dispatcher.scheduler.runCurrent()

        assertNull(viewModel.uiState.value.liveStatuses["stored-a"])
        assertEquals(2, source.requestedConnections.size)
    }

    @Test
    fun `stream deltas during snapshot do not invalidate it or schedule refresh`() {
        val source = FakeLiveStatusSource()
        val suspendedSnapshot = CompletableDeferred<LiveSessionSnapshot?>()
        source.snapshots.add(suspendedSnapshot)
        source.connections["profile-a"] = SessionLiveConnection("profile-a", 7)
        val viewModel = testViewModel(source)

        viewModel.startLiveStatusTracking()
        source.profiles.value = "profile-a"
        dispatcher.scheduler.runCurrent()
        source.status.value = ConnectionStatus.CONNECTED
        dispatcher.scheduler.runCurrent()
        source.events.tryEmit(sourced(WsEvent.MessageToken("token", "runtime-a")))
        source.events.tryEmit(sourced(WsEvent.ThinkingDelta("thinking", "runtime-a")))
        source.events.tryEmit(sourced(WsEvent.ReasoningDelta("reasoning", "runtime-a")))
        dispatcher.scheduler.runCurrent()
        suspendedSnapshot.complete(snapshot("runtime-a", "stored-a", SessionLiveStatus.WORKING))
        dispatcher.scheduler.runCurrent()

        assertEquals(SessionLiveStatus.WORKING, viewModel.uiState.value.liveStatuses["stored-a"])
        assertEquals(1, source.requestedConnections.size)
    }

    @Test
    fun `repeated live transition during snapshot still schedules one refresh`() {
        val source = FakeLiveStatusSource()
        val suspendedSnapshot = CompletableDeferred<LiveSessionSnapshot?>()
        source.snapshots.add(CompletableDeferred(snapshot("runtime-a", "stored-a", SessionLiveStatus.WORKING)))
        source.snapshots.add(suspendedSnapshot)
        source.snapshots.add(CompletableDeferred(snapshot("runtime-a", "stored-a", SessionLiveStatus.WORKING)))
        source.connections["profile-a"] = SessionLiveConnection("profile-a", 7)
        val viewModel = testViewModel(source)

        viewModel.startLiveStatusTracking()
        source.profiles.value = "profile-a"
        source.status.value = ConnectionStatus.CONNECTED
        dispatcher.scheduler.runCurrent()
        viewModel.refreshLiveStatuses()
        dispatcher.scheduler.runCurrent()
        source.events.tryEmit(
            sourced(
                WsEvent.SessionInfo(
                    mapOf(
                        "session_id" to "runtime-a",
                        "stored_session_id" to "stored-a",
                        "running" to true,
                    ),
                ),
            ),
        )
        dispatcher.scheduler.runCurrent()
        suspendedSnapshot.complete(snapshot("runtime-a", "stored-a", SessionLiveStatus.WAITING))
        dispatcher.scheduler.runCurrent()

        assertEquals(SessionLiveStatus.WORKING, viewModel.uiState.value.liveStatuses["stored-a"])
        assertEquals(3, source.requestedConnections.size)
    }

    @Test
    fun `wrong profile or connection generation event is ignored`() {
        val source = FakeLiveStatusSource()
        source.snapshots.add(CompletableDeferred(snapshot("runtime-a", "stored-a", SessionLiveStatus.WORKING)))
        source.connections["profile-a"] = SessionLiveConnection("profile-a", 3)
        val viewModel = testViewModel(source)

        viewModel.startLiveStatusTracking()
        source.profiles.value = "profile-a"
        source.status.value = ConnectionStatus.CONNECTED
        dispatcher.scheduler.runCurrent()
        source.events.tryEmit(
            SourcedSessionLiveEvent(
                WsEvent.MessageDone("runtime-a"),
                profileId = "profile-a",
                connectionGeneration = 2,
            ),
        )
        dispatcher.scheduler.runCurrent()

        assertEquals(SessionLiveStatus.WORKING, viewModel.uiState.value.liveStatuses["stored-a"])
    }

    @Test
    fun `disconnect clears status and unsupported refresh preserves last connected state`() {
        val source = FakeLiveStatusSource()
        source.snapshots.add(CompletableDeferred(snapshot("runtime-a", "stored-a", SessionLiveStatus.WAITING)))
        source.snapshots.add(CompletableDeferred(null))
        source.connections["profile-a"] = SessionLiveConnection("profile-a", 4)
        val viewModel = testViewModel(source)

        viewModel.startLiveStatusTracking()
        source.profiles.value = "profile-a"
        source.status.value = ConnectionStatus.CONNECTED
        dispatcher.scheduler.runCurrent()
        assertEquals(SessionLiveStatus.WAITING, viewModel.uiState.value.liveStatuses["stored-a"])
        assertEquals(true, viewModel.uiState.value.liveStatusesAuthoritative)

        viewModel.refreshLiveStatuses()
        dispatcher.scheduler.runCurrent()
        assertEquals(SessionLiveStatus.WAITING, viewModel.uiState.value.liveStatuses["stored-a"])

        source.status.value = ConnectionStatus.DISCONNECTED
        dispatcher.scheduler.runCurrent()
        assertEquals(emptyMap<String, SessionLiveStatus>(), viewModel.uiState.value.liveStatuses)
        assertEquals(false, viewModel.uiState.value.liveStatusesAuthoritative)
    }

    @Test
    fun `stopping live tracking cancels an in-flight snapshot`() {
        val source = FakeLiveStatusSource()
        source.snapshots.add(CompletableDeferred())
        source.connections["profile-a"] = SessionLiveConnection("profile-a", 4)
        val viewModel = testViewModel(source)

        viewModel.startLiveStatusTracking()
        source.profiles.value = "profile-a"
        source.status.value = ConnectionStatus.CONNECTED
        dispatcher.scheduler.runCurrent()

        viewModel.stopLiveStatusTracking()
        dispatcher.scheduler.runCurrent()

        assertEquals(1, source.cancelledRequests)
    }

    private fun snapshot(
        runtimeId: String,
        storedId: String,
        status: SessionLiveStatus,
    ) = LiveSessionSnapshot(mapOf(storedId to status), mapOf(runtimeId to storedId))

    private fun sourced(event: WsEvent) =
        SourcedSessionLiveEvent(
            event = event,
            profileId = "profile-a",
            connectionGeneration = 7,
        )

    private fun testViewModel(source: SessionLiveStatusSource) =
        SessionsViewModel(
            pinStore =
                object : SessionPinStore {
                    override fun load(): List<String> = emptyList()

                    override fun save(pinIds: List<String>) = Unit
                },
            ioDispatcher = dispatcher,
            liveStatusSource = source,
        )

    private class FakeLiveStatusSource : SessionLiveStatusSource {
        val status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        val profiles = MutableStateFlow("initial")
        val events = MutableSharedFlow<SourcedSessionLiveEvent>(extraBufferCapacity = 8)
        val connections = mutableMapOf<String, SessionLiveConnection>()
        val snapshots = ArrayDeque<CompletableDeferred<LiveSessionSnapshot?>>()
        val requestedConnections = mutableListOf<SessionLiveConnection>()
        var cancelledRequests = 0

        override val sourcedEvents: Flow<SourcedSessionLiveEvent> = events
        override val connectionStatus: StateFlow<ConnectionStatus> = status
        override val selectedProfileIds: Flow<String> = profiles

        override fun currentConnection(profileId: String): SessionLiveConnection? = connections[profileId]

        override suspend fun fetchActiveSessionsSnapshot(connection: SessionLiveConnection): LiveSessionSnapshot? {
            requestedConnections += connection
            return try {
                snapshots.removeFirstOrNull()?.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelledRequests++
                throw e
            }
        }
    }
}
