package com.m57.hermescontrol.data.security

/**
 * Encrypted key/value storage for secrets — session tokens, refresh tokens, the
 * database password.
 *
 * Deliberately three methods and no platform types: Android implements it over
 * the Keystore, and the desktop and browser targets provide their own backends
 * without the manager above ever learning which one it is talking to.
 */
interface SecretStore {
    fun getString(logicalKey: String): String?

    fun putString(
        logicalKey: String,
        value: String?,
    )

    fun deletePrefix(prefix: String)
}
