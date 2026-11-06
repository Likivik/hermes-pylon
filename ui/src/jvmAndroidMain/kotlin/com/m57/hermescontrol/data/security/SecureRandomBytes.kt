package com.m57.hermescontrol.data.security

import java.security.SecureRandom

actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }
