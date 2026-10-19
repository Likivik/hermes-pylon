package com.m57.hermescontrol.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.m57.hermescontrol.data.model.Attachment
import com.m57.hermescontrol.data.model.AttachmentSource
import com.m57.hermescontrol.data.remote.GatewayFile
import com.m57.hermescontrol.data.remote.GatewayFileClient
import com.m57.hermescontrol.data.remote.GatewayFileResult
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.attachment_chooser_open
import com.m57.hermescontrol.ui.common.SecureMediaOpenRoute
import com.m57.hermescontrol.ui.common.StringResolver
import com.m57.hermescontrol.ui.common.routeAttachmentOpen
import java.io.File

/**
 * Opens attachments on Android: a content URI goes straight to the system viewer,
 * gateway media is fetched into the media cache first and then handed over through
 * FileProvider.
 *
 * This is the platform half of the tap path — deciding *what* to do lives in
 * [routeAttachmentOpen], which is shared.
 */
class AndroidAttachmentOpener(
    context: Context,
    private val resolver: StringResolver,
    private val cacheDir: String,
) : AttachmentOpener {
    private val appContext = context.applicationContext

    override suspend fun open(
        attachment: Attachment,
        onProgress: (String?) -> Unit,
    ): AttachmentOpenOutcome {
        when (val route = routeAttachmentOpen(attachment)) {
            is SecureMediaOpenRoute.Player -> return AttachmentOpenOutcome.PlayMedia(route.request)
            SecureMediaOpenRoute.OpenLocal, SecureMediaOpenRoute.DownloadAndOpen -> Unit
        }
        if (attachment.source == AttachmentSource.LOCAL) {
            return openLocal(attachment)
        }
        val path =
            attachment.gatewayPath ?: return AttachmentOpenOutcome.Failed(
                AttachmentOpenError.MISSING_GATEWAY_PATH,
                attachment.name,
            )
        onProgress(path)
        return try {
            when (val result = GatewayFileClient.fetch(path, File(cacheDir, "gateway_media"))) {
                is GatewayFileResult.Success -> {
                    openBytes(result.file)
                    AttachmentOpenOutcome.Handled
                }

                GatewayFileResult.NotFound ->
                    AttachmentOpenOutcome.Failed(AttachmentOpenError.NOT_FOUND, attachment.name)

                GatewayFileResult.Forbidden ->
                    AttachmentOpenOutcome.Failed(AttachmentOpenError.ACCESS_DENIED, attachment.name)

                GatewayFileResult.TooLarge ->
                    AttachmentOpenOutcome.Failed(AttachmentOpenError.TOO_LARGE, attachment.name)

                GatewayFileResult.Unauthorized ->
                    AttachmentOpenOutcome.Failed(AttachmentOpenError.SESSION_EXPIRED, attachment.name)

                is GatewayFileResult.Failure ->
                    AttachmentOpenOutcome.Failed(AttachmentOpenError.OPEN, attachment.name)
            }
        } finally {
            onProgress(null)
        }
    }

    private suspend fun openLocal(attachment: Attachment): AttachmentOpenOutcome {
        val parsed = Uri.parse(attachment.uri)
        val viewAttempt = runCatching { openWithView(parsed, attachment.mimeType) }
        if (viewAttempt.isSuccess) return AttachmentOpenOutcome.Handled
        // Local cache-copy fallback: some providers refuse to grant a read to a
        // foreign viewer, so copy the bytes into our own cache and share that.
        val cached =
            runCatching {
                val bytes =
                    appContext.contentResolver.openInputStream(parsed)?.use { it.readBytes() }
                        ?: error("Could not read local attachment")
                val dir = File(cacheDir, "local_attachments").apply { mkdirs() }
                val file = File(dir, "${GatewayFileClient.sha256(attachment.uri)}.media")
                file.writeBytes(bytes)
                GatewayFile(
                    name = attachment.name,
                    mimeType = attachment.mimeType,
                    cacheFile = file,
                )
            }.getOrElse { return AttachmentOpenOutcome.Failed(AttachmentOpenError.OPEN, attachment.name) }
        openBytes(cached)
        return AttachmentOpenOutcome.Handled
    }

    private suspend fun openBytes(file: GatewayFile) {
        val uri =
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file.cacheFile,
            )
        openWithView(uri, file.mimeType)
    }

    /** Fire an ACTION_VIEW intent, falling back to a chooser when nothing handles the type. */
    private suspend fun openWithView(
        uri: Uri,
        mimeType: String,
    ) {
        val viewIntent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        try {
            appContext.startActivity(viewIntent)
        } catch (_: Throwable) {
            val fallback =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            val title = resolver.get(Res.string.attachment_chooser_open)
            appContext.startActivity(
                Intent.createChooser(fallback, title).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }
}
