package com.m57.hermescontrol.util

/**
 * Whether this code is running inside a JVM unit-test run.
 *
 * Some ViewModel paths skip retry timers and artificial delays under test. That
 * check used to be `Class.forName("org.junit.Test")` inline, which is JVM-only —
 * this seam keeps the behaviour while letting the class live in commonMain.
 * Web and native targets are never unit-test hosts for this module.
 */
expect fun isRunningUnderTest(): Boolean
