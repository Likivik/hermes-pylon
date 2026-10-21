package com.m57.hermescontrol.data.local

/** Desktop/web binding: an in-memory store, nothing persisted. */
actual val authStore: AuthStore = InMemoryAuthStore()
