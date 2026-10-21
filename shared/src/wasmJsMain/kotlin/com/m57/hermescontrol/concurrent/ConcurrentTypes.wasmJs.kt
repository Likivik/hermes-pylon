package com.m57.hermescontrol.concurrent

/**
 * wasmJs actuals: the browser has one thread, so every structure here is a plain
 * collection and the atomics are ordinary fields. This is not a weaker lock — it is the
 * absence of the problem the JVM actuals solve.
 *
 * A `wasmJs` target must stay declared even while nothing runs on it: without a non-JVM
 * target, `compileCommonMainKotlinMetadata` is SKIPPED and `commonMain` is only compiled
 * per target against the JVM stdlib.
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

actual fun atomicInteger(initial: Int): AtomicInteger = WasmAtomicInteger(initial)

actual fun atomicBoolean(initial: Boolean): AtomicBoolean = WasmAtomicBoolean(initial)

actual fun <E> concurrentLinkedQueue(): ConcurrentLinkedQueue<E> = WasmConcurrentLinkedQueue()

private class WasmAtomicInteger(
    private var current: Int,
) : AtomicInteger {
    override fun get(): Int = current

    override fun set(value: Int) {
        current = value
    }

    override fun incrementAndGet(): Int = ++current
}

private class WasmAtomicBoolean(
    private var current: Boolean,
) : AtomicBoolean {
    override fun get(): Boolean = current

    override fun set(value: Boolean) {
        current = value
    }

    override fun compareAndSet(
        expect: Boolean,
        update: Boolean,
    ): Boolean {
        if (current != expect) return false
        current = update
        return true
    }
}

private class WasmConcurrentLinkedQueue<E> : ConcurrentLinkedQueue<E> {
    private val delegate = ArrayDeque<E>()

    override val size: Int get() = delegate.size

    override fun isEmpty(): Boolean = delegate.isEmpty()

    override fun contains(element: E): Boolean = delegate.contains(element)

    override fun containsAll(elements: Collection<E>): Boolean = delegate.containsAll(elements)

    override fun iterator(): MutableIterator<E> = delegate.iterator()

    override fun add(element: E): Boolean = delegate.add(element)

    override fun remove(element: E): Boolean = delegate.remove(element)

    override fun addAll(elements: Collection<E>): Boolean = delegate.addAll(elements)

    override fun removeAll(elements: Collection<E>): Boolean = delegate.removeAll(elements)

    override fun retainAll(elements: Collection<E>): Boolean = delegate.retainAll(elements)

    override fun clear() = delegate.clear()

    override fun poll(): E? = delegate.removeFirstOrNull()

    override fun peek(): E? = delegate.firstOrNull()
}
