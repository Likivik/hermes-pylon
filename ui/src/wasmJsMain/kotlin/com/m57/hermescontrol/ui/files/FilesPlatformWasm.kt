package com.m57.hermescontrol.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The two things the browser *can* do, wired to real browser APIs.
 *
 * Bytes cross the JS boundary as base64: Kotlin/Wasm does convert typed arrays, but a string is
 * unambiguous and a 20 MB upload is still a single allocation either way.
 */
@JsFun(
    """(accept, onData, onError) => {
        const input = document.createElement('input');
        input.type = 'file';
        if (accept) input.accept = accept;
        input.style.display = 'none';
        document.body.appendChild(input);
        const done = () => { input.remove(); };
        input.addEventListener('change', () => {
            const file = input.files && input.files[0];
            if (!file) { onError('No file chosen'); done(); return; }
            const reader = new FileReader();
            reader.onload = () => {
                const view = new Uint8Array(reader.result);
                let binary = '';
                const chunk = 0x8000;
                for (let i = 0; i < view.length; i += chunk) {
                    binary += String.fromCharCode.apply(null, view.subarray(i, i + chunk));
                }
                onData(file.name, file.type || 'application/octet-stream', btoa(binary));
                done();
            };
            reader.onerror = () => { onError('Could not read that file'); done(); };
            reader.readAsArrayBuffer(file);
        });
        input.click();
    }""",
)
private external fun jsOpenFilePicker(
    accept: String?,
    onData: (String, String, String) -> Unit,
    onError: (String) -> Unit,
)

@JsFun(
    """(name, mime, base64) => {
        const binary = atob(base64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        const blob = new Blob([bytes], { type: mime });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = name;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 60000);
    }""",
)
private external fun jsSaveBlob(
    name: String,
    mime: String,
    base64: String,
)

@OptIn(ExperimentalEncodingApi::class)
private fun encode(bytes: ByteArray): String = Base64.Default.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
private fun decode(base64: String): ByteArray = Base64.Default.decode(base64)

private fun openPicker(
    accept: String?,
    onPicked: (PickedFile) -> Unit,
    onError: (String?) -> Unit,
) {
    jsOpenFilePicker(
        accept = accept,
        onData = { name, mime, base64 ->
            runCatching { onPicked(PickedFile(name = name, mimeType = mime, bytes = decode(base64))) }
                .onFailure { onError(it.message ?: "Could not decode that file") }
        },
        onError = { message -> onError(message) },
    )
}

/** Downloads in the browser are a blob URL behind an anchor: there is no file to stage. */
@Composable
actual fun rememberDownloadedFileLauncher(): ((DownloadedFile) -> Unit)? =
    remember {
        { file ->
            val bytes = file.bytes
            if (bytes != null) {
                jsSaveBlob(name = file.name, mime = file.mimeType, base64 = encode(bytes))
            }
        }
    }

@JsFun("(ms) => new Date(ms).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })")
private external fun jsDateString(ms: Double): String

@JsFun("(ms) => new Date(ms).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })")
private external fun jsTimeString(ms: Double): String

/**
 * The browser cannot do either half of this yet: there is no file-system picker
 * bound to the gateway upload path, and no way to hand a cached file to an
 * external viewer. Both say so plainly instead of failing silently.
 */
@Composable
actual fun rememberFilesPlatform(): FilesPlatform =
    remember {
        object : FilesPlatform {
            override val canPickFiles = true
            override val canOpenDownloads = false

            override fun pickFile(
                mimeType: String,
                onPicked: (PickedFile) -> Unit,
                onError: (String?) -> Unit,
            ) {
                openPicker(
                    accept = mimeType.takeIf { it.isNotBlank() && it != "application/octet-stream" },
                    onPicked = onPicked,
                    onError = onError,
                )
            }

            override fun stagingDirectory(): String? = null

            override suspend fun openDownloaded(
                cachePath: String,
                name: String,
                mimeType: String,
            ): Result<Unit> =
                Result.failure(
                    UnsupportedOperationException("Opening downloads is not supported in the browser build yet"),
                )

            override fun formatSize(bytes: Long): String {
                if (bytes < 1024) return "$bytes B"
                val units = listOf("KB", "MB", "GB", "TB")
                var value = bytes.toDouble() / 1024.0
                var unit = 0
                while (value >= 1024.0 && unit < units.lastIndex) {
                    value /= 1024.0
                    unit++
                }
                return "${(value * 10).toLong() / 10.0} ${units[unit]}"
            }

            override fun formatDateTime(epochMillis: Long): Pair<String, String> =
                jsDateString(epochMillis.toDouble()) to jsTimeString(epochMillis.toDouble())
        }
    }
