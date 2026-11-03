package com.m57.hermescontrol.data.local

import kotlinx.browser.window

actual val defaultServerBaseUrl: String = "${window.location.origin}/"
