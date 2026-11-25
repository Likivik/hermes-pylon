package com.m57.hermescontrol.data.remote

import com.m57.hermescontrol.data.local.AuthSessionState
import com.m57.hermescontrol.util.ioDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import java.io.IOException

/**
 * Streaming transport for byte-range reads of managed files.
 *
 * Ktor owns this path now: one client per credential boundary, a `Range`
 * header, and a bounded body read. The seam returns plain data
 * ([GatewayRangeResponse]) rather than an engine response, so nothing below it
 * knows or cares which engine produced it.
 */
interface GatewayRangeRequester {
    suspend fun streamRange(
        path: String,
        range: String,
    ): GatewayRangeResponse
}

/** A bounded range response: status, the headers the parser needs, and the bytes. */
data class GatewayRangeResponse(
    val status: Int,
    val contentRange: String?,
    val contentType: String?,
    val body: ByteArray,
)

/**
 * Production implementation: `Range` request against [endpoint], dispatched on
 * [client]. The path is a URL-form-encoded *query parameter* to match the
 * previous Retrofit endpoint (`api/files/stream?path=…`).
 */
class KtorGatewayRangeRequester(
    private val endpoint: ServerEndpoint,
    private val client: HttpClient,
) : GatewayRangeRequester {
    override suspend fun streamRange(
        path: String,
        range: String,
    ): GatewayRangeResponse {
        val base = endpoint.baseUrl.resolve("api/files/stream").toString()
        val url = base + "?path=" + path.encodeURLParameter(spaceToPlus = true)
        val response =
            client.get(url) {
                header(HttpHeaders.Range, range)
            }
        val bytes =
            response.bodyAsChannel()
                .readRemaining(MAX_IN_MEMORY_MEDIA_BYTES)
                .readByteArray()
        return GatewayRangeResponse(
            status = response.status.value,
            contentRange = response.headers[HttpHeaders.ContentRange],
            contentType = response.headers[HttpHeaders.ContentType],
            body = bytes,
        )
    }
}

fun interface SeekableGatewayMediaReader {
    suspend fun read(
        position: Long,
        byteCount: Int,
    ): GatewayMediaRangeResult

    fun isCurrent(): Boolean = true

    companion object {
        const val MAX_RANGE_BYTES = 1024 * 1024
    }
}

class SeekableGatewayMediaSession(
    rawPath: String,
    private val rangeRequester: GatewayRangeRequester,
    private val scope: MediaCacheScope,
    private val currentScope: () -> MediaCacheScope,
) : SeekableGatewayMediaReader {
    private val path = GatewayFileClient.normalizePath(rawPath)

    override suspend fun read(
        position: Long,
        byteCount: Int,
    ): GatewayMediaRangeResult {
        if (path == null) {
            return GatewayMediaRangeResult.Failure(
                IllegalArgumentException("not an absolute gateway path"),
            )
        }
        if (position < 0 || byteCount !in 1..SeekableGatewayMediaReader.MAX_RANGE_BYTES) {
            return GatewayMediaRangeResult.Failure(IllegalArgumentException("invalid media byte range"))
        }
        if (!isCurrent()) return GatewayMediaRangeResult.Stale

        val end =
            runCatching { Math.addExact(position, byteCount.toLong() - 1L) }
                .getOrElse {
                    return GatewayMediaRangeResult.Failure(
                        IllegalArgumentException("media byte range overflow"),
                    )
                }
        return withContext(ioDispatcher) {
            try {
                val response = rangeRequester.streamRange(path, "bytes=$position-$end")
                if (!isCurrent()) {
                    GatewayMediaRangeResult.Stale
                } else {
                    response.toRangeResult(position, byteCount)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Throwable) {
                GatewayMediaRangeResult.Failure(e)
            }
        }
    }

    override fun isCurrent(): Boolean = currentScope() == scope

    private fun GatewayRangeResponse.toRangeResult(
        requestedPosition: Long,
        requestedCount: Int,
    ): GatewayMediaRangeResult =
        when (status) {
            401 -> {
                AuthSessionState.requireSignIn()
                GatewayMediaRangeResult.Unauthorized
            }

            403 -> GatewayMediaRangeResult.Forbidden
            404 -> GatewayMediaRangeResult.NotFound
            413 -> GatewayMediaRangeResult.TooLarge
            206 -> toRangeSuccess(requestedPosition, requestedCount)
            else -> GatewayMediaRangeResult.Failure(IOException("HTTP $status"))
        }

    private fun GatewayRangeResponse.toRangeSuccess(
        requestedPosition: Long,
        requestedCount: Int,
    ): GatewayMediaRangeResult {
        if (body.size.toLong() > requestedCount.toLong()) {
            return GatewayMediaRangeResult.TooLarge
        }
        val match =
            contentRange?.let { CONTENT_RANGE.matchEntire(it.trim()) }
                ?: return GatewayMediaRangeResult.Failure(IOException("Invalid Content-Range"))
        val start = match.groupValues[1].toLongOrNull()
        val end = match.groupValues[2].toLongOrNull()
        if (start != requestedPosition || end == null || end < start || end - start + 1L != body.size.toLong()) {
            return GatewayMediaRangeResult.Failure(IOException("Mismatched Content-Range"))
        }
        val totalLength = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull()
        if (totalLength != null && totalLength <= end) {
            return GatewayMediaRangeResult.Failure(IOException("Mismatched Content-Range total"))
        }
        val mimeType = contentType?.substringBefore(';')?.trim()?.takeIf(String::isNotBlank)
        return GatewayMediaRangeResult.Success(body, totalLength, mimeType)
    }

    companion object {
        private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)", RegexOption.IGNORE_CASE)
    }
}

sealed interface GatewayMediaRangeResult {
    data class Success(
        val bytes: ByteArray,
        val totalLength: Long?,
        val mimeType: String?,
    ) : GatewayMediaRangeResult

    data object Stale : GatewayMediaRangeResult

    data object Unauthorized : GatewayMediaRangeResult

    data object Forbidden : GatewayMediaRangeResult

    data object NotFound : GatewayMediaRangeResult

    data object TooLarge : GatewayMediaRangeResult

    data class Failure(val throwable: Throwable) : GatewayMediaRangeResult
}
