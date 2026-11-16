package com.m57.hermescontrol.ui.files

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.m57.hermescontrol.data.model.ManagedFileEntry
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * The transport seam: picking a file must hand its bytes to the upload action unchanged.
 * This is the part that used to be verified by clicking pixels at a canvas.
 */
@OptIn(ExperimentalTestApi::class)
class FilesUploadTest {
    private class RecordingActions : FilesActions by FilesScreenTest.NoopActions {
        var name: String? = null
        var bytes: ByteArray? = null
        var mimeType: String? = null

        override fun uploadFile(
            name: String,
            bytes: ByteArray,
            mimeType: String,
        ) {
            this.name = name
            this.bytes = bytes
            this.mimeType = mimeType
        }
    }

    private val picked =
        PickedFile(
            name = "seed.txt",
            bytes = byteArrayOf(1, 2, 3, 4),
            mimeType = "text/plain",
        )

    private val platform =
        object : FilesPlatform {
            override val canPickFiles = true

            override val canOpenDownloads = false

            override fun pickFile(
                mimeType: String,
                onPicked: (PickedFile) -> Unit,
                onError: (String?) -> Unit,
            ) {
                assertEquals("*/*", mimeType)
                onPicked(picked)
            }

            override fun stagingDirectory(): String? = null

            override suspend fun openDownloaded(
                cachePath: String,
                name: String,
                mimeType: String,
            ): Result<Unit> = Result.failure(UnsupportedOperationException("test"))

            override fun formatSize(bytes: Long): String = "$bytes B"

            override fun formatDateTime(epochMillis: Long): Pair<String, String> = "" to ""
        }

    @Test
    fun pickingAFileUploadsItsBytes() =
        runComposeUiTest {
            val recorded = RecordingActions()
            setContent {
                FilesScreen(
                    state =
                        FilesUiState(
                            currentPath = "/opt/data/seed",
                            entries =
                                listOf(
                                    ManagedFileEntry(
                                        name = "notes.md",
                                        path = "/opt/data/seed/notes.md",
                                        size = 1024,
                                    ),
                                ),
                        ),
                    actions = recorded,
                    filesPlatform = platform,
                )
            }
            onNodeWithTag("files-upload").performClick()
            waitForIdle()
            assertEquals("seed.txt", recorded.name)
            assertEquals("text/plain", recorded.mimeType)
            assertContentEquals(byteArrayOf(1, 2, 3, 4), recorded.bytes ?: ByteArray(0))
        }
}
