package com.m57.hermescontrol.data.remote

import android.content.Context
import android.net.Uri
import android.util.Base64OutputStream
import com.m57.hermescontrol.util.HermesLog
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream

/**
 * Reads a `content://` or `file://` URI through the ContentResolver and encodes it
 * to base64 in 128 KB chunks, so a large attachment never lands in memory twice.
 */
class AndroidAttachmentReader(
    private val context: Context,
) : AttachmentReader {
    override suspend fun readBase64(uri: String): String? =
        try {
            val parsed = Uri.parse(uri)
            context.contentResolver.openInputStream(parsed)?.use { stream ->
                val out = ByteArrayOutputStream()
                val encoder = Base64OutputStream(out, android.util.Base64.NO_WRAP)
                val buffer = ByteArray(128 * 1024)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    encoder.write(buffer, 0, read)
                    yield()
                }
                encoder.close()
                out.toString("UTF-8")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            HermesLog.e(TAG, "Failed to read and encode attachment (${e.javaClass.simpleName})", e)
            null
        }

    private companion object {
        const val TAG = "AttachmentReader"
    }
}
