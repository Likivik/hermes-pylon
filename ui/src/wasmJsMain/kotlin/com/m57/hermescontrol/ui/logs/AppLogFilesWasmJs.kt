package com.m57.hermescontrol.ui.logs

/** The browser build has no log directory, so there is nothing to upload. */
actual suspend fun readAppLogFiles(): List<AppLogFile> = emptyList()
