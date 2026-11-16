package com.m57.hermescontrol.ui.files

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.m57.hermescontrol.data.model.ManagedFileEntry
import com.m57.hermescontrol.data.remote.NetworkResult
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Files screen renders from state, so it can be tested without a ViewModel, a gateway or
 * a browser. These tests also hold the accessibility line: every clickable node must announce
 * something, or a screen-reader user meets a button with no name.
 */
@OptIn(ExperimentalTestApi::class)
class FilesScreenTest {
    private val entries =
        listOf(
            ManagedFileEntry(name = "notes.md", path = "/opt/data/seed/notes.md", size = 1024),
            ManagedFileEntry(name = "diagram.png", path = "/opt/data/seed/diagram.png", size = 40960),
        )

    internal object NoopActions : FilesActions {
        override fun refresh() = Unit

        override fun navigateTo(entry: ManagedFileEntry) = Unit

        override fun navigateUp() = Unit

        override fun navigateToCrumb(path: String) = Unit

        override fun openCreateDir() = Unit

        override fun dismissCreateDir() = Unit

        override fun setNewDirName(name: String) = Unit

        override fun createDir() = Unit

        override fun requestDelete(entry: ManagedFileEntry) = Unit

        override fun dismissDelete() = Unit

        override fun confirmDelete() = Unit

        override fun uploadFile(
            name: String,
            bytes: ByteArray,
            mimeType: String,
        ) = Unit

        override fun downloadFile(
            entry: ManagedFileEntry,
            staging: String,
            onResult: (NetworkResult<DownloadedFile>) -> Unit,
        ) = Unit

        override fun showToast(message: FilesUiText) = Unit

        override fun clearToast() = Unit

        override fun clearTransientState() = Unit
    }

    @Test
    fun populatedStateRendersEveryEntry() =
        runComposeUiTest {
            setContent {
                FilesScreen(
                    state = FilesUiState(currentPath = "/opt/data/seed", entries = entries),
                    actions = NoopActions,
                )
            }
            onNodeWithText("notes.md").assertExists()
            onNodeWithText("diagram.png").assertExists()
        }

    @Test
    fun everyClickableNodeAnnouncesSomething() =
        runComposeUiTest {
            setContent {
                FilesScreen(
                    state = FilesUiState(currentPath = "/opt/data/seed", entries = entries),
                    actions = NoopActions,
                )
            }
            val clickable = onAllNodes(hasClickAction()).fetchSemanticsNodes()
            assertTrue(clickable.isNotEmpty(), "expected at least one clickable node")
            val unnamed =
                clickable.filter { node ->
                    val description =
                        node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                    val text = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                    description.isEmpty() && text.isEmpty()
                }
            assertTrue(
                unnamed.isEmpty(),
                "clickable nodes with no name: " +
                    unnamed.size.toString(),
            )
        }
}
