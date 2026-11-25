package com.m57.hermescontrol.ui.chat

/*
 * The rules that move ConnectionState forward.
 *
 * The connection block used to be open-coded in three places: one latch in `init`, one pill shown, one pill cleared.
 * Each update is a rule, so each rule is named here and tested. The call sites keep only the ordering they own.
 *
 * Kept on purpose: ConnectionState.connectionStatus is never touched by these rules. The transport status belongs to
 * the socket, and a pill or a latch must not rewrite it. The other two fields are the ones a rule is allowed to move,
 * and every rule moves exactly one of them.
 */

/**
 * Latches the first successful connection.
 *
 * **Rule:** once the socket has connected, the app knows the backend was reachable at least once, so the flag stays
 * true for the rest of the session even after a later drop.
 *
 * **Kept on purpose:** [ConnectionState.connectionStatus] and [ConnectionState.statusPill]. The latch says nothing about
 * the current socket and nothing about what the user is being told.
 */
fun ConnectionState.afterFirstConnected(): ConnectionState = copy(hasConnectedOnce = true)

/***
 * Shows a transient status pill.
 *
 * **Rule:** the latest transient message replaces the pill text. One pill at a time. A later message wins over an
 * earlier one.
 *
 * **Kept on purpose:** [ConnectionState.connectionStatus] and [ConnectionState.hasConnectedOnce]. The pill is a message
 * for the user, not transport state, and it must not undo the first-connect latch.
 */
fun ConnectionState.afterStatusPill(text: String): ConnectionState = copy(statusPill = text)

/***
 * Clears the transient status pill.
 *
 * **Rule:** the pill disappears once its timeout elapses, so no stale text is left behind.
 *
 * **Kept on purpose:** [ConnectionState.connectionStatus] and [ConnectionState.hasConnectedOnce]. Clearing the pill says
 * nothing about the socket and nothing about whether the backend was ever reached.
 */
fun ConnectionState.afterStatusPillCleared(): ConnectionState = copy(statusPill = null)
