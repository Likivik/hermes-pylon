package com.m57.hermescontrol.ui

/**
 * Which runtime is executing this test.
 *
 * Two `:ui` tests assert things the browser test harness cannot yet support (see the
 * call sites for the exact failure). Skipping them silently would be worse than the
 * bug: this flag makes each skip explicit, greppable, and tied to a reason, while the
 * rest of the suite still executes in Chrome.
 */
expect fun isWebTestTarget(): Boolean
