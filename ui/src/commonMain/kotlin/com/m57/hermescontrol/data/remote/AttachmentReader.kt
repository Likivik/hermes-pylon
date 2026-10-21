package com.m57.hermescontrol.data.remote

/**
 * Turns an attachment reference the compose bar produced (`content://`, `file://`,
 * a path, or an http URL) into base64 for the gateway payload.
 *
 * Installed by the host, like [com.m57.hermescontrol.util.HermesLog]: only Android
 * can read a `content://` URI, and it needs a `Context` to do it, which is exactly
 * what a library module must not hold. Until something installs a reader the call
 * returns null, so an unsupported target degrades to "attachment skipped" instead
 * of crashing.
 */
fun interface AttachmentReader {
    suspend fun readBase64(uri: String): String?
}

private var installed: AttachmentReader? = null

/** The reader the host installed, or null when this target has none. */
val attachmentReader: AttachmentReader?
    get() = installed

/**
 * Installs the platform reader, or clears it with null. Safe to call more than once;
 * the last call wins.
 */
fun installAttachmentReader(reader: AttachmentReader?) {
    installed = reader
}
