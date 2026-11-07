package com.m57.hermescontrol.data.local

/**
 * Desktop binding: every call reaches [AuthManager], which owns the server store,
 * the secret store and the cookie platform supplied by [JvmAuthPlatform].
 * The 44 delegating members live once, in [AuthManagerAuthStore].
 */
actual val authStore: AuthStore = AuthManagerAuthStore
