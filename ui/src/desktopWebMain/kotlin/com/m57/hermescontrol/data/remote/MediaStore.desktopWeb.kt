package com.m57.hermescontrol.data.remote

import com.m57.hermescontrol.util.GatewayPath
import com.m57.hermescontrol.util.sha256Hex
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes

/**
 * Desktop streams the artifact into memory.
 *
 * There is no media cache to speak of off Android, so [cacheDir] is ignored and the
 * artifact comes back as bytes. Callers that only need to display it hand the bytes
 * to the decoder instead of a path.
 */
actual val mediaStore: MediaStore =
    object : MediaStore {
        override fun normalizePath(raw: String): String? = GatewayPath.normalizePath(raw)

        override fun sha(value: String): String = sha256Hex(value)

        override suspend fun fetch(
            path: String,
            cacheDir: String,
        ): MediaResult =
            runCatching {
                val response = createHermesHttpClient().get(path)
                if (response.status.value !in 200..299) {
                    return@runCatching when (response.status.value) {
                        401 -> MediaResult.Unauthorized
                        403 -> MediaResult.Forbidden
                        404 -> MediaResult.NotFound
                        else -> MediaResult.Failure(IllegalStateException("HTTP ${response.status.value}"))
                    }
                }
                MediaResult.Bytes(
                    name = path.substringAfterLast('/'),
                    mimeType = response.headers["Content-Type"] ?: "application/octet-stream",
                    data = response.bodyAsBytes(),
                )
            }.getOrElse { MediaResult.Failure(it) }
    }
