// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioSerializer
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.util.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource

/**
 * The persisted server/profile/window state, over any DataStore.
 *
 * Platform-neutral: the only thing a platform supplies is the `Storage` (a file
 * on Android and desktop, `localStorage`/OPFS in the browser). See the
 * `serverStoreStorage` builders in each target's source set.
 */
class ServerStore(
    private val dataStore: DataStore<ServerStoreState>,
    private val scope: CoroutineScope,
) {
    private val _stateFlow: MutableStateFlow<ServerStoreState>
    val stateFlow: StateFlow<ServerStoreState>

    init {
        // Android and desktop can block until the first read lands; the browser
        // cannot (no `runBlocking` on wasmJs), so it starts on the default and
        // the launch below fills it in on the first emission.
        _stateFlow = MutableStateFlow(firstBlocking(dataStore.data, ServerStoreSerializer.defaultValue).selfHealed())
        stateFlow = _stateFlow.asStateFlow()
        scope.launch(ioDispatcher) {
            runCatching { dataStore.data.first() }.onSuccess { loaded ->
                _stateFlow.update { current ->
                    if (current == ServerStoreSerializer.defaultValue) loaded.selfHealed() else current
                }
            }
        }
    }

    fun getLatestState(): ServerStoreState = _stateFlow.value

    fun update(transform: (ServerStoreState) -> ServerStoreState) {
        val current = getLatestState()
        val updated = transform(current).selfHealed()
        _stateFlow.value = updated
        scope.launch(ioDispatcher) {
            try {
                dataStore.updateData { updated }
            } catch (e: Exception) {
                // Fail-safe: an unwritable store must not take the app down.
            }
        }
    }
}

/**
 * JSON serializer for [ServerStoreState].
 *
 * `OkioSerializer` rather than `Serializer`: the multiplatform storage backends
 * (`OkioStorage`, `WebLocalStorage`, `WebOpfsStorage`) all take an okio-based
 * serializer, so this is what lets one `ServerStore` run on every target.
 */
object ServerStoreSerializer : OkioSerializer<ServerStoreState> {
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }

    override val defaultValue: ServerStoreState =
        ServerStoreState(baseUrl = ServerEndpoint.DEFAULT_BASE_URL)

    override suspend fun readFrom(source: BufferedSource): ServerStoreState =
        try {
            json.decodeFromString(
                ServerStoreState.serializer(),
                source.readUtf8(),
            )
        } catch (e: Exception) {
            defaultValue
        }

    override suspend fun writeTo(
        t: ServerStoreState,
        sink: BufferedSink,
    ) {
        sink.writeUtf8(json.encodeToString(ServerStoreState.serializer(), t))
    }
}

/**
 * The first value of [flow], or [fallback] when the platform cannot block or the
 * read fails.
 *
 * Android and the desktop block (the store is read during app start and callers
 * expect the persisted value immediately); wasmJs has no blocking primitive and
 * returns [fallback], which the store's own launch replaces on first emission.
 */
internal expect fun <T> firstBlocking(
    flow: Flow<T>,
    fallback: T,
): T
