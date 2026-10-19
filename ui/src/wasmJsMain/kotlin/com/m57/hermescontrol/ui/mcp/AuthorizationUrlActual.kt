package com.m57.hermescontrol.ui.mcp

import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """(raw) => {
        try {
            const u = new URL(raw);
            if (u.protocol !== 'https:') return false;
            if (!u.hostname) return false;
            if (u.username || u.password) return false;
            return true;
        } catch (e) {
            return false;
        }
    }""",
)
private external fun jsIsSafeHttpsUrl(raw: String): Boolean

actual fun isSafeHttpsAuthorizationUrl(raw: String): Boolean = jsIsSafeHttpsUrl(raw)
