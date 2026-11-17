package com.m57.hermescontrol.ui.files

import androidx.compose.runtime.Composable

/** A file the user chose, already read into memory and capped by the caller. */
data class PickedFile(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is PickedFile && name == other.name && mimeType == other.mimeType &&
                    bytes.contentEquals(
                        other.bytes,
                    )
            )

    override fun hashCode(): Int = 31 * (31 * name.hashCode() + mimeType.hashCode()) + bytes.contentHashCode()
}

/**
 * The device-side half of the files screen: choosing a file to upload, staging a
 * download, and opening it.
 *
 * Android uses the Storage Access Framework and a FileProvider; the desktop uses
 * a file chooser and `Desktop.open`. The browser has no equivalent for either, so
 * it reports that explicitly instead of pretending — the honest failure the user
 * can act on beats a silent no-op.
 */
interface FilesPlatform {
    /** True when this platform can pick files to upload. */
    val canPickFiles: Boolean

    /** True when this platform can open a downloaded file. */
    val canOpenDownloads: Boolean

    /** Opens the platform picker. [onPicked] gets null-equivalent via [onError] when the user cancels. */
    fun pickFile(
        mimeType: String,
        onPicked: (PickedFile) -> Unit,
        onError: (String?) -> Unit,
    )

    /** Directory downloads are staged in, or null when the platform has none. */
    fun stagingDirectory(): String?

    /** Opens a previously staged download. */
    suspend fun openDownloaded(
        cachePath: String,
        name: String,
        mimeType: String,
    ): Result<Unit>

    /** Human-readable size, e.g. "1.4 MB". */
    fun formatSize(bytes: Long): String

    /** Local date and time strings for a file's modification time, epoch millis. */
    fun formatDateTime(epochMillis: Long): Pair<String, String>
}

@Composable
expect fun rememberFilesPlatform(): FilesPlatform

/**
 * A way to hand in-memory bytes to the user, for platforms with no filesystem.
 *
 * The browser downloads through a blob URL and an anchor; Android and the desktop stage a real
 * file and open it, so they return null here.
 *
 * Contract: non-null means this platform hands bytes to a launcher (the browser's blob
 * URL). Null means the bytes were staged to a real file and the screen opens it by path.
 * **Null does not mean "download unsupported"** — both paths deliver the file.
 */
@Composable
expect fun rememberDownloadedFileLauncher(): ((DownloadedFile) -> Unit)?
