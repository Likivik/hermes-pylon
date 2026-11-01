package com.m57.hermescontrol.data.security

/**
 * The browser target has no local encrypted database, so no master password is
 * ever generated there. Failing loudly beats returning weak randomness.
 */
actual fun secureRandomBytes(size: Int): ByteArray =
    throw UnsupportedOperationException("no local database on this target")
