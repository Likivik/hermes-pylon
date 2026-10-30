package com.m57.hermescontrol.ui.logs

import io.kseongbin.stacktrace.CrashLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the crash logger's directory exactly as the logs screen always did. */
actual suspend fun readAppLogFiles(): List<AppLogFile> =
    withContext(Dispatchers.IO as CoroutineDispatcher) {
        val dir = CrashLogger.getLogDirectory() ?: return@withContext emptyList()
        dir
            .listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.lastModified() }
            ?.map { AppLogFile(it.name, it.readBytes()) }
            ?: emptyList()
    }
