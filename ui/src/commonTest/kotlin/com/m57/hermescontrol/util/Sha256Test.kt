package com.m57.hermescontrol.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Known-answer tests for the hand-written SHA-256.
 *
 * The constants were regenerated from the FIPS 180-4 definition rather than typed
 * in, but constants alone prove nothing: these vectors are the check that the
 * padding and word assembly are right too.
 */
class Sha256Test {
    @Test
    fun emptyStringMatchesFipsVector() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(""),
        )
    }

    @Test
    fun abcMatchesFipsVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc"),
        )
    }

    @Test
    fun multiBlockInputMatchesFipsVector() {
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }

    @Test
    fun oneMillionAsMatchesFipsVector() {
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            sha256Hex("a".repeat(1_000_000)),
        )
    }
}
