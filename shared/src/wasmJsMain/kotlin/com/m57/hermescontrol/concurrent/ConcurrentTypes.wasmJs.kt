package com.m57.hermescontrol.concurrent

/**
 * wasmJs actuals: the browser has one thread, so every structure here is a plain
 * collection and the atomics are ordinary fields. This is not a weaker lock — it
 * is the absence of the problem the JVM actuals solve.
 *
 * A `wasmJs` target must stay declared even while nothing runs on it: without a
 * non-JVM target, `compileCommonMainKotlinMetadata` is SKIPPED and `commonMain`
 * is only compiled per target against the JVM stdlib.
 */

actual fun <K, V> concurrentMutableMap(): MutableMap<K, V> = LinkedHashMap()

actual fun <K, V> MutableMap<K, V>.removeIfValue(
    key: K,
    value: V,
): Boolean =
    if (this[key] == value) {
        remove(key)
        true
    } else {
        false
    }

actual class ConcurrentLinkedQueue<E> actual constructor() : MutableCollection<E> {
    private val delegate = ArrayDeque<E>()

    actual override val size: Int get() = delegate.size

    actual override fun isEmpty(): Boolean = delegate.isEmpty()

    actual override fun contains(element: E): Boolean = delegate.contains(element)

    actual override fun containsAll(elements: Collection<E>): Boolean = delegate.containsAll(elements)

    actual override fun iterator(): MutableIterator<E> = delegate.iterator()

    actual override fun add(element: E): Boolean = delegate.add(element)

    actual override fun remove(element: E): Boolean = delegate.remove(element)

    actual override fun addAll(elements: Collection<E>): Boolean = delegate.addAll(elements)

    actual override fun removeAll(elements: Collection<E>): Boolean = delegate.removeAll(elements)

    actual override fun retainAll(elements: Collection<E>): Boolean = delegate.retainAll(elements)

    actual override fun clear() = delegate.clear()

    actual fun poll(): E? = delegate.removeFirstOrNull()

    actual fun peek(): E? = delegate.firstOrNull()
}

actual class AtomicInteger actual constructor(value: Int) {
    private var current = value

    actual fun get(): Int = current

    actual fun set(value: Int) {
        current = value
    }

    actual fun incrementAndGet(): Int = ++current
}

actual class AtomicBoolean actual constructor(value: Boolean) {
    private var current = value

    actual fun get(): Boolean = current

    actual fun set(value: Boolean) {
        current = value
    }

    actual fun compareAndSet(
        expect: Boolean,
        update: Boolean,
    ): Boolean {
        if (current != expect) return false
        current = update
        return true
    }
}
