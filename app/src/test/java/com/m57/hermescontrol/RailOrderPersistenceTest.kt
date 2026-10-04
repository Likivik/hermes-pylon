package com.m57.hermescontrol

import androidx.test.core.app.ApplicationProvider
import com.m57.hermescontrol.ui.chat.SessionUi
import com.m57.hermescontrol.ui.chat.rail.groupRail
import com.m57.hermescontrol.ui.chat.rail.mergeArrangement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Persistence contract of the drag-reorder: the ORDER the rail writes to
 * SharedPreferences (home_order) is the SAME order applied on the next
 * launch. This is the "survives relaunch" half of the reorder feature —
 * the drag→persist half is covered by RailReorderE2eTest (real gesture,
 * asserts home_order). This unit test covers persist→reload without the
 * emulator/gesture flake.
 *
 * The ViewModel writes home_order via setRailOrder (ChatViewModel.kt:1588)
 * as a comma-joined id string and reads it back in loadHomeOrder (:1583),
 * then the rail orders rows by groupRail (RailGrouping.kt:30). We mirror
 * that exact round-trip here.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application on purpose: the real HermesControlApp.onCreate initializes
// AuthManager → SecureBlobStore → AndroidKeyStore, which Robolectric does not
// provide ("AndroidKeyStore not found"). This test only needs an app Context.
@Config(sdk = [34], application = android.app.Application::class)
class RailOrderPersistenceTest {
    private fun session(
        id: String,
        lastActive: Long = nowSeconds() - 60,
    ): SessionUi =
        SessionUi(
            id = id,
            title = id,
            lastActive = lastActive,
        )

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1000

    private fun seedHomeOrder(order: List<String>) {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("hermes_rail", 0)
            .edit()
            .putString("home_order", order.joinToString(","))
            .apply()
    }

    @Test
    fun `persisted home_order is reapplied on rail reload`() {
        // A previous session drag left this order on disk.
        seedHomeOrder(listOf("item-low", "item-top", "stored-bg", "stored-current"))

        // Next launch: rail loads all fresh sessions and applies the order.
        val now = nowSeconds()
        val sessions =
            listOf(
                session("stored-current", now - 2000),
                session("stored-bg", now - 3000),
                session("item-top", now - 1000), // newest — would sort first w/o order
                session("item-low", now - 5000),
            )
        val groups = groupRail(sessions, pinnedIds = emptySet(), order = seedOrderFromPrefs())
        val displayed = groups.fresh.map { it.id }

        // The persisted drag order wins over the newest-first default sort.
        assertEquals(
            listOf("item-low", "item-top", "stored-bg", "stored-current"),
            displayed,
        )
    }

    @Test
    fun `mergeArrangement round-trips the persisted order`() {
        val sessions =
            listOf(
                session("item-top"),
                session("item-low"),
                session("stored-bg"),
                session("stored-current"),
            )
        val groups = groupRail(sessions, pinnedIds = emptySet(), order = emptyList())

        // The drag moved item-low above item-top -> mergeArrangement persists it.
        val arranged = listOf("item-low", "item-top", "stored-bg", "stored-current")
        val merged = mergeArrangement(groups, arranged)

        assertEquals(arranged, merged)
    }

    @Test
    fun `missing home_order falls back to default sort`() {
        val now = nowSeconds()
        val sessions =
            listOf(
                session("item-top", now - 1000), // older
                session("item-low", now - 5000), // older still
            )
        val groups = groupRail(sessions, pinnedIds = emptySet(), order = emptyList())
        // No persisted order: newest-first (default) -> item-top above item-low.
        assertTrue(
            groups.fresh.map { it.id }.indexOf("item-top") <
                groups.fresh.map { it.id }.indexOf("item-low"),
        )
    }

    private fun seedOrderFromPrefs(): List<String> =
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("hermes_rail", 0)
            .getString("home_order", null)
            ?.takeIf { it.isNotBlank() }
            ?.split(',')
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
}
