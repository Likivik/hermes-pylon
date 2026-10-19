package com.m57.hermescontrol.theme

import com.m57.hermescontrol.data.theme.ThemePreference as DataThemePreference
import com.m57.hermescontrol.data.theme.ThemePreset as DataThemePreset

/**
 * Back-compat re-export of the persistent theme enums. The actual enum
 * constants moved to `:shared` (package `com.m57.hermescontrol.data.theme`)
 * so the data/config layer is platform-neutral, but `Theme.kt` and any
 * pre-Phase-3 caller (including the existing test suite under
 * `app/src/test/.../theme/`) still resolves them via the original
 * `com.m57.hermescontrol.theme.*` path. A typealias preserves the existing
 * imports; the values themselves stay single-sourced in `:shared`.
 */
typealias ThemePreference = DataThemePreference

typealias ThemePreset = DataThemePreset
