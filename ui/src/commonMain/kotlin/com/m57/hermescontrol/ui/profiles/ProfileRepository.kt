package com.m57.hermescontrol.ui.profiles

import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.util.HermesLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

/**
 * The single writer of "which profile this app acts on".
 *
 * The value itself lives in the client, because the transport needs it to scope every request.
 * This class is the only thing allowed to change it — nothing else calls
 * [ApiClient.setSelectedProfile] — and it adds the one piece of state the client cannot own:
 * what the gateway process was last *observed* to serve.
 *
 * `current` is a snapshot, not a promise: it never follows a selection on its own, so the value
 * here is timestamped and refreshed on events (gateway change, screen open), never assumed.
 */
class ProfileRepository(
    /** Reads what the gateway process serves (`/api/profiles/active` → `current`). */
    private val fetchServing: suspend () -> String? = { ApiClient.currentProfileName() },
    /** Writes the selection; the client stores it because the transport scopes every request. */
    private val apply: (String?) -> Unit = { ApiClient.setSelectedProfile(it) },
    /** The selection as a value stream. */
    selection: StateFlow<String?> = ApiClient.profileFlow,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) {
    /** Timestamped because it is evidence, not truth; millis since this repository started. */
    data class Observed(val profile: String?, val atMillis: Long)

    private val origin = TimeSource.Monotonic.markNow()
    private val _lastObservedCurrent = MutableStateFlow<Observed?>(null)

    /** The selection, as every screen should read it. */
    val selected: StateFlow<String?> = selection

    /** What the gateway process was last seen to serve. Null until observed. */
    val lastObservedCurrent: StateFlow<Observed?> = _lastObservedCurrent.asStateFlow()

    /** True when a selection exists and the gateway was observed serving something else. */
    fun isDivergent(): Boolean {
        val chosen = selected.value ?: return false
        val observed = _lastObservedCurrent.value?.profile ?: return false
        return chosen != observed
    }

    /** Choose a profile for this app. The gateway's preference is a separate call. */
    fun select(profile: String?) {
        apply(profile?.takeIf { it.isNotBlank() })
    }

    /** Forget the selection — a profile name that exists on one gateway need not exist on the next. */
    fun clear() = select(null)

    /**
     * Re-read what the gateway serves. Cheap and single-flight by construction: callers
     * await it, and a failure keeps the previous observation rather than inventing one.
     */
    suspend fun refreshCurrent(): Observed? {
        val name =
            try {
                withContext(io) { fetchServing() }
            } catch (e: Exception) {
                // A snapshot we could not refresh is stale, not wrong: keep the last one.
                HermesLog.e("ProfileRepository", "Could not read the serving profile", e)
                return _lastObservedCurrent.value
            }
        if (name != null) {
            _lastObservedCurrent.value = Observed(name, origin.elapsedNow().inWholeMilliseconds)
        }
        return _lastObservedCurrent.value
    }

    /**
     * The profile a request should be scoped to: the selection, resolved once if nothing is
     * selected yet (a cold start has no persisted choice in this process).
     */
    suspend fun resolve(): String? = selected.value ?: refreshCurrent()?.profile?.also { select(it) }
}

/** One repository for the app process. */
object ProfileScope {
    val repository: ProfileRepository by lazy { ProfileRepository() }
}
