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
 * is plain `MutableMap`, which covers every call site.
 */
expect fun <K, V> concurrentMutableMap(): MutableMap<K, V>

expect class ConcurrentLinkedQueue<E>() : MutableCollection<E> {
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
