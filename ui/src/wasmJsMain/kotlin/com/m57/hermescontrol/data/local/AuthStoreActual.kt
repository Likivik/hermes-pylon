package com.m57.hermescontrol.data.local

/**
 * Browser binding, the same shape as the Android and desktop ones: every call
 * reaches [AuthManager], which owns the server store and the secret store.
 * Persistence is the platform's job — [WebAuthPlatform] writes to web storage —
 * so what the login writes is what the boot path reads.
 */
actual val authStore: AuthStore = AuthManagerAuthStore
