package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.local.ChatMessageDao
import com.m57.hermescontrol.data.local.ChatMessageEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ChatPersistenceRepositoryTest {
    private suspend fun ChatPersistenceRepository.persistMessage(
        message: ChatMessage,
        sessionId: String,
    ): Boolean = persistMessage(message, sessionId, replacementGeneration(sessionId))

    private suspend fun ChatPersistenceRepository.persistMessages(
        messages: List<ChatMessage>,
        sessionId: String,
    ): Boolean = persistMessages(messages, sessionId, replacementGeneration(sessionId))

    @Test
    fun acceptedWritesAreFencedBeforeFifoRegistration() =
        runBlocking {
            val dao = RacingDao()
            val repository = ChatPersistenceRepository(dao)
            val releasePreUndo = CompletableDeferred<Unit>()
            val acceptedBeforeUndo = repository.replacementGeneration("session-a")
            val preUndoWrite =
                async {
                    releasePreUndo.await()
                    repository.persistMessage(message("pre-undo"), "session-a", acceptedBeforeUndo)
                }

            repository.invalidateReplacementWrites("session-a")
            val acceptedAfterUndo = repository.replacementGeneration("session-a")
            assertTrue(
                repository.replaceMessagesIfCurrent(
                    listOf(message("replacement")),
                    "session-a",
                    acceptedAfterUndo,
                ),
            )
            assertTrue(repository.persistMessage(message("post-undo"), "session-a", acceptedAfterUndo))
            val unrelatedRevision = repository.replacementGeneration("session-b")
            assertTrue(repository.persistMessage(message("unrelated"), "session-b", unrelatedRevision))

            releasePreUndo.complete(Unit)
            assertFalse(preUndoWrite.await())
            assertEquals(listOf("replacement", "post-undo"), dao.contents("session-a"))
            assertEquals(listOf("unrelated"), dao.contents("session-b"))
        }

    @Test
    fun earlierRegisteredWriteCompletesBeforeLaterReplacement() =
        runBlocking {
            val firstRegistered = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val secondRegistered = CompletableDeferred<Unit>()
            var registration = 0
            val dao = RacingDao()
            val repository =
                ChatPersistenceRepository(
                    dao = dao,
                    operationRegistrationHook =
                        OperationRegistrationHook {
                            when (++registration) {
                                1 -> {
                                    firstRegistered.complete(Unit)
                                    releaseFirst.await()
                                }
                                2 -> secondRegistered.complete(Unit)
                            }
                        },
                )

            val earlierWrite = async { repository.persistMessage(message("earlier"), "session-a") }
            firstRegistered.await()
            val laterReplacement =
                async {
                    repository.replaceMessagesIfCurrent(
                        listOf(message("replacement")),
                        "session-a",
                        repository.replacementGeneration("session-a"),
                    )
                }
            secondRegistered.await()

            assertEquals(emptyList<String>(), dao.contents("session-a"))
            releaseFirst.complete(Unit)
            earlierWrite.await()
            assertTrue(laterReplacement.await())
            assertEquals(listOf("replacement"), dao.contents("session-a"))
            assertEquals(0, repository.queuedSessionCount())
        }

    @Test
    fun cancelledOperationDoesNotStrandRegisteredSuccessor() =
        runBlocking {
            val firstRegistered = CompletableDeferred<Unit>()
            val secondRegistered = CompletableDeferred<Unit>()
            var registration = 0
            val dao = RacingDao()
            val repository =
                ChatPersistenceRepository(
                    dao = dao,
                    operationRegistrationHook =
                        OperationRegistrationHook {
                            when (++registration) {
                                1 -> {
                                    firstRegistered.complete(Unit)
                                    awaitCancellation()
                                }
                                2 -> secondRegistered.complete(Unit)
                            }
                        },
                )

            val cancelled = async { repository.persistMessage(message("cancelled"), "session-a") }
            firstRegistered.await()
            val successor = async { repository.persistMessage(message("successor"), "session-a") }
            secondRegistered.await()
            cancelled.cancel()
            try {
                cancelled.await()
            } catch (_: CancellationException) {
                // Expected: cancellation belongs to this caller only.
            }
            successor.await()

            assertEquals(listOf("successor"), dao.contents("session-a"))
            assertEquals(0, repository.queuedSessionCount())
        }

    @Test
    fun cancelledWaiterDoesNotLetSuccessorOvertakeUnfinishedPredecessor() =
        runBlocking {
            val registrations = List(3) { CompletableDeferred<Unit>() }
            val releasePredecessor = CompletableDeferred<Unit>()
            var registration = 0
            val dao = RacingDao()
            val repository =
                ChatPersistenceRepository(
                    dao = dao,
                    operationRegistrationHook =
                        OperationRegistrationHook {
                            val index = registration++
                            registrations[index].complete(Unit)
                            if (index == 0) releasePredecessor.await()
                        },
                )

            val predecessor = async { repository.persistMessage(message("predecessor"), "session-a") }
            registrations[0].await()
            val cancelledWaiter = async { repository.persistMessage(message("cancelled"), "session-a") }
            registrations[1].await()
            val successor = async { repository.persistMessage(message("successor"), "session-a") }
            registrations[2].await()

            cancelledWaiter.cancel()
            assertFalse(successor.isCompleted)
            assertEquals(emptyList<String>(), dao.contents("session-a"))

            releasePredecessor.complete(Unit)
            predecessor.await()
            try {
                cancelledWaiter.await()
            } catch (_: CancellationException) {
                // Expected: its queue link remains until the predecessor finishes.
            }
            successor.await()

            assertEquals(listOf("predecessor", "successor"), dao.contents("session-a"))
            assertEquals(0, repository.queuedSessionCount())
        }

    @Test
    fun failedOperationPropagatesAndQueueContinues() =
        runBlocking {
            val dao = RacingDao(failContent = "fails")
            val repository = ChatPersistenceRepository(dao)

            try {
                repository.persistMessage(message("fails"), "session-a")
                throw AssertionError("Expected operation failure")
            } catch (error: IllegalStateException) {
                assertEquals("planned failure", error.message)
            }
            repository.persistMessage(message("successor"), "session-a")

            assertEquals(listOf("successor"), dao.contents("session-a"))
            assertEquals(0, repository.queuedSessionCount())
        }

    @Test
    fun writeStartedBeforeReplacementCannotResurrectMessage() {
        val dao = RacingDao(blockContent = "stale")
        val repository = ChatPersistenceRepository(dao)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val staleWrite =
                executor.submit {
                    runBlocking {
                        repository.persistMessage(
                            message("stale"),
                            "session-a",
                        )
                    }
                }
            assertTrue(dao.blockedWriteStarted.await(5, TimeUnit.SECONDS))
            val replacementStarted = CountDownLatch(1)
            val replacement =
                executor.submit<Boolean> {
                    replacementStarted.countDown()
                    runBlocking {
                        repository.replaceMessagesIfCurrent(
                            listOf(message("replacement")),
                            "session-a",
                            repository.replacementGeneration("session-a"),
                        )
                    }
                }
            assertTrue(replacementStarted.await(5, TimeUnit.SECONDS))
            val replacementCompletedBeforeRelease =
                try {
                    replacement.get(1, TimeUnit.SECONDS)
                } catch (_: TimeoutException) {
                    null
                }

            dao.allowBlockedWrite.countDown()
            staleWrite.get(5, TimeUnit.SECONDS)
            assertTrue(replacementCompletedBeforeRelease ?: replacement.get(5, TimeUnit.SECONDS))

            assertEquals(listOf("replacement"), dao.contents("session-a"))
        } finally {
            dao.allowBlockedWrite.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun batchWriteStartedBeforeReplacementCannotResurrectMessages() {
        val dao = RacingDao(blockContent = "stale batch")
        val repository = ChatPersistenceRepository(dao)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val staleWrite =
                executor.submit {
                    runBlocking {
                        repository.persistMessages(listOf(message("stale batch"), message("also stale")), "session-a")
                    }
                }
            assertTrue(dao.blockedWriteStarted.await(5, TimeUnit.SECONDS))
            val replacementStarted = CountDownLatch(1)
            val replacement =
                executor.submit<Boolean> {
                    replacementStarted.countDown()
                    runBlocking {
                        repository.replaceMessagesIfCurrent(
                            listOf(message("replacement")),
                            "session-a",
                            repository.replacementGeneration("session-a"),
                        )
                    }
                }
            assertTrue(replacementStarted.await(5, TimeUnit.SECONDS))
            val replacementCompletedBeforeRelease =
                try {
                    replacement.get(1, TimeUnit.SECONDS)
                } catch (_: TimeoutException) {
                    null
                }

            dao.allowBlockedWrite.countDown()
            staleWrite.get(5, TimeUnit.SECONDS)
            assertTrue(replacementCompletedBeforeRelease ?: replacement.get(5, TimeUnit.SECONDS))

            assertEquals(listOf("replacement"), dao.contents("session-a"))
        } finally {
            dao.allowBlockedWrite.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun writeStartedAfterReplacementIsRetained() =
        runBlocking {
            val dao = RacingDao()
            val repository = ChatPersistenceRepository(dao)

            assertTrue(
                repository.replaceMessagesIfCurrent(
                    listOf(message("replacement")),
                    "session-a",
                    repository.replacementGeneration("session-a"),
                ),
            )
            repository.persistMessage(message("new message"), "session-a")

            assertEquals(listOf("replacement", "new message"), dao.contents("session-a"))
        }

    @Test
    fun blockedSessionDoesNotBlockUnrelatedSession() {
        val dao = RacingDao(blockContent = "blocked")
        val repository = ChatPersistenceRepository(dao)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val blockedWrite =
                executor.submit {
                    runBlocking {
                        repository.persistMessage(
                            message("blocked"),
                            "session-a",
                        )
                    }
                }
            assertTrue(dao.blockedWriteStarted.await(5, TimeUnit.SECONDS))

            val unrelated =
                executor.submit<Boolean> {
                    runBlocking {
                        repository.replaceMessagesIfCurrent(
                            listOf(message("other replacement")),
                            "session-b",
                            repository.replacementGeneration("session-b"),
                        )
                    }
                }
            assertTrue(unrelated.get(5, TimeUnit.SECONDS))
            assertEquals(listOf("other replacement"), dao.contents("session-b"))

            dao.allowBlockedWrite.countDown()
            blockedWrite.get(5, TimeUnit.SECONDS)
        } finally {
            dao.allowBlockedWrite.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun singleMessageWriteAndInvalidationAreAtomic() {
        val dao = RacingDao(blockContent = "blocked")
        val repository = ChatPersistenceRepository(dao)
        val revision = repository.replacementGeneration("session-a")
        val executor = Executors.newFixedThreadPool(2)

        try {
            val write =
                executor.submit<Boolean> {
                    runBlocking {
                        repository.persistMessage(message("blocked"), "session-a", revision)
                    }
                }
            assertTrue(dao.blockedWriteStarted.await(5, TimeUnit.SECONDS))
            val invalidationFinished = CountDownLatch(1)
            executor.submit {
                repository.invalidateReplacementWrites("session-a")
                invalidationFinished.countDown()
            }

            assertFalse(invalidationFinished.await(100, TimeUnit.MILLISECONDS))
            dao.allowBlockedWrite.countDown()
            assertTrue(write.get(5, TimeUnit.SECONDS))
            assertTrue(invalidationFinished.await(5, TimeUnit.SECONDS))
            assertFalse(
                runBlocking {
                    repository.persistMessage(message("stale"), "session-a", revision)
                },
            )
            assertEquals(listOf("blocked"), dao.contents("session-a"))
        } finally {
            dao.allowBlockedWrite.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun replacementAndInvalidationAreAtomic() {
        val dao = RacingDao(blockReplacement = true)
        val repository = ChatPersistenceRepository(dao)
        val generation = repository.replacementGeneration("session-a")
        val executor = Executors.newFixedThreadPool(2)

        try {
            val replacement =
                executor.submit<Boolean> {
                    runBlocking {
                        repository.replaceMessagesIfCurrent(listOf(message("current")), "session-a", generation)
                    }
                }
            assertTrue(dao.blockedReplacementStarted.await(5, TimeUnit.SECONDS))
            val invalidationFinished = CountDownLatch(1)
            executor.submit {
                repository.invalidateReplacementWrites("session-a")
                invalidationFinished.countDown()
            }

            assertFalse(invalidationFinished.await(100, TimeUnit.MILLISECONDS))
            dao.allowBlockedReplacement.countDown()
            assertTrue(replacement.get(5, TimeUnit.SECONDS))
            assertTrue(invalidationFinished.await(5, TimeUnit.SECONDS))
            assertFalse(
                runBlocking {
                    repository.replaceMessagesIfCurrent(listOf(message("stale")), "session-a", generation)
                },
            )
        } finally {
            dao.allowBlockedReplacement.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun blockedReplacementAndItsInvalidationDoNotBlockAnotherSession() {
        val dao = RacingDao(blockReplacementSessionId = "session-a")
        val repository = ChatPersistenceRepository(dao)
        val generationA = repository.replacementGeneration("session-a")
        val generationB = repository.replacementGeneration("session-b")
        val executor = Executors.newFixedThreadPool(3)

        try {
            val blocked =
                executor.submit<Boolean> {
                    runBlocking {
                        repository.replaceMessagesIfCurrent(listOf(message("current-a")), "session-a", generationA)
                    }
                }
            assertTrue(dao.blockedReplacementStarted.await(5, TimeUnit.SECONDS))
            val invalidationFinished = CountDownLatch(1)
            executor.submit {
                repository.invalidateReplacementWrites("session-a")
                invalidationFinished.countDown()
            }
            assertFalse(invalidationFinished.await(100, TimeUnit.MILLISECONDS))
            assertTrue(
                runBlocking {
                    repository.replaceMessagesIfCurrent(listOf(message("current-b")), "session-b", generationB)
                },
            )
            assertEquals(listOf("current-b"), dao.contents("session-b"))
            dao.allowBlockedReplacement.countDown()
            assertTrue(blocked.get(5, TimeUnit.SECONDS))
            assertTrue(invalidationFinished.await(5, TimeUnit.SECONDS))
            assertFalse(
                runBlocking {
                    repository.replaceMessagesIfCurrent(listOf(message("stale-a")), "session-a", generationA)
                },
            )
            assertTrue(
                runBlocking {
                    repository.replaceMessagesIfCurrent(listOf(message("still-current-b")), "session-b", generationB)
                },
            )
        } finally {
            dao.allowBlockedReplacement.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun replacementGenerationsAreIsolatedAndNeverReset() {
        val repository = ChatPersistenceRepository(RacingDao())
        val initialA = repository.replacementGeneration("session-a")
        val initialB = repository.replacementGeneration("session-b")
        repository.invalidateReplacementWrites("session-a")
        val nextA = repository.replacementGeneration("session-a")
        repository.invalidateReplacementWrites("session-a")

        assertEquals(initialA + 2, repository.replacementGeneration("session-a"))
        assertEquals(initialB, repository.replacementGeneration("session-b"))
        assertFalse(
            runBlocking {
                repository.replaceMessagesIfCurrent(listOf(message("stale-a")), "session-a", initialA)
            },
        )
        assertFalse(
            runBlocking {
                repository.replaceMessagesIfCurrent(listOf(message("also-stale-a")), "session-a", nextA)
            },
        )
    }

    @Test
    fun pagedPersistenceRejectsInvalidatedTranscriptRevision() {
        val dao = RacingDao()
        val repository = ChatPersistenceRepository(dao)
        val staleRevision = repository.replacementGeneration("session-a")
        repository.invalidateReplacementWrites("session-a")

        assertFalse(
            runBlocking {
                repository.persistMessagesIfCurrent(listOf(message("stale page")), "session-a", staleRevision)
            },
        )
        val currentRevision = repository.replacementGeneration("session-a")
        assertTrue(
            runBlocking {
                repository.persistMessagesIfCurrent(listOf(message("current page")), "session-a", currentRevision)
            },
        )
        assertEquals(listOf("current page"), dao.contents("session-a"))
    }

    @Test
    fun pagedPersistenceChecksOwnerInsideSerializedWrite() {
        val dao = RacingDao(blockContent = "blocker")
        val repository = ChatPersistenceRepository(dao)
        val revision = repository.replacementGeneration("session-a")
        val executor = Executors.newSingleThreadExecutor()
        var ownerCurrent = true
        try {
            val blocker = executor.submit { runBlocking { repository.persistMessage(message("blocker"), "session-a") } }
            assertTrue(dao.blockedWriteStarted.await(5, TimeUnit.SECONDS))
            val stale =
                executor.submit<Boolean> {
                    runBlocking {
                        repository.persistMessagesIfCurrent(
                            listOf(message("stale page")),
                            "session-a",
                            revision,
                        ) { ownerCurrent }
                    }
                }
            ownerCurrent = false
            dao.allowBlockedWrite.countDown()

            blocker.get(5, TimeUnit.SECONDS)
            assertFalse(stale.get(5, TimeUnit.SECONDS))
            assertEquals(listOf("blocker"), dao.contents("session-a"))
        } finally {
            dao.allowBlockedWrite.countDown()
            executor.shutdownNow()
        }
    }

    private fun message(content: String) =
        ChatMessage(
            id = content,
            role = MessageRole.ASSISTANT,
            content = content,
        )

    private class RacingDao(
        private val blockContent: String? = null,
        private val blockReplacement: Boolean = false,
        private val blockReplacementSessionId: String? = null,
        private val failContent: String? = null,
    ) : ChatMessageDao {
        private val messages = ConcurrentHashMap<String, MutableList<ChatMessageEntity>>()
        val blockedWriteStarted = CountDownLatch(1)
        val allowBlockedWrite = CountDownLatch(1)
        val blockedReplacementStarted = CountDownLatch(1)
        val allowBlockedReplacement = CountDownLatch(1)

        override suspend fun sessionExists(sessionId: String): Boolean = messages.containsKey(sessionId)

        override suspend fun getMessagesForSession(sessionId: String): List<ChatMessageEntity> =
            synchronized(messages) { messages[sessionId]?.toList().orEmpty() }

        override suspend fun upsert(message: ChatMessageEntity) {
            blockWriteIfNeeded(listOf(message))
            failIfNeeded(listOf(message))
            store(listOf(message))
        }

        override fun upsertAll(messages: List<ChatMessageEntity>) {
            blockWriteIfNeeded(messages)
            failIfNeeded(messages)
            store(messages)
        }

        override fun deleteMessagesForSession(sessionId: String) {
            synchronized(messages) { messages.remove(sessionId) }
        }

        override fun replaceMessagesForSession(
            sessionId: String,
            messages: List<ChatMessageEntity>,
        ) {
            if (blockReplacement || sessionId == blockReplacementSessionId) {
                blockedReplacementStarted.countDown()
                assertTrue(allowBlockedReplacement.await(5, TimeUnit.SECONDS))
            }
            synchronized(this.messages) { this.messages[sessionId] = messages.toMutableList() }
        }

        fun contents(sessionId: String): List<String> =
            synchronized(messages) { messages[sessionId]?.map { it.content }.orEmpty() }

        private fun blockWriteIfNeeded(messages: List<ChatMessageEntity>) {
            if (messages.any { it.content == blockContent }) {
                blockedWriteStarted.countDown()
                assertTrue(allowBlockedWrite.await(5, TimeUnit.SECONDS))
            }
        }

        private fun failIfNeeded(messages: List<ChatMessageEntity>) {
            if (messages.any { it.content == failContent }) throw IllegalStateException("planned failure")
        }

        private fun store(newMessages: List<ChatMessageEntity>) {
            synchronized(messages) {
                newMessages.forEach { message ->
                    val sessionMessages = messages.getOrPut(message.sessionId) { mutableListOf() }
                    sessionMessages.removeAll { it.id == message.id }
                    sessionMessages += message
                }
            }
        }
    }
}
