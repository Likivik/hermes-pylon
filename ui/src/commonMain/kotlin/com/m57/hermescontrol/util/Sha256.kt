package com.m57.hermescontrol.util

/**
 * SHA-256 of [input] as lowercase hex.
 *
 * `java.security.MessageDigest` is JVM-only, so this implements FIPS 180-4 directly.
 * It is used to fingerprint cache keys and media paths — not for secrets.
 */
fun sha256Hex(input: String): String = sha256Hex(input.encodeToByteArray())

// Round constants: fractional parts of the cube roots of the first 64 primes.
private val K =
    intArrayOf(
        1116352408,
        1899447441,
        -1245643825,
        -373957723,
        961987163,
        1508970993,
        -1841331548,
        -1424204075,
        -670586216,
        310598401,
        607225278,
        1426881987,
        1925078388,
        -2132889090,
        -1680079193,
        -1046744716,
        -459576895,
        -272742522,
        264347078,
        604807628,
        770255983,
        1249150122,
        1555081692,
        1996064986,
        -1740746414,
        -1473132947,
        -1341970488,
        -1084653625,
        -958395405,
        -710438585,
        113926993,
        338241895,
        666307205,
        773529912,
        1294757372,
        1396182291,
        1695183700,
        1986661051,
        -2117940946,
        -1838011259,
        -1564481375,
        -1474664885,
        -1035236496,
        -949202525,
        -778901479,
        -694614492,
        -200395387,
        275423344,
        430227734,
        506948616,
        659060556,
        883997877,
        958139571,
        1322822218,
        1537002063,
        1747873779,
        1955562222,
        2024104815,
        -2067236844,
        -1933114872,
        -1866530822,
        -1538233109,
        -1090935817,
        -965641998,
    )

// Initial hash: fractional parts of the square roots of the first 8 primes.
private const val H0 = 1779033703
private const val H1 = -1150833019
private const val H2 = 1013904242
private const val H3 = -1521486534
private const val H4 = 1359893119
private const val H5 = -1694144372
private const val H6 = 528734635
private const val H7 = 1541459225

fun sha256Hex(input: ByteArray): String {
    val bitLength = input.size.toLong() * 8
    val padded =
        input.copyOf(((input.size + 9 + 63) / 64) * 64).also { buf ->
            buf[input.size] = 0x80.toByte()
            for (i in 0 until 8) {
                buf[buf.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
            }
        }

    var h0 = H0
    var h1 = H1
    var h2 = H2
    var h3 = H3
    var h4 = H4
    var h5 = H5
    var h6 = H6
    var h7 = H7

    val w = IntArray(64)
    var chunk = 0
    while (chunk < padded.size) {
        for (i in 0 until 16) {
            val j = chunk + i * 4
            w[i] =
                ((padded[j].toInt() and 0xff) shl 24) or
                ((padded[j + 1].toInt() and 0xff) shl 16) or
                ((padded[j + 2].toInt() and 0xff) shl 8) or
                (padded[j + 3].toInt() and 0xff)
        }
        for (i in 16 until 64) {
            val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
            val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }

        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4
        var f = h5
        var g = h6
        var h = h7

        for (i in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = h + s1 + ch + K[i] + w[i]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj

            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }

        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
        h5 += f
        h6 += g
        h7 += h

        chunk += 64
    }

    return listOf(h0, h1, h2, h3, h4, h5, h6, h7).joinToString("") { word ->
        word.toUInt().toString(16).padStart(8, '0')
    }
}

private fun Int.rotateRight(bits: Int): Int = (this ushr bits) or (this shl (32 - bits))
