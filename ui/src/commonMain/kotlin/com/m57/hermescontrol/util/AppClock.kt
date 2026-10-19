package com.m57.hermescontrol.util

import com.m57.hermescontrol.util.monotonicMillis as platformMonotonicMillis
import com.m57.hermescontrol.util.nowMillis as platformNowMillis

/** Wall-clock time, injectable so a test can pin "now". */
interface AppClock {
    fun nowMillis(): Long

    fun monotonicMillis(): Long
}

/**
 * The production clock.
 *
 * The delegates are aliased: an unqualified `nowMillis()` inside `nowMillis()`
 * would resolve to the member and recurse forever, and it would compile.
 */
object SystemClock : AppClock {
    override fun nowMillis(): Long = platformNowMillis()

    override fun monotonicMillis(): Long = platformMonotonicMillis()
}
