package com.m57.hermescontrol.data.local

/**
 * Browser binding, the same shape as the Android one: every call delegates to
 * [AuthManager], which owns the server store, the secret store and the cookie
 * platform supplied by [WebAuthPlatform].
 *
 * An earlier attempt kept its own `InMemoryAuthStore` and persisted a copy to
 * web storage. That was a dead end: `AuthManager.getToken()` reads the secret
 * store, so a token written into a detached store was invisible on the boot
 * path and every reload produced the login screen. Delegation leaves one owner,
 * and what the login writes is what the boot reads.
 */
actual val authStore: AuthStore = AuthManagerAuthStore
