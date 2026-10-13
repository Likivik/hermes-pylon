package com.m57.hermescontrol.concurrent

/**
 * Concurrent collections and atomics for shared code.
 *
 * `commonMain` may not import `java.util.concurrent`, and no multiplatform
 * library provides these types with the same semantics, so the Kotlin docs'
 * endorsed route is used: declare the type here, satisfy it with the existing
 * platform type in a target source set. The `jvmMain`/`androidMain` actuals are
 * the JDK types themselves, so behaviour is identical to the
 * `java.util.concurrent` code this replaced.
 *
 * The map is a factory rather than an `expect class`: the JDK
 * `ConcurrentHashMap`'s mapped supertypes do not line up with a `MutableMap`
 * expect declaration, so `actual typealias` is rejected for it. Its static type
 * is plain `MutableMap`, which is not quite enough: the one operation common code
 * lacks is supplied as an `expect` extension (`removeIfValue`).
 */
expect fun <K, V> concurrentMutableMap(): MutableMap<K, V>

/**
 * Removes [key] only while it still maps to [value].
 *
 * The two-argument `MutableMap.remove(key, value)` is a JVM default method with
 * no common declaration, so `:shared` compiled on the JVM only because the map's
 * static type resolves to `java.util.Map` there. Call sites relied on it being
 * atomic, so each target supplies its own: the JDK's atomic remove on JVM and
 * Android, a plain check-then-act where there are no threads to race with.
 */
expect fun <K, V> MutableMap<K, V>.removeIfValue(
    key: K,
    value: V,
): Boolean

expect class ConcurrentLinkedQueue<E>() : MutableCollection<E> {
    override val size: Int

    override fun isEmpty(): Boolean

    override fun contains(element: E): Boolean

    override fun containsAll(elements: Collection<E>): Boolean

    override fun iterator(): MutableIterator<E>

    override fun add(element: E): Boolean

    override fun remove(element: E): Boolean

    override fun addAll(elements: Collection<E>): Boolean

    override fun removeAll(elements: Collection<E>): Boolean

    override fun retainAll(elements: Collection<E>): Boolean

    override fun clear()

    fun poll(): E?

    fun peek(): E?
}

expect class AtomicInteger(value: Int) {
    fun get(): Int

    fun set(value: Int)

    fun incrementAndGet(): Int
}

expect class AtomicBoolean(value: Boolean) {
    fun get(): Boolean

    fun set(value: Boolean)

    fun compareAndSet(
        expect: Boolean,
        update: Boolean,
    ): Boolean
}
