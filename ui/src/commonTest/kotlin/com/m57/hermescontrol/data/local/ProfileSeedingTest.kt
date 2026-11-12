package com.m57.hermescontrol.data.local

import com.m57.hermescontrol.data.config.ConnectionProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * A browser sign-in happens outside the app, so the profile list has to be adopted
 * afterwards. Both defects reported on device lived here: an empty Connections list,
 * and a dead "Default" row appearing beside the real one.
 */
class ProfileSeedingTest {
    private val defaultBaseUrl = "https://127.0.0.1:9119/"

    private fun pristineDefault() =
        ConnectionProfile(id = AuthManager.DEFAULT_PROFILE_ID, name = "Default", baseUrl = defaultBaseUrl)

    private fun seed(
        profiles: List<ConnectionProfile>,
        selectedId: String? = null,
        origin: String = ORIGIN,
    ) = profileSeedingFor(
        profiles = profiles,
        selectedId = selectedId,
        origin = origin,
        displayName = "serenity",
        defaultProfileId = AuthManager.DEFAULT_PROFILE_ID,
        defaultBaseUrl = defaultBaseUrl,
    )

    @Test
    fun cleanStoreAdoptsThePlaceholderInsteadOfAddingARow() {
        val seeded = seed(listOf(pristineDefault()))

        assertEquals(1, seeded!!.profiles.size)
        assertEquals(ORIGIN, seeded.profiles.single().baseUrl)
        assertEquals("serenity", seeded.profiles.single().name)
        assertEquals("ticket", seeded.profiles.single().wsAuthParam)
        assertEquals(seeded.profiles.single().id, seeded.selectedId)
    }

    @Test
    fun existingProfileForTheOriginIsSelectedNotDuplicated() {
        val existing = ConnectionProfile(name = "work", baseUrl = ORIGIN)

        val seeded = seed(listOf(existing), selectedId = "other")

        assertEquals(listOf(existing), seeded!!.profiles)
        assertEquals(existing.id, seeded.selectedId)
    }

    @Test
    fun alreadySelectedProfileIsLeftCompletelyAlone() {
        val existing = ConnectionProfile(name = "work", baseUrl = ORIGIN)

        assertNull(seed(listOf(existing), selectedId = existing.id))
    }

    @Test
    fun aUserMadeProfileIsNeverReplaced() {
        val mine = ConnectionProfile(name = "mine", baseUrl = "https://elsewhere:9119")
        val mine2 = ConnectionProfile(name = "mine2", baseUrl = "https://another:9119")

        val seeded = seed(listOf(mine, mine2))!!

        assertEquals(3, seeded.profiles.size)
        assertEquals(mine, seeded.profiles[0])
        assertEquals(mine2, seeded.profiles[1])
        assertNotEquals(mine.id, seeded.selectedId)
    }

    @Test
    fun trailingSlashDoesNotCreateASecondProfile() {
        val existing = ConnectionProfile(name = "work", baseUrl = ORIGIN)

        assertNull(seed(listOf(existing), selectedId = existing.id, origin = "$ORIGIN/"))
    }

    private companion object {
        const val ORIGIN = "https://serenity.oryx-galaxy.ts.net:8445"
    }
}
