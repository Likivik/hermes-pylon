package com.m57.hermescontrol.ui.icons

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.material_symbols_outlined
import org.jetbrains.compose.resources.Font

/**
 * Material Symbols rendered as a variable font.
 *
 * Why a font instead of `ImageVector`s: the Material Icons artifacts
 * (`androidx.compose.material:material-icons-extended`) are Android-only and
 * were pinned at 1.7.3 when JetBrains moved to K2, so they are unavailable to
 * wasmJs/JVM. Material Symbols is the maintained successor and ships as a
 * variable font, which Compose Multiplatform supports on every target
 * (variable fonts landed on all platforms in CMP 1.8.0; `FontVariation.Settings`
 * is supported in the resources library).
 *
 * The asset is `material_symbols_outlined.ttf` in `composeResources/font`
 * (variable axes FILL/GRAD/opsz/wght), and `files/material_symbols.codepoints`
 * maps symbol name -> Unicode code point for all 4,301 symbols.
 *
 * The TTF is used rather than the smaller WOFF2 because WOFF2 is web-only:
 * Android's font stack cannot load it, and this is commonMain.
 */
object MaterialSymbols {
    /** Symbol name -> glyph string. Parsed once from the bundled codepoints file. */
    private var cache: Map<String, String>? = null

    /**
     * Parse `files/material_symbols.codepoints` (`<name> <hex>` per line) into
     * an ordered name -> glyph map. The file is ~79 KB / 4,301 entries; it is
     * read through Compose resources so it is fetched on demand on web.
     */
    suspend fun entries(): Map<String, String> {
        cache?.let { return it }
        val parsed =
            Res
                .readBytes("files/material_symbols.codepoints")
                .decodeToString()
                .lineSequence()
                .mapNotNull { line ->
                    val parts = line.trim().split(' ')
                    if (parts.size != 2) return@mapNotNull null
                    val code = parts[1].toIntOrNull(16) ?: return@mapNotNull null
                    parts[0] to codePointToString(code)
                }.toMap()
        cache = parsed
        return parsed
    }

    /** Look up a symbol's glyph, or null when the name is unknown. */
    suspend fun glyph(name: String): String? = entries()[name]

    /**
     * Code point -> String.
     *
     * Material Symbols live in the private-use area and ~a dozen of them sit
     * above U+FFFF (max is U+FFFFD), so a bare `Char` cannot represent them.
     * Build the UTF-16 surrogate pair explicitly — portable across JVM,
     * Android and wasm (no `Character.toChars` in common code).
     */
    private fun codePointToString(codePoint: Int): String =
        if (codePoint <= 0xFFFF) {
            codePoint.toChar().toString()
        } else {
            val v = codePoint - 0x10000
            val high = (0xD800 + (v shr 10)).toChar()
            val low = (0xDC00 + (v and 0x3FF)).toChar()
            buildString(2) {
                append(high)
                append(low)
            }
        }
}

/**
 * The Material Symbols font family for the given fill state.
 *
 * `@Composable` because the font resource is loaded asynchronously by Compose
 * resources. `filled` drives the FILL axis (0 = outlined, 1 = filled).
 */
@Composable
fun materialSymbolsFamily(filled: Boolean = true): FontFamily {
    // Do NOT wrap this in `remember`: `Font(...)` is a @Composable that
    // recomposes once the resource finishes loading (async on web). Caching the
    // family would pin the pre-load Font instance and leave private-use glyphs
    // rendering as tofu forever.
    val font =
        Font(
            Res.font.material_symbols_outlined,
            variationSettings =
                FontVariation.Settings(
                    // FILL is a custom Material Symbols axis, not one of Compose's
                    // built-in ones, so it is addressed by tag.
                    FontVariation.Setting("FILL", if (filled) 1f else 0f),
                ),
        )
    return FontFamily(font)
}

/**
 * Render a single Material Symbol by name.
 *
 * Renders nothing when [name] is not in the catalog — callers should not be
 * handed unknown names, but a missing glyph must never crash a rail row.
 */
@Composable
fun MaterialSymbol(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    filled: Boolean = true,
    contentDescription: String? = null,
) {
    var glyph by remember(name) { mutableStateOf<String?>(null) }
    LaunchedEffect(name) { glyph = MaterialSymbols.glyph(name) }

    val symbol = glyph ?: return
    val fontSize = with(LocalDensity.current) { size.toSp() }
    val semanticsModifier =
        if (contentDescription == null) {
            modifier
        } else {
            modifier.semantics { this.contentDescription = contentDescription }
        }
    Text(
        text = symbol,
        modifier = semanticsModifier,
        color = tint,
        fontFamily = materialSymbolsFamily(filled),
        fontSize = fontSize,
        lineHeight = fontSize,
        maxLines = 1,
    )
}
