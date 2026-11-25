package com.m57.hermescontrol.ui.chat.rail

import com.m57.hermescontrol.data.remote.NetworkError
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The rail must never render "no sessions" for a failed request. These pin the
 * mapping: rows present → Ready; empty success → Empty; every failure → a
 * visible Unavailable with a short reason, and 401 asks for sign-in.
 */
class RailStatusMappingTest {
    @Test
    fun rowsWithNoErrorIsReady() {
        assertEquals(RailLoadStatus.Ready, railLoadStatusOf(rowCount = 3, error = null))
    }

    @Test
    fun emptySuccessIsEmptyNotUnavailable() {
        assertEquals(RailLoadStatus.Empty, railLoadStatusOf(rowCount = 0, error = null))
    }

    @Test
    fun unauthorizedAsksForSignIn() {
        val status = railLoadStatusOf(rowCount = 0, error = NetworkError.AuthExpired())
        val unavailable = assertIs<RailLoadStatus.Unavailable>(status)
        assertEquals(true, unavailable.needsSignIn)
    }

    @Test
    fun httpErrorCarriesItsCode() {
        val status = railLoadStatusOf(rowCount = 0, error = NetworkError.Http(code = 404, message = "not found"))
        val unavailable = assertIs<RailLoadStatus.Unavailable>(status)
        assertEquals("HTTP 404", unavailable.reason)
        assertEquals(false, unavailable.needsSignIn)
    }

    @Test
    fun connectionErrorReadsAsOffline() {
        val status =
            railLoadStatusOf(
                rowCount = 0,
                error = NetworkError.Connection("boom", IOException("boom")),
            )
        val unavailable = assertIs<RailLoadStatus.Unavailable>(status)
        assertEquals("offline", unavailable.reason)
    }

    @Test
    fun failureWithLastGoodRowsStillReportsTheReason() {
        val status = railLoadStatusOf(rowCount = 7, error = NetworkError.Http(code = 500, message = "boom"))
        assertIs<RailLoadStatus.Unavailable>(status)
    }
}
