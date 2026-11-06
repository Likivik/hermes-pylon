package com.m57.hermescontrol.data.local

/**
 * Browser binding: settings and profiles persist in `localStorage`, secrets in
 * `sessionStorage`. See [WebAuthStore].
 */
actual val authStore: AuthStore = WebAuthStore()
