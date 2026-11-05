package com.m57.hermescontrol.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.onest_variable
import org.jetbrains.compose.resources.Font

/**
 * The app's UI font: Onest, variable (wght 100–900), subset to Latin + Cyrillic
 * plus the punctuation/symbols the UI uses (~123 KB raw, ~72 KB gz).
 *
 * Why a bundled font instead of `FontFamily.Default`:
 *   - on web, `Default` resolves through Compose's automatic fallback, which
 *     downloads a ~206 KB font from fonts.gstatic.com on first text layout —
 *     a third-party round trip between the loading overlay leaving and the
 *     first word painting. A bundled family renders from local bytes.
 *   - today every platform shows a different font (Roboto on Android, system
 *     default on desktop, Noto Sans from Google on web); one family makes them
 *     the same.
 *
 * Chosen 2026-11-05 over Inter/Geist/Manrope: Cyrillic is first-class (the
 * foundry is Moldovan), geometry is modern without being a clone. Arabic, CJK
 * and other scripts keep the automatic per-script fallback — bundling those is
 * megabytes per language for locales most users never open.
 *
 * Weights used by [Typography] are exposed through one variable file; Compose
 * maps `FontWeight` to the `wght` axis.
 */
@Composable
fun onestFamily(): FontFamily {
    return FontFamily(
        Font(
            Res.font.onest_variable,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.Setting("wght", 400f)),
        ),
        Font(
            Res.font.onest_variable,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.Setting("wght", 500f)),
        ),
        Font(
            Res.font.onest_variable,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.Setting("wght", 600f)),
        ),
        Font(
            Res.font.onest_variable,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.Setting("wght", 700f)),
        ),
    )
}

/**
 * Full Material 3 type hierarchy — every style explicitly tuned so the
 * app stops inheriting Compose defaults.
 *
 * Letter spacing is tighter than M3 defaults (modern look), line heights are
 * comfortable for long-form.
 */
@Composable
fun pylonTypography(): Typography =
    Typography(
        // Display — hero numbers, big stats
        displayLarge =
            TextStyle(
                fontFamily = onestFamily(),
                fontWeight = FontWeight.Bold,
                fontSize = 44.sp,
                lineHeight = 52.sp,
                letterSpacing = (-0.5).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = onestFamily(),
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = (-0.25).sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = onestFamily(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                lineHeight = 38.sp,
                letterSpacing = 0.sp,
            ),
        // Headline — empty-state titles, screen intros
        headlineLarge =
            TextStyle(
                fontFamily = onestFamily(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = onestFamily(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = onestFamily(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                letterSpacing = 0.1.sp,
            ),
        // Title — TopAppBar, card headings
        titleLarge =
            TextStyle(
                fontFamily = onestFamily(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                letterSpacing = 0.1.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = onestFamily(),
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = onestFamily(),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        // Body — primary reading
        bodyLarge =
            TextStyle(
                fontFamily = onestFamily(),
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.25.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = onestFamily(),
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.2.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = onestFamily(),
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
            ),
        // Label — buttons, chips, captions
        labelLarge =
            TextStyle(
                fontFamily = onestFamily(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = onestFamily(),
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = onestFamily(),
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
    )
