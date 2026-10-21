package com.m57.hermescontrol.concurrent

/**
 * Concurrent collections and atomics for shared code.
 *
 * `commonMain` may not import `java.util.concurrent`, and no multiplatform library
 * provides these with the same semantics, so each capability is a *factory* returning a
 * plain common interface. Factories are chosen deliberately over `expect class`: classes
 * are still Beta on Kotlin 2.4.x and every build emitted 18 warnings for them, while
 * `expect fun` has been Stable since 1.9.20. The JVM and Android implementations wrap the
 * very same JDK types the code used before, so behaviour is unchanged.
 *
 * The map follows the same shape for a different reason: the JDK `ConcurrentHashMap`'s
 * mapped supertypes do not line up with a `MutableMap` expect declaration, so
 * `actual typealias` is rejected for it. Its static type is plain `MutableMap`, which is
 * not quite enough: the one operation common code lacks is supplied as an `expect`
 * extension ([removeIfValue]).
 */
expect fun <K, V> concurrentMutableMap(): MutableMap<K, V>

/**
 * Removes [key] only while it still maps to [value].
 *
 * The two-argument `MutableMap.remove(key, value)` is a JVM default method with no common
 * declaration, so `:shared` compiled on the JVM only because the map's static type
 * resolves to `java.util.Map` there. Call sites relied on it being atomic, so each target
 * supplies its own: the JDK's atomic remove on JVM and Android, a plain check-then-act
 * where there are no threads to race with.
 */
expect fun <K, V> MutableMap<K, V>.removeIfValue(
    key: K,
    value: V,
): Boolean

/** A counter that is safe to mutate from more than one thread. */
interface AtomicInteger {
    fun get(): Int

    fun set(value: Int)

    fun incrementAndGet(): Int
}

/** A flag that is safe to mutate from more than one thread. */
interface AtomicBoolean {
    fun get(): Boolean

    fun set(value: Boolean)

    fun compareAndSet(
        expect: Boolean,
        update: Boolean,
    ): Boolean
}

/** A FIFO queue that is safe to mutate from more than one thread. */
interface ConcurrentLinkedQueue<E> : MutableCollection<E> {
    fun poll(): E?

    fun peek(): E?
}

expect fun atomicInteger(initial: Int): AtomicInteger

expect fun atomicBoolean(initial: Boolean): AtomicBoolean

expect fun <E> concurrentLinkedQueue(): ConcurrentLinkedQueue<E>
