package com.m57.hermescontrol.ui.landing

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * In-tree icon vectors that are needed on every target but are not shipped by
 * Compose Multiplatform.
 *
 * Why this exists: `androidx.compose.material.icons` (Material Icons) is
 * deprecated upstream in favour of Material Symbols, and Compose Multiplatform
 * stopped republishing `material-icons-extended` after 1.7.3 (it was K1-built
 * and CMP 1.8+ is K2). The official guidance is to vendor the handful of
 * icons a project actually uses as local `ImageVector`s rather than pull a
 * 1.7.3 klib into a 1.12.x graph.
 *
 * [Bolt] is a byte-for-byte copy of `Icons.Filled.Bolt` from
 * material-icons-extended 1.7.8 — same 24x24 viewport, same path commands,
 * same non-mirrored orientation — reconstructed from the artifact's compiled
 * `PathBuilder` calls so the glyph matches Android exactly.
 */
object HermesIcons {
    /** Material "bolt" (filled) — the landing screen hero glyph. */
    val Bolt: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "HermesIcons.Bolt",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(11f, 21f)
                    horizontalLineToRelative(-1f)
                    lineToRelative(1f, -7f)
                    horizontalLineTo(7.5f)
                    curveToRelative(-0.58f, 0f, -0.57f, -0.32f, -0.38f, -0.66f)
                    curveToRelative(0.19f, -0.34f, 0.05f, -0.08f, 0.07f, -0.12f)
                    curveTo(8.48f, 10.94f, 10.42f, 7.54f, 13f, 3f)
                    horizontalLineToRelative(1f)
                    lineToRelative(-1f, 7f)
                    horizontalLineToRelative(3.5f)
                    curveToRelative(0.49f, 0f, 0.56f, 0.33f, 0.47f, 0.51f)
                    lineToRelative(-0.07f, 0.15f)
                    curveTo(12.96f, 17.55f, 11f, 21f, 11f, 21f)
                    close()
                }
            }.build()
    }
}
