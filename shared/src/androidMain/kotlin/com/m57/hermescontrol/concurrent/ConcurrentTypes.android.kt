package com.m57.hermescontrol.concurrent

/**
 * Android actuals for [concurrentMutableMap], [removeIfValue] and the three factories.
 * Each implementation wraps the JDK type the code used before, so shared code keeps the
 * exact concurrency semantics it had when it imported `java.util.concurrent` directly.
 */
actual fun <K, V> concurrentMutableMap(): MutableMap<K, V> = java.util.concurrent.ConcurrentHashMap()

/**
 * `java.util.Map.remove(key, value)` — atomic on `ConcurrentHashMap`, and the exact call
 * the shared code made before it had to name a common operation.
 */
actual fun <K, V> MutableMap<K, V>.removeIfValue(
    key: K,
    value: V,
): Boolean = remove(key, value)

actual fun atomicInteger(initial: Int): AtomicInteger = AndroidAtomicInteger(initial)

actual fun atomicBoolean(initial: Boolean): AtomicBoolean = AndroidAtomicBoolean(initial)

actual fun <E> concurrentLinkedQueue(): ConcurrentLinkedQueue<E> = AndroidConcurrentLinkedQueue()

private class AndroidAtomicInteger(
    initial: Int,
) : AtomicInteger {
    private val delegate = java.util.concurrent.atomic.AtomicInteger(initial)

    override fun get(): Int = delegate.get()

    override fun set(value: Int) {
        delegate.set(value)
    }

    override fun incrementAndGet(): Int = delegate.incrementAndGet()
}

private class AndroidAtomicBoolean(
    initial: Boolean,
) : AtomicBoolean {
    private val delegate = java.util.concurrent.atomic.AtomicBoolean(initial)

    override fun get(): Boolean = delegate.get()

    override fun set(value: Boolean) {
        delegate.set(value)
    }

    override fun compareAndSet(
        expect: Boolean,
        update: Boolean,
    ): Boolean = delegate.compareAndSet(expect, update)
}

private class AndroidConcurrentLinkedQueue<E> : ConcurrentLinkedQueue<E> {
    private val delegate = java.util.concurrent.ConcurrentLinkedQueue<E>()

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

    override fun poll(): E? = delegate.poll()

    override fun peek(): E? = delegate.peek()
}
