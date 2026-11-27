package com.m57.hermescontrol.ui.mcp

import java.net.URI

actual fun isSafeHttpsAuthorizationUrl(raw: String): Boolean {
    // inventory: intentional-ignore — no authorization URL means the flow reports unavailable
    val uri = runCatching { URI(raw) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    if (uri.host.isNullOrBlank() || uri.userInfo != null) return false
    return true
}
