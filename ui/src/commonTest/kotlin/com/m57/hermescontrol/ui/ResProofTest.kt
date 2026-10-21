package com.m57.hermescontrol.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.landing_title
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase 2 resource proof. Resolves a string from
 * `ui/src/commonMain/composeResources/` through the **suspend, non-composable**
 * API — the exact path the ViewModel call sites will use once the migration
 * starts, and the one that needs no `Context`.
 *
 * This is deliberately a separate test from [CmpProofTest]: that one proves the
 * Compose toolchain renders, this one proves the resource pipeline generates a
 * usable `Res` accessor and that a string can be read outside composition.
 *
 * `landing_title` is used rather than `app_name` because `app_name` must remain
 * in `androidMain/res/` for the manifest label — the two trees intentionally
 * coexist while the migration is in progress.
 */
@OptIn(ExperimentalTestApi::class)
class ResProofTest {
    @Test
    fun commonMainReadsAStringResourceOutsideComposition() =
        runComposeUiTest {
            val title = getString(Res.string.landing_title)
            assertTrue(
                title.isNotBlank(),
                "landing_title should resolve from composeResources",
            )
        }
}
