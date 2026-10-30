package com.m57.hermescontrol.ui.logs

/** The desktop build writes no crash logs of its own, so there is nothing to upload. */
actual suspend fun readAppLogFiles(): List<AppLogFile> = emptyList()
