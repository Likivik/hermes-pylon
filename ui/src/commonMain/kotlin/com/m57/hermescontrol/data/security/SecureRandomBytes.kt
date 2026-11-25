package com.m57.hermescontrol.data.security

/**
 * Cryptographically secure random bytes.
 *
 * Used for the local database master password, so this must never degrade to
 * [kotlin.random.Random].
 */
expect fun secureRandomBytes(size: Int): ByteArray
