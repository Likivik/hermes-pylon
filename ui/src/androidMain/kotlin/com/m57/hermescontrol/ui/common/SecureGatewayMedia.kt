package com.m57.hermescontrol.ui.common

import android.media.MediaDataSource
import com.m57.hermescontrol.data.remote.GatewayMediaRangeResult
import com.m57.hermescontrol.data.remote.SeekableGatewayMediaReader
import com.m57.hermescontrol.util.ioDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/** Blocking range mapper used only on MediaPlayer's data-source worker threads. */
class GatewayMediaDataReader(
    private val session: SeekableGatewayMediaReader,
) {
    private val stateLock = Any()
    private val knownSize = AtomicLong(-1L)
    private val activeReads = mutableSetOf<Job>()
    private var closed = false

    fun readAt(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        size: Int,
    ): Int {
        synchronized(stateLock) {
            if (closed) throw IOException("Media data source is closed")
        }
        if (position < 0 || offset < 0 || size < 0 || offset > buffer.size - size) {
            throw IOException("Invalid media read")
        }
        if (size == 0) return 0
        if (knownSize.get() >= 0 && position >= knownSize.get()) return -1
        val requested = size.coerceAtMost(SeekableGatewayMediaReader.MAX_RANGE_BYTES)
        val readJob = Job()
        synchronized(stateLock) {
            if (closed) throw IOException("Media data source is closed")
            activeReads += readJob
        }
        val result =
            try {
                runBlocking(ioDispatcher + readJob) { session.read(position, requested) }
            } catch (_: CancellationException) {
                throw IOException("Media data source is closed")
            } finally {
                synchronized(stateLock) { activeReads -= readJob }
            }
        return synchronized(stateLock) {
            if (closed) throw IOException("Media data source is closed")
            when (result) {
                is GatewayMediaRangeResult.Success -> {
                    result.totalLength?.let(knownSize::set)
                    if (result.bytes.isEmpty()) {
                        -1
                    } else {
                        val count = result.bytes.size.coerceAtMost(size)
                        result.bytes.copyInto(buffer, offset, 0, count)
                        count
                    }
                }

                GatewayMediaRangeResult.Stale -> throw IOException("Playback credentials changed")
                GatewayMediaRangeResult.Unauthorized -> throw IOException("Playback session expired")
                GatewayMediaRangeResult.Forbidden -> throw IOException("Playback access denied")
                GatewayMediaRangeResult.NotFound -> throw IOException("Media was not found")
                GatewayMediaRangeResult.TooLarge -> throw IOException("Media range was too large")
                is GatewayMediaRangeResult.Failure -> throw IOException("Media read failed", result.throwable)
            }
        }
    }

    fun size(): Long = knownSize.get()

    fun close() {
        val reads =
            synchronized(stateLock) {
                if (closed) return
                closed = true
                activeReads.toList()
            }
        reads.forEach(Job::cancel)
    }

    fun isCurrent(): Boolean = synchronized(stateLock) { !closed } && session.isCurrent()
}

class GatewayMediaDataSource(
    session: SeekableGatewayMediaReader,
) : MediaDataSource() {
    private val reader = GatewayMediaDataReader(session)

    override fun readAt(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        size: Int,
    ): Int = reader.readAt(position, buffer, offset, size)

    override fun getSize(): Long = reader.size()

    override fun close() = reader.close()

    fun isCurrent(): Boolean = reader.isCurrent()
}
