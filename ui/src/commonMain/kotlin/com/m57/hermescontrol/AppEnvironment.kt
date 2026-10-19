package com.m57.hermescontrol

import com.m57.hermescontrol.data.local.AuthStore
import com.m57.hermescontrol.data.local.PreferencesStore
import com.m57.hermescontrol.data.local.authStore
import com.m57.hermescontrol.util.AppClock
import com.m57.hermescontrol.util.SystemClock
import com.m57.hermescontrol.util.ioDispatcher
import kotlin.concurrent.Volatile

/**
 * The one place a ViewModel's platform services come from.
 *
 * Before this existed every ViewModel defaulted each dependency separately
 * (`ioDispatcher`, `authStore`, `nowMillis()`, `BuildFlags`), so there were fifteen
 * slightly different answers to "where does this come from" — and a single wrong
 * default (an eagerly-captured `Dispatchers.IO`) silently defeated the whole test
 * suite. Defaults now read from one swappable value, and tests replace it once:
 *
 * ```
 * AppEnvironment.current = AppEnvironment.test(io = testDispatcher, clock = FixedClock(0))
 * ```
 *
 * [current] stays mutable on purpose: constructor defaults cannot take parameters,
 * so this is how a test run or a platform entry point supplies its own values
 * without threading them through every call site. Production installs it once at
 * start-up via [install].
 */
class AppEnvironment(
    val io: kotlinx.coroutines.CoroutineDispatcher = ioDispatcher,
    val auth: AuthStore = authStore,
    val clock: AppClock = SystemClock,
    val preferences: PreferencesStore? = null,
) {
    companion object {
        @Volatile
        private var installed: AppEnvironment? = null

        /**
         * Not a field: constructing the default eagerly would capture
         * `Dispatchers.IO` at class-init, which is *before* a test's
         * `mockkStatic(Dispatchers::class)` runs — the mock would then be
         * bypassed and every coroutine would hop onto a real thread pool,
         * defeating `advanceUntilIdle()`. Deferring to first access keeps the
         * value correct in production and replaceable in tests.
         *
         * Deliberately not memoised: a cached instance outlives a test's
         * `runTest` block and would hand the next test a closed dispatcher.
         * Construction is a handful of field reads.
         */
        val current: AppEnvironment
            get() = installed ?: AppEnvironment()

        fun install(environment: AppEnvironment) {
            installed = environment
        }
    }
}
