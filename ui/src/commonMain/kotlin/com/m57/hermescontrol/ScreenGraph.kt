package com.m57.hermescontrol

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.nav_drawer_section_automate
import com.m57.hermescontrol.generated.resources.nav_drawer_section_configure
import com.m57.hermescontrol.generated.resources.nav_drawer_section_converse
import com.m57.hermescontrol.generated.resources.nav_drawer_section_inspect
import com.m57.hermescontrol.ui.icons.HermesIcon
import org.jetbrains.compose.resources.StringResource

/** Drawer grouping for a top-level screen. */
enum class DrawerSection(
    val titleRes: StringResource,
) {
    CONVERSE(Res.string.nav_drawer_section_converse),
    AUTOMATE(Res.string.nav_drawer_section_automate),
    CONFIGURE(Res.string.nav_drawer_section_configure),
    INSPECT(Res.string.nav_drawer_section_inspect),
}

/**
 * One entry in the app's navigation surface.
 *
 * [content] is supplied by the platform: the definition (key, label, icon,
 * section) is identical everywhere, but a screen may be assembled differently per
 * target — Android's chat screen builds its own ViewModel from the `Application`,
 * while a future desktop shell would not.
 */
data class ScreenDefinition(
    val key: NavKey,
    val labelRes: StringResource,
    val icon: HermesIcon,
    val drawerSection: DrawerSection?,
    val content: @Composable (sessionId: String?, openDrawer: () -> Unit) -> Unit,
)

/**
 * Every screen this target can show, plus the policy for how they stack.
 *
 * Replaces the Android-only `ScreenRegistry` object: the lookup, drawer grouping
 * and primary-screen policy are shared, and only the list is per-platform. That
 * is what stops one platform-bound screen from blocking a file from moving to
 * `commonMain`.
 *
 * [primary] is deliberately explicit rather than "everything in the drawer" — a
 * top-level screen clears the back stack on navigation, and Profiles, Keys and
 * the rest must not.
 */
class ScreenGraph(
    val screens: List<ScreenDefinition>,
    val primary: Set<NavKey>,
) {
    private val byKey = screens.associateBy { it.key }

    /** True when [key] is a top-level screen that resets the back stack. */
    fun isPrimary(key: NavKey): Boolean = key in primary

    fun definitionFor(key: NavKey): ScreenDefinition? = byKey[key]

    /** Drawer entries grouped by section, in declaration order. */
    fun drawer(): List<Pair<DrawerSection, List<ScreenDefinition>>> =
        DrawerSection.entries.map { section ->
            section to screens.filter { it.drawerSection == section }
        }

    fun contentFor(key: NavKey): (@Composable (sessionId: String?, openDrawer: () -> Unit) -> Unit)? =
        byKey[key]?.content
}

/**
 * The graph for this target.
 *
 * Android overrides the chat entry so it can build `ChatViewModel` from the
 * `Application`; other targets declare the subset they can render.
 */
expect fun screenGraph(): ScreenGraph
