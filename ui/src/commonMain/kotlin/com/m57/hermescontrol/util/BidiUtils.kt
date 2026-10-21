package com.m57.hermescontrol.util

import androidx.compose.ui.unit.LayoutDirection
// ── Portable bidi classification ────────────────────────────────────────
// `java.lang.Character.getDirectionality` is JVM-only, so strong-direction
// detection is done with explicit code-point ranges here. Covers the scripts
// this app actually renders (Arabic, Hebrew, Latin, Greek, Cyrillic, Indic,
// kana, Han, Hangul); anything else falls through as NEUTRAL, matching the old
// behaviour for emoji, punctuation and digits.

private const val DIR_NEUTRAL = 0
private const val DIR_LTR = 1
private const val DIR_RTL = 2

private fun codePointAt(
    text: CharSequence,
    index: Int,
): Int {
    val c = text[index]
    if (c.isHighSurrogate() && index + 1 < text.length) {
        val low = text[index + 1]
        if (low.isLowSurrogate()) {
            return 0x10000 + ((c.code - 0xD800) shl 10) + (low.code - 0xDC00)
        }
    }
    return c.code
}

private fun charCount(codePoint: Int): Int = if (codePoint > 0xFFFF) 2 else 1

private fun directionalityOf(codePoint: Int): Int =
    when {
        codePoint in 0x0590..0x05FF ||
            codePoint in 0x0600..0x06FF ||
            codePoint in 0x0700..0x074F ||
            codePoint in 0x0750..0x077F ||
            codePoint in 0x0780..0x07BF ||
            codePoint in 0x07C0..0x07FF ||
            codePoint in 0x0800..0x085F ||
            codePoint in 0x08A0..0x08FF ||
            codePoint in 0xFB1D..0xFB4F ||
            codePoint in 0xFB50..0xFDFF ||
            codePoint in 0xFE70..0xFEFF ||
            codePoint in 0x10800..0x10FFF ||
            codePoint in 0x1E800..0x1EFFF -> DIR_RTL

        codePoint in 'A'.code..'Z'.code ||
            codePoint in 'a'.code..'z'.code ||
            codePoint in 0x00C0..0x024F ||
            codePoint in 0x0370..0x03FF ||
            codePoint in 0x0400..0x04FF ||
            codePoint in 0x0900..0x0DFF ||
            codePoint in 0x3040..0x30FF ||
            codePoint in 0x3400..0x9FFF ||
            codePoint in 0xAC00..0xD7AF -> DIR_LTR

        else -> DIR_NEUTRAL
    }

private fun isDigitCodePoint(codePoint: Int): Boolean =
    codePoint in '0'.code..'9'.code ||
        codePoint in 0x0660..0x0669 ||
        codePoint in 0x06F0..0x06F9 ||
        codePoint in 0x0966..0x096F

private fun isWhitespaceCodePoint(codePoint: Int): Boolean =
    codePoint == ' '.code ||
        codePoint == '\t'.code ||
        codePoint == '\n'.code ||
        codePoint == '\r'.code

/**
 * Bidirectional (BiDi) text utilities for mixed Arabic/English content,
 * dynamic layout direction resolution, and Unicode directional isolates.
 */
object BidiUtils {
    /**
     * True when the LTR run scanning below should start at [index]: a letter or digit
     * with strong left-to-right directionality.
     */
    fun isLtrRunStart(
        text: CharSequence,
        index: Int,
    ): Boolean {
        return directionalityOf(codePointAt(text, index)) == DIR_LTR
    }

    /**
     * Index just past the run of LTR text that starts at [startIndex].
     *
     * Replaces the `Character.getDirectionality` loop MarkdownText used to run on the
     * JVM: it keeps letters, European numbers and the separators between them inside
     * one isolate, and stops at anything RTL or neutral.
     */
    fun ltrRunEnd(
        text: CharSequence,
        startIndex: Int,
    ): Int {
        var end = startIndex + charCount(codePointAt(text, startIndex))
        while (end < text.length) {
            val cp = codePointAt(text, end)
            val continueRun =
                directionalityOf(cp) == DIR_LTR ||
                    isDigitCodePoint(cp) ||
                    isWhitespaceCodePoint(
                        cp,
                    ) && end + 1 < text.length && directionalityOf(codePointAt(text, end + 1)) == DIR_LTR
            if (!continueRun) break
            end += charCount(cp)
        }
        return end
    }

    const val LRI = "\u2066" // Left-to-Right Isolate
    const val RLI = "\u2067" // Right-to-Left Isolate
    const val FSI = "\u2068" // First-Strong Isolate
    const val PDI = "\u2069" // Pop Directional Isolate
    const val LRM = "\u200E" // Left-to-Right Mark
    const val RLM = "\u200F" // Right-to-Left Mark

    enum class BidiDirection {
        LTR,
        RTL,
        NEUTRAL,
    }

    /**
     * Determines whether the given text should be rendered as RTL.
     *
     * 1. If the first strong directional character is RTL (Arabic/Hebrew), returns true.
     * 2. If the first strong directional character is LTR (e.g. an Arabic sentence starting with
     *    an English term like "Android هو نظام..."), returns true if RTL characters outnumber LTR characters.
     * 3. If there are no strong characters (empty, numbers, emojis, symbols only), returns false.
     */
    fun isRtlText(text: CharSequence): Boolean {
        var firstStrongRtl: Boolean? = null
        var rtlCount = 0
        var ltrCount = 0

        var i = 0
        val len = text.length
        while (i < len) {
            val cp = codePointAt(text, i)
            when (directionalityOf(cp)) {
                DIR_RTL -> {
                    if (firstStrongRtl == null) firstStrongRtl = true
                    rtlCount++
                }

                DIR_LTR -> {
                    if (firstStrongRtl == null) firstStrongRtl = false
                    ltrCount++
                }
            }
            i += charCount(cp)
        }

        return when {
            firstStrongRtl == true -> true
            firstStrongRtl == false -> rtlCount > ltrCount
            else -> false
        }
    }

    /**
     * Detects the dominant [BidiDirection] of [text].
     * Returns [BidiDirection.NEUTRAL] when no strong directional characters exist.
     */
    fun detectTextDirection(text: CharSequence): BidiDirection {
        var hasStrong = false
        var firstStrongRtl: Boolean? = null
        var rtlCount = 0
        var ltrCount = 0

        var i = 0
        val len = text.length
        while (i < len) {
            val cp = codePointAt(text, i)
            when (directionalityOf(cp)) {
                DIR_RTL -> {
                    hasStrong = true
                    if (firstStrongRtl == null) firstStrongRtl = true
                    rtlCount++
                }

                DIR_LTR -> {
                    hasStrong = true
                    if (firstStrongRtl == null) firstStrongRtl = false
                    ltrCount++
                }
            }
            i += charCount(cp)
        }

        if (!hasStrong) return BidiDirection.NEUTRAL

        val isRtl =
            when {
                firstStrongRtl == true -> true
                firstStrongRtl == false -> rtlCount > ltrCount
                else -> false
            }
        return if (isRtl) BidiDirection.RTL else BidiDirection.LTR
    }

    /**
     * Resolves the Compose [LayoutDirection] for [text].
     * If [text] is neutral/empty, falls back to [fallback].
     */
    fun resolveLayoutDirection(
        text: CharSequence,
        fallback: LayoutDirection = LayoutDirection.Ltr,
    ): LayoutDirection =
        when (detectTextDirection(text)) {
            BidiDirection.RTL -> LayoutDirection.Rtl
            BidiDirection.LTR -> LayoutDirection.Ltr
            BidiDirection.NEUTRAL -> fallback
        }

    /**
     * Returns true if [text] contains strong LTR characters (Latin letters, digits)
     * and no strong RTL characters (Arabic, Hebrew).
     */
    fun isLtrSnippet(text: CharSequence): Boolean {
        var hasLtr = false
        var i = 0
        val len = text.length
        while (i < len) {
            val cp = codePointAt(text, i)
            when (directionalityOf(cp)) {
                DIR_RTL -> return false

                DIR_LTR -> hasLtr = true
            }
            i += charCount(cp)
        }
        return hasLtr
    }

    /**
     * Wraps [text] in a Left-to-Right Isolate (LRI ... PDI) if [isLtrSnippet] is true.
     * Used to prevent inline English terms and code from bleeding into surrounding RTL runs.
     */
    fun wrapLtrIsolate(text: String): String = if (isLtrSnippet(text)) "$LRI$text$PDI" else text

    /** Wraps inline code in an LTR isolate, including code made only of neutral characters. */
    fun wrapInlineCodeLtrIsolate(text: String): String = "$LRI$text$PDI"

    /**
     * Appends a Right-to-Left Mark (RLM) to [text] if it is RTL, ensuring trailing
     * punctuation and neutral emojis anchor to the visual end of the RTL sentence.
     */
    fun anchorTrailingRtl(text: String): String = if (isRtlText(text) && !text.endsWith(RLM)) "$text$RLM" else text
}
