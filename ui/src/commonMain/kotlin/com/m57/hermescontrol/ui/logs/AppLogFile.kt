package com.m57.hermescontrol.ui.logs

/** One on-device app log file waiting to be uploaded. */
data class AppLogFile(
    val name: String,
    val bytes: ByteArray,
) {
    // data class with a ByteArray needs explicit equality; identity is fine here
    // because these are uploaded immediately and never compared or hashed.
    override fun equals(other: Any?): Boolean =
        this === other || (other is AppLogFile && name == other.name && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
}

/**
 * The app's own log files, newest last, ready to upload.
 *
 * Android exposes files written by the crash logger; the desktop and browser
 * targets have no such directory and return an empty list, which the screen
 * reports honestly as "No app logs found to upload".
 */
expect suspend fun readAppLogFiles(): List<AppLogFile>
