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
 * The app mark is "spire": a crystal spike flanked by two shards. It ships in
 * two tiers of the same silhouette, because faceting stops reading below about
 * 32 px — the same two-tier pattern Ethereum and most faceted brand marks use.
 *
 *  - [Spire]        hero tier, 6 paths, faceted. Use at 48 dp and above.
 *  - [SpireCompact] compact tier, 3 paths, flat. Use below 48 dp.
 *
 * Both carry baked-in colours, so draw them with `Image(...)`, never with
 * `Icon(tint = ...)` — a tint flattens the facets into one colour.
 *
 * Source of truth: `docs/design/chosen-spire-template.svg`
 * (`spire-hero-on-primary` and `spire-compact-on-primary`). The geometry is
 * approved: the spike is 7.2 units wide at y = 7.8 with its apex at y = 1.2.
 * Do not retune it; an earlier 6-unit-wide spike with quad shards was rejected
 * as a different mark.
 */
object HermesIcons {
    private val SpikeLit = Color(0xFFFFFFFF)
    private val SpikeShade = Color(0xFFC9B8FF)
    private val ShardLit = Color(0xFFEFE9FF)
    private val ShardShade = Color(0xFFC0AAFF)
    private val ShardCompact = Color(0xFFD9CCFF)

    /**
     * "spire", hero tier: the spike splits into two planes and each shard folds
     * into a lit face and a shaded face. Six filled paths, no strokes.
     */
    val Spire: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "HermesIcons.Spire",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                // Spike, split on the vertical axis.
                path(fill = SolidColor(SpikeLit)) {
                    moveTo(12f, 1.2f)
                    lineTo(15.6f, 7.8f)
                    lineTo(12f, 21.6f)
                    close()
                }
                path(fill = SolidColor(SpikeShade)) {
                    moveTo(12f, 1.2f)
                    lineTo(8.4f, 7.8f)
                    lineTo(12f, 21.6f)
                    close()
                }
                // Left shard, folded along its own axis.
                path(fill = SolidColor(ShardLit)) {
                    moveTo(3.2f, 8.4f)
                    lineTo(8.8f, 12.2f)
                    lineTo(6.3f, 13.1f)
                    close()
                }
                path(fill = SolidColor(ShardShade)) {
                    moveTo(6.3f, 13.1f)
                    lineTo(8.8f, 12.2f)
                    lineTo(9.4f, 17.8f)
                    close()
                }
                // Right shard, mirrored.
                path(fill = SolidColor(ShardLit)) {
                    moveTo(20.8f, 8.4f)
                    lineTo(15.2f, 12.2f)
                    lineTo(17.7f, 13.1f)
                    close()
                }
                path(fill = SolidColor(ShardShade)) {
                    moveTo(17.7f, 13.1f)
                    lineTo(15.2f, 12.2f)
                    lineTo(14.6f, 17.8f)
                    close()
                }
            }.build()
    }

    /**
     * "spire", compact tier: the same silhouette with no inner facets.
     *
     * Use below 48 dp — list rows, tabs, the web boot overlay, any badge under
     * 48 dp. Above ~32 px the hero facets turn to noise; below it they are gone.
     */
    val SpireCompact: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "HermesIcons.SpireCompact",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(SpikeLit)) {
                    moveTo(12f, 1.2f)
                    lineTo(15.6f, 7.8f)
                    lineTo(12f, 21.6f)
                    lineTo(8.4f, 7.8f)
                    close()
                }
                path(fill = SolidColor(ShardCompact)) {
                    moveTo(3.2f, 8.4f)
                    lineTo(8.8f, 12.2f)
                    lineTo(9.4f, 17.8f)
                    close()
                }
                path(fill = SolidColor(ShardCompact)) {
                    moveTo(20.8f, 8.4f)
                    lineTo(15.2f, 12.2f)
                    lineTo(14.6f, 17.8f)
                    close()
                }
            }.build()
    }
}
