// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.data.remote

import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * Whether [ServerEndpoint.parse] should accept plain-HTTP base URLs.
 *
 * The default production policy is [DENY] — a cleartext URL is rejected
 * with an [IllegalArgumentException] that mentions `"Cleartext HTTP"`. The
 * development policy [ALLOW_WITH_WARNING] accepts it and surfaces a
 * non-null [ServerEndpoint.securityWarning] so the UI can show a banner.
 */
enum class CleartextPolicy { DENY, ALLOW_WITH_WARNING }

/**
 * Immutable, normalised base URL for a Hermes server. The previous
 * implementation exposed OkHttp 3's [okhttp3.HttpUrl], which is jvm-only
 * and cannot be shared from a KMP `commonMain` source set. The new
 * implementation is a thin wrapper that:
 *
 * - uses Ktor's multiplatform [Url] parser as a building block for the
 *   actual RFC 3986 parsing (scheme, host, port, path, query, fragment);
 * - exposes the small surface the rest of the app needs (`host`, `port`,
 *   `isHttps`, `encodedPath`, `newBuilder()`);
 * - re-emits the canonical wire form on [toString] with the trailing slash
 *   that [ServerEndpoint.parse] guarantees.
 *
 * The [host] field is stored without the surrounding `[` / `]` brackets that
 * Ktor (and RFC 3986) require for IPv6 literals — the brackets are
 * re-inserted in [toString] so the wire form round-trips. This matches the
 * shape OkHttp exposed.
 */
class ServerBaseUrl internal constructor(
    val protocol: URLProtocol,
    val host: String,
    val port: Int,
    val encodedPath: String,
) {
    val isHttps: Boolean
        get() = protocol == URLProtocol.HTTPS

    /** Serialise back to the canonical wire form, e.g. `https://host:9119/prefix/`. */
    override fun toString(): String =
        buildString {
            append(protocol.name)
            append("://")
            if (':' in host) append('[').append(host).append(']') else append(host)
            if (port != 0 && port != protocol.defaultPort) {
                append(':')
                append(port)
            }
            val path = if (encodedPath.startsWith('/')) encodedPath else "/$encodedPath"
            append(path)
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ServerBaseUrl) return false
        return protocol == other.protocol &&
            host == other.host &&
            port == other.port &&
            encodedPath == other.encodedPath
    }

    override fun hashCode(): Int {
        var result = protocol.hashCode()
        result = 31 * result + host.hashCode()
        result = 31 * result + port
        result = 31 * result + encodedPath.hashCode()
        return result
    }

    /**
     * Fluent setter style so the call sites that previously read like
     * `endpoint.baseUrl.newBuilder().host(...).port(...).build()` keep
     * compiling under the Ktor-backed wrapper. Mirrors the OkHttp
     * HttpUrl.Builder / Ktor URLBuilder fluent API.
     */
    class Builder internal constructor(initial: ServerBaseUrl) {
        private var protocol: URLProtocol = initial.protocol
        private var host: String = initial.host
        private var port: Int = initial.port
        private var encodedPath: String = initial.encodedPath

        fun protocol(value: URLProtocol): Builder = apply { protocol = value }

        fun host(value: String): Builder = apply { host = value }

        fun port(value: Int): Builder = apply { port = value }

        fun encodedPath(value: String): Builder = apply { encodedPath = value }

        fun build(): ServerBaseUrl = ServerBaseUrl(protocol, host, port, encodedPath)
    }

    fun newBuilder(): Builder = Builder(this)

    /**
     * Resolve a path below this endpoint's proxy prefix. Accepts a path with
     * or without a leading `/`. The result preserves the encoded path exactly
     * (no further normalisation) so call sites that want a trailing slash
     * can still append one themselves.
     */
    fun resolve(relativePath: String): ServerBaseUrl {
        val base = if (encodedPath.endsWith('/')) encodedPath else "$encodedPath/"
        val tail = if (relativePath.startsWith('/')) relativePath.drop(1) else relativePath
        val merged = base + tail
        return ServerBaseUrl(protocol, host, port, merged)
    }
}

/**
 * Validate and normalise a user-supplied base URL.
 *
 * The raw value is first handed to Ktor's [Url] parser, which gives us RFC
 * 3986 parsing for free (scheme, host, port, IPv6 literals, percent-encoded
 * paths). The parser also rejects the inputs the caller cares about
 * (empty, unknown scheme, missing host) by throwing — we map those to
 * [IllegalArgumentException] so the call-site contract is uniform.
 */
class ServerEndpoint internal constructor(
    val baseUrl: ServerBaseUrl,
    val securityWarning: String?,
) {
    /**
     * Construct a WebSocket URL for the current auth mode.
     *
     * @param authParameter `"token"` (default) or `"ticket"`; any other value
     *   is rejected with [IllegalArgumentException] to prevent accidental
     *   leakage via the wrong query key.
     * @param credential the secret to embed. Encoded with RFC 3986
     *   percent-encoding (spaces become `%20`, not `+`).
     */
    fun webSocketUrl(
        authParameter: String,
        credential: String,
    ): String {
        require(authParameter == "token" || authParameter == "ticket") {
            "Unsupported WebSocket authentication parameter"
        }
        val socketScheme = if (baseUrl.isHttps) "wss" else "ws"
        val httpUrl = baseUrl.resolve("api/ws")
        return buildString {
            append(socketScheme)
            append("://")
            if (':' in httpUrl.host) append('[').append(httpUrl.host).append(']') else append(httpUrl.host)
            if (httpUrl.port != 0 && httpUrl.port != httpUrl.protocol.defaultPort) {
                append(':')
                append(httpUrl.port)
            }
            append(httpUrl.encodedPath)
            append('?')
            append(authParameter)
            append('=')
            append(percentEncodeRfc3986(credential))
        }
    }

    /**
     * Compatibility hook that the WS client uses to derive a request path
     * below the reverse-proxy prefix. No production caller passes an
     * `HttpUrl` (jvm-only) into this any more; it now takes the multiplatform
     * [ServerBaseUrl] so the call site compiles from `:app` without leaking
     * OkHttp types.
     */
    fun relativeRequestPath(requestUrl: ServerBaseUrl): String {
        if (!sameOrigin(requestUrl)) return requestUrl.encodedPath
        val prefix = baseUrl.encodedPath
        if (!requestUrl.encodedPath.startsWith(prefix)) {
            return requestUrl.encodedPath
        }
        return "/" + requestUrl.encodedPath.removePrefix(prefix)
    }

    private fun sameOrigin(other: ServerBaseUrl): Boolean =
        baseUrl.protocol == other.protocol &&
            baseUrl.host == other.host &&
            baseUrl.port == other.port

    companion object {
        const val DEFAULT_BASE_URL = "https://127.0.0.1:9119/"
        const val CLEARTEXT_WARNING =
            "Cleartext HTTP exposes credentials and messages. " +
                "Use HTTPS unless this is a trusted development network."

        fun parse(
            rawValue: String,
            cleartextPolicy: CleartextPolicy = CleartextPolicy.DENY,
        ): ServerEndpoint {
            val raw = rawValue.trim()
            require(raw.isNotEmpty()) { "Base URL is required" }
            require(
                raw.startsWith("https://", ignoreCase = true) ||
                    raw.startsWith("http://", ignoreCase = true),
            ) { "Base URL must use https:// or http://" }

            // Ktor's parser substitutes `localhost` when the authority is
            // absent (`Url("https://").host == "localhost"`), so a
            // post-parse host check cannot tell `https://` from a real
            // loopback URL. Validate the authority on the raw string
            // instead: everything between the scheme separator and the
            // first path/query/fragment delimiter.
            val authority =
                raw.substringAfter("://").takeWhile { it != '/' && it != '?' && it != '#' }
            require(authority.isNotBlank()) { "Base URL is missing a host" }

            val parsed: Url =
                try {
                    Url(raw)
                } catch (_: IllegalArgumentException) {
                    throw IllegalArgumentException("Malformed base URL")
                } catch (_: IllegalStateException) {
                    throw IllegalArgumentException("Malformed base URL")
                }

            require(parsed.host.isNotEmpty()) { "Base URL is missing a host" }

            require(parsed.encodedUser.isNullOrEmpty() && parsed.password == null) {
                "Base URL must not contain credentials"
            }
            require(parsed.encodedQuery.isEmpty()) {
                "Base URL must not contain a query"
            }
            require(parsed.fragment.isEmpty()) {
                "Base URL must not contain a fragment"
            }

            val baseUrl = toServerBaseUrl(parsed)

            if (!baseUrl.isHttps && cleartextPolicy == CleartextPolicy.DENY) {
                throw IllegalArgumentException(
                    "Cleartext HTTP is disabled in this build. Use HTTPS.",
                )
            }
            val warning = if (baseUrl.isHttps) null else CLEARTEXT_WARNING
            return ServerEndpoint(baseUrl, warning)
        }

        /**
         * Preserve the pre-migration HTTP behaviour for an existing install.
         * Used by code paths that still hand around the legacy `host` /
         * `port` pair (e.g. the bot configuration on first launch).
         */
        fun fromLegacy(
            host: String,
            port: Int,
        ): ServerEndpoint {
            require(port in 1..65535) { "Legacy port is out of range" }
            val normalizedHost =
                host.trim().removePrefix("[").removeSuffix("]")
            val legacy =
                ServerBaseUrl(
                    protocol = URLProtocol.HTTP,
                    host = normalizedHost,
                    port = port,
                    encodedPath = "/",
                )
            return ServerEndpoint(legacy, CLEARTEXT_WARNING)
        }

        /**
         * Drop a credential query parameter from a WebSocket URL before
         * logging it. Replaces the OkHttp 3 implementation that ran
         * `newBuilder().query(null)`. The new implementation works on the
         * raw `String` form (multiplatform, no OkHttp), recognising the
         * `wss://` and `ws://` schemes and stripping everything from the
         * first `?` onward.
         */
        fun redactWebSocketUrlForLog(rawUrl: String): String {
            val socketScheme =
                when {
                    rawUrl.startsWith("wss://", ignoreCase = true) -> "wss"
                    rawUrl.startsWith("ws://", ignoreCase = true) -> "ws"
                    else -> return "<invalid-websocket-url>"
                }
            val queryStart = rawUrl.indexOf('?')
            val withoutQuery = if (queryStart >= 0) rawUrl.substring(0, queryStart) else rawUrl
            // Validate by re-parsing the http(s) form.
            val httpForm =
                when (socketScheme) {
                    "wss" -> withoutQuery.replaceFirst("wss://", "https://", ignoreCase = true)
                    else -> withoutQuery.replaceFirst("ws://", "http://", ignoreCase = true)
                }
            try {
                Url(httpForm)
            } catch (_: IllegalArgumentException) {
                return "<invalid-websocket-url>"
            } catch (_: IllegalStateException) {
                return "<invalid-websocket-url>"
            }
            return withoutQuery
        }
    }
}

private fun toServerBaseUrl(parsed: Url): ServerBaseUrl {
    val rawHost = parsed.host
    val host =
        if (rawHost.length >= 2 && rawHost.startsWith('[') && rawHost.endsWith(']')) {
            rawHost.substring(1, rawHost.length - 1)
        } else {
            rawHost
        }
    val protocol = parsed.protocol
    val port = parsed.port
    val path =
        when {
            parsed.encodedPath.isEmpty() -> "/"
            parsed.encodedPath.endsWith('/') -> parsed.encodedPath
            else -> parsed.encodedPath + "/"
        }
    return ServerBaseUrl(protocol, host, port, path)
}

/**
 * RFC 3986 percent-encode a query-parameter value. The set of unreserved
 * characters is the union of letters, digits, `-`, `_`, `.`, `~`; every
 * other byte is encoded as `%XX` (uppercase hex). This is intentionally
 * different from the form-encoding that Ktor's `parameters` builder uses
 * (which would turn a space into `+`); the WS ticket / token wire format
 * expects `%20`.
 */
private fun percentEncodeRfc3986(value: String): String {
    if (value.isEmpty()) return value
    val sb = StringBuilder(value.length)
    for (b in value.encodeToByteArray()) {
        val isUnreserved =
            (b in 'a'.code.toByte()..'z'.code.toByte()) ||
                (b in 'A'.code.toByte()..'Z'.code.toByte()) ||
                (b in '0'.code.toByte()..'9'.code.toByte()) ||
                b == '-'.code.toByte() ||
                b == '_'.code.toByte() ||
                b == '.'.code.toByte() ||
                b == '~'.code.toByte()
        if (isUnreserved) {
            sb.append(b.toInt().toChar())
        } else {
            sb.append('%')
            sb.append(HEX[(b.toInt() ushr 4) and 0x0f])
            sb.append(HEX[b.toInt() and 0x0f])
        }
    }
    return sb.toString()
}

private val HEX = "0123456789ABCDEF".toCharArray()
