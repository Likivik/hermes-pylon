package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.ManagedDirectoryCreate
import com.m57.hermescontrol.data.model.ManagedFileActionResponse
import com.m57.hermescontrol.data.model.ManagedFileDelete
import com.m57.hermescontrol.data.model.ManagedFilesListResponse
import com.m57.hermescontrol.data.remote.GatewayResponse

/**
 * What the files screen needs from the gateway: 4 calls of the 150 it offers.
 *
 * Replaces the `SessionsGateway` wrapper added in #257. The wrapper duplicated signatures for no gain; this names
 * only what this screen calls, and `FilesSourceOf` is the production adapter.
 */
interface FilesSource {
    suspend fun listManagedFiles(path: String? = null): GatewayResponse<ManagedFilesListResponse>

    suspend fun createManagedDirectory(body: ManagedDirectoryCreate): GatewayResponse<ManagedFileActionResponse>

    suspend fun deleteManagedFile(body: ManagedFileDelete): GatewayResponse<ManagedFileActionResponse>

    suspend fun uploadManagedFileStream(
        path: String,
        overwrite: Boolean,
        fileName: String,
        content: ByteArray,
        mimeType: String? = null,
    ): GatewayResponse<ManagedFileActionResponse>
}
