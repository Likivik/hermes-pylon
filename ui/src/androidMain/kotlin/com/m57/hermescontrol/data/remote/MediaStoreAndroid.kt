@file:Suppress("ktlint:standard:filename")

package com.m57.hermescontrol.data.remote

import java.io.File

/**
 * Android binding: delegates to [GatewayFileClient], which owns the on-disk media
 * cache and the credential-bound streaming reads.
 */
actual val mediaStore: MediaStore =
    object : MediaStore {
        override fun normalizePath(raw: String): String? = GatewayFileClient.normalizePath(raw)

        override fun sha(value: String): String = GatewayFileClient.sha256(value)

        override suspend fun fetch(
            path: String,
            cacheDir: String,
        ): MediaResult =
            when (val result = GatewayFileClient.fetch(path, File(cacheDir))) {
                is GatewayFileResult.Success ->
                    MediaResult.LocalFile(
                        name = result.file.name,
                        mimeType = result.file.mimeType,
                        path = result.file.cacheFile.absolutePath,
                    )

                GatewayFileResult.NotFound -> MediaResult.NotFound
                GatewayFileResult.Forbidden -> MediaResult.Forbidden
                GatewayFileResult.TooLarge -> MediaResult.TooLarge
                GatewayFileResult.Unauthorized -> MediaResult.Unauthorized
                is GatewayFileResult.Failure -> MediaResult.Failure(result.throwable)
            }
    }
