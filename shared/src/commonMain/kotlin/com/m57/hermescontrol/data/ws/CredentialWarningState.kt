package com.m57.hermescontrol.data.ws

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the gateway's credential warning without letting a repeated
 * `session.info` event undo an explicit dismissal.
 */
class CredentialWarningState : SynchronizedObject() {
    private val _warning = MutableStateFlow<String?>(null)
    val warning: StateFlow<String?> = _warning.asStateFlow()

    private var dismissedWarning: String? = null

    fun update(reportedWarning: String?) {
        synchronized(this) {
            val warning = reportedWarning?.trim()?.takeIf { it.isNotEmpty() }
            if (warning == null) {
                dismissedWarning = null
                _warning.value = null
                return@synchronized
            }
            if (warning != dismissedWarning) {
                _warning.value = warning
            }
        }
    }

    fun dismiss() {
        synchronized(this) {
            dismissedWarning = _warning.value
            _warning.value = null
        }
    }
}
