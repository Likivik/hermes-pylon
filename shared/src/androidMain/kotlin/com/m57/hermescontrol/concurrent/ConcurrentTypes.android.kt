package com.m57.hermescontrol.concurrent

/**
 * Android actuals for [concurrentMutableMap], [ConcurrentLinkedQueue],
 * [AtomicInteger] and [AtomicBoolean]: the JDK types themselves, so shared code
 * keeps the exact concurrency semantics it had when it imported
 * `java.util.concurrent` directly.
 */
actual fun <K, V> concurrentMutableMap(): MutableMap<K, V> = java.util.concurrent.ConcurrentHashMap()

actual typealias ConcurrentLinkedQueue<E> = java.util.concurrent.ConcurrentLinkedQueue<E>

actual typealias AtomicInteger = java.util.concurrent.atomic.AtomicInteger

actual typealias AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean
