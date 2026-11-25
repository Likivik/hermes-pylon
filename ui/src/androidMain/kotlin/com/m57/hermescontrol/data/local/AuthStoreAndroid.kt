package com.m57.hermescontrol.data.local

/**
 * Android binding: every call reaches [AuthManager], which owns the encrypted
 * store, the server store and the cookie jar.
 * The 44 delegating members live once, in [AuthManagerAuthStore].
 */
actual val authStore: AuthStore = AuthManagerAuthStore
