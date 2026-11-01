package com.m57.hermescontrol.data.security

/** Thrown when a stored secret cannot be authenticated or decoded. */
class SecureBlobException(cause: Throwable? = null) : Exception("Secure storage authentication failed", cause)
