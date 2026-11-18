package com.m57.hermescontrol.ui.chat.rail

import com.m57.hermescontrol.data.remote.NetworkError

/**
 * Turn one REST outcome into the rail's visible state.
 *
 * Kept pure so the mapping is unit-tested without a ViewModel: the whole point
 * of the status is that a failure never looks like "no sessions".
 */
fun railLoadStatusOf(
    rowCount: Int,
    error: NetworkError?,
): RailLoadStatus =
    when {
        error == null && rowCount > 0 -> RailLoadStatus.Ready
        error == null -> RailLoadStatus.Empty
        error is NetworkError.AuthExpired ->
            RailLoadStatus.Unavailable(reason = "sign in", needsSignIn = true)
        error is NetworkError.Http -> RailLoadStatus.Unavailable(reason = "HTTP ${error.code}")
        error is NetworkError.Connection -> RailLoadStatus.Unavailable(reason = "offline")
        else -> RailLoadStatus.Unavailable(reason = "failed")
    }
