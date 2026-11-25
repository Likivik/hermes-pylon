package com.m57.hermescontrol.data.security

/** Namespace for auth secrets inside [SecretStore]. Must match the legacy keystore prefix. */
const val AUTH_SECRET_PREFIX = "auth/"

fun authSecretKey(key: String): String = AUTH_SECRET_PREFIX + key
