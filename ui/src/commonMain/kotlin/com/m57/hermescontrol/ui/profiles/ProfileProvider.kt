package com.m57.hermescontrol.ui.profiles

/**
 * The seam for "which profile should this screen act on".
 *
 * A screen must not reach into the singleton client: that makes it untestable (a unit test
 * would dial the network) and hides the dependency. Production passes
 * `GatewayConnection::currentProfileName`; tests pass a constant.
 */
fun interface ProfileProvider {
    suspend fun current(): String?
}
