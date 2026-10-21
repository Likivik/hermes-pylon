package com.m57.hermescontrol.util

/**
 * Marks a declaration that is public only so tests can reach it.
 *
 * The stand-in for `androidx.annotation.VisibleForTesting`, which `commonMain`
 * may not import. Same intent, no platform dependency.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
)
@Retention(AnnotationRetention.BINARY)
annotation class VisibleForTesting
