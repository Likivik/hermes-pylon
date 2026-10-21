package com.m57.hermescontrol.ui.media

/**
 * Minimal RFC 4648 base64 decoder.
 *
 * There is no common Kotlin base64: `java.util.Base64` is JVM-only and
 * `kotlin.io.encoding.Base64` is opt-in and not available on every target this
 * module compiles for. A data-URL payload is the only thing that needs it, so
 * the twenty lines live here rather than behind another expect/actual.
 *
 * Padding is optional and whitespace is ignored; invalid input throws.
 */
internal fun base64Decode(text: String): ByteArray {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val out = ArrayList<Byte>(text.length * 3 / 4)
    var buffer = 0
    var bits = 0
    for (char in text) {
        if (char == '=' || char == '\n' || char == '\r' || char == ' ' || char == '\t') continue
        val value = alphabet.indexOf(char)
        require(value >= 0) { "Invalid base64 character: $char" }
        buffer = (buffer shl 6) or value
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out.add(((buffer shr bits) and 0xFF).toByte())
        }
    }
    return out.toByteArray()
}
