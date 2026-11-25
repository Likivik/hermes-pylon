package com.m57.hermescontrol.data.local

import android.content.Context

/** Android entry point: binds the manager to a `Context`-backed platform. */
fun AuthManager.init(context: Context) = init(AndroidAuthPlatform(context))
