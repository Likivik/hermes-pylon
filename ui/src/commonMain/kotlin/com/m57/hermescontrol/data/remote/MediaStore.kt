package com.m57.hermescontrol.data.remote

/**
 * Result of asking the gateway for a media artifact.
 *
 * [LocalFile] and [Bytes] are both "success" but differ by platform capability:
 * Android keeps a disk cache and hands back a path Coil can load; desktop and web
 * have no meaningful media cache and hand back the bytes.
 */
sealed interface MediaResult {
    data class LocalFile(
        val name: String,
        val mimeType: String,
        val path: String,
    ) : MediaResult

    data class Bytes(
        val name: String,
        val mimeType: String,
        val data: ByteArray,
    ) : MediaResult {
        override fun equals(other: Any?): Boolean =
            other is Bytes && other.name == name && other.mimeType == mimeType && other.data.contentEquals(data)

        override fun hashCode(): Int = (name.hashCode() * 31 + mimeType.hashCode()) * 31 + data.contentHashCode()
    }

    data object NotFound : MediaResult

    data object Forbidden : MediaResult

    data object TooLarge : MediaResult

    data object Unauthorized : MediaResult

    data class Failure(val throwable: Throwable) : MediaResult
}

/**
 * Fetching gateway media (attachments, generated files) without naming a
 * filesystem or an HTTP library.
 *
 * [cacheDir] is a hint, not a contract: Android uses it as the on-disk cache root,
 * other targets treat it as a cache namespace and keep bytes in memory.
 */
interface MediaStore {
    /** Normalises a gateway path, or null when it is not a valid media path. */
    fun normalizePath(raw: String): String?

    /** Stable content fingerprint used for cache file names. */
    fun sha(value: String): String

    suspend fun fetch(
        path: String,
        cacheDir: String,
    ): MediaResult
}

/** The active media store for this target. */
expect val mediaStore: MediaStore
