package com.m57.hermescontrol.ui.common

/**
 * Pure request/kind models for opening gateway-hosted media.
 *
 * Split out of SecureGatewayMedia.kt, which is Android-only (it holds
 * MediaDataSource / URLConnection readers). These two types are plain data and
 * are referenced from chat UI state, so they must be multiplatform.
 */
enum class SecureGatewayMediaKind {
    AUDIO,
    VIDEO,
}

data class SecureGatewayMediaRequest(
    val path: String,
    val title: String,
    val mimeType: String,
    val kind: SecureGatewayMediaKind,
) {
    val isVideo: Boolean = kind == SecureGatewayMediaKind.VIDEO
}
