package com.m57.hermescontrol.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `queryPath` builds the query string of every GET route in the app, so the replacement encoder is pinned here.
 *
 * The values below are what the previous hand-rolled encoder produced: unreserved characters stayed bare, a space
 * became `%20`, and everything else became uppercase `%XX` per UTF-8 byte. Ktor's `URLBuilder` must agree, or the
 * change is not a refactor but a behaviour change.
 */
class QueryPathTest {
    @Test
    fun noParametersLeavesThePathAlone() {
        assertEquals("api/logs", queryPath("api/logs"))
        assertEquals("api/logs", queryPath("api/logs", "level" to null))
    }

    @Test
    fun parametersJoinWithAmpersandsInOrder() {
        assertEquals(
            "api/logs?file=agent&lines=100",
            queryPath("api/logs", "file" to "agent", "lines" to "100"),
        )
    }

    @Test
    fun unreservedCharactersStayBare() {
        assertEquals("p?q=agent_2026-11-22.log~1", queryPath("p", "q" to "agent_2026-11-22.log~1"))
    }

    @Test
    fun aSpaceBecomesPercentTwenty() {
        assertEquals("p?q=a%20b", queryPath("p", "q" to "a b"))
    }

    @Test
    fun aLiteralPlusIsEscaped() {
        assertEquals("p?q=a%2Bb", queryPath("p", "q" to "a+b"))
    }

    @Test
    fun anAmpersandCannotSplitAValueIntoTwoParameters() {
        assertEquals("p?q=a%26b%3D1", queryPath("p", "q" to "a&b=1"))
    }

    @Test
    fun cyrillicIsPercentEncodedPerUtf8Byte() {
        val built = queryPath("p", "q" to "привет")
        assertTrue(built.startsWith("p?q=%D0%9F") || built.startsWith("p?q=%D0%BF"), "got $built")
        assertEquals("p?q=%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82", built)
    }

    @Test
    fun anEmptyValueStillNamesItsParameter() {
        assertEquals("p?q=", queryPath("p", "q" to ""))
    }
}
