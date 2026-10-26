package com.m57.hermescontrol.ui.common

import java.text.NumberFormat
import java.util.Locale

actual fun formatIntegerGrouped(value: Long): String =
    NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)
