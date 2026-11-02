package org.jhaeussler.practicetracker.drummachine.engine

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlaybackRingBufferTest {

    private lateinit var ringBuffer: PlaybackRingBuffer
    private val scratchMeta = MutableEventMetadata()

    @Before
    fun setUp() {
        ringBuffer = PlaybackRingBuffer(capacity = 4) // Small power of 2 for deterministic boundary tests
    }

    @Test
    fun testOfferAndPollPreservesFIFOOrder() {
        ringBuffer.offer(100L, 0, 0, 0, isPrimary = true, isDownbeat = true)
        ringBuffer.offer(200L, 0, 1, 0, isPrimary = true, isDownbeat = false)

        assertEquals(2, ringBuffer.size())
        assertFalse(ringBuffer.isEmpty())

        // First event
        val pos1 = ringBuffer.poll(scratchMeta)
        assertEquals(100L, pos1)
        assertEquals(0, scratchMeta.beatIndex)
        assertTrue(scratchMeta.isDownbeat)
        assertTrue(scratchMeta.isPrimaryBeat)

        // Second event
        val pos2 = ringBuffer.poll(scratchMeta)
        assertEquals(200L, pos2)
        assertEquals(1, scratchMeta.beatIndex)
        assertFalse(scratchMeta.isDownbeat)

        // Empty
        assertEquals(PlaybackRingBuffer.NO_EVENT, ringBuffer.poll(scratchMeta))
        assertTrue(ringBuffer.isEmpty())
    }

    @Test
    fun testPeekDoesNotConsume() {
        ringBuffer.offer(48000L, 0, 2, 1, isPrimary = false, isDownbeat = false)

        assertEquals(48000L, ringBuffer.peekSamplePosition())
        assertEquals(48000L, ringBuffer.peekSamplePosition())
        assertEquals(1, ringBuffer.size())

        val pos = ringBuffer.poll(scratchMeta)
        assertEquals(48000L, pos)
        assertEquals(PlaybackRingBuffer.NO_EVENT, ringBuffer.peekSamplePosition())
    }

    @Test
    fun testBufferDropsEventsWhenFullWithoutCrashing() {
        // Capacity is 4
        assertTrue(ringBuffer.offer(10L,0, 0, 0, true, true))
        assertTrue(ringBuffer.offer(20L,0, 1, 0, true, false))
        assertTrue(ringBuffer.offer(30L,0, 2, 0, true, false))
        assertTrue(ringBuffer.offer(40L,0, 3, 0, true, false))

        assertEquals(4, ringBuffer.size())

        // 5th offer must be rejected safely
        val accepted = ringBuffer.offer(50L, 0, 4, 0, true, false)
        assertFalse("Buffer must drop event when capacity is exceeded", accepted)
        assertEquals(4, ringBuffer.size())

        // Polling 1 item frees up a slot
        assertEquals(10L, ringBuffer.poll(scratchMeta))
        assertEquals(3, ringBuffer.size())

        // Now offer succeeds
        assertTrue(ringBuffer.offer(60L, 0, 4, 0, true, false))
        assertEquals(4, ringBuffer.size())
    }

    @Test
    fun testClearEmptiesBuffer() {
        ringBuffer.offer(10L, 0, 0, 0, true, true)
        ringBuffer.offer(20L, 0, 1, 0, true, false)
        assertEquals(2, ringBuffer.size())

        ringBuffer.clear()

        assertEquals(0, ringBuffer.size())
        assertTrue(ringBuffer.isEmpty())
        assertEquals(PlaybackRingBuffer.NO_EVENT, ringBuffer.peekSamplePosition())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRejectsNonPowerOfTwoCapacity() {
        PlaybackRingBuffer(capacity = 500) // Not a power of 2
    }

    @Test
    fun testDrainReadyEventsDrainsOnlyDueEventsPreservingFutureEvents() {
        val buffer = PlaybackRingBuffer(capacity = 8)

        // Schedule 3 events at samples 100, 200, and 300
        buffer.offer(samplePosition = 100L, 0, beatIndex = 0, subIndex = 0, isPrimary = true, isDownbeat = true)
        buffer.offer(samplePosition = 200L, 0, beatIndex = 1, subIndex = 0, isPrimary = true, isDownbeat = false)
        buffer.offer(samplePosition = 300L, 0, beatIndex = 2, subIndex = 0, isPrimary = true, isDownbeat = false)

        val drainedPositions = mutableListOf<Long>()
        val drainedBeatIndices = mutableListOf<Int>()

        // Act 1: Playback head is at sample 250 (should drain 100 and 200, but leave 300)
        buffer.drainReadyEvents(currentPlayedSample = 250L, scratchMeta) { eventPos, meta ->
            drainedPositions.add(eventPos)
            drainedBeatIndices.add(meta.beatIndex)
        }

        // Assert 1: Exactly 2 events drained in order with correct metadata
        assertEquals(listOf(100L, 200L), drainedPositions)
        assertEquals(listOf(0, 1), drainedBeatIndices)

        // Assert 1: Event 3 remains in the buffer untouched
        assertEquals(1, buffer.size())
        assertEquals(300L, buffer.peekSamplePosition())

        // Act 2: Playback head reaches sample 300 (exact boundary match)
        drainedPositions.clear()
        drainedBeatIndices.clear()

        buffer.drainReadyEvents(currentPlayedSample = 300L, scratchMeta) { eventPos, meta ->
            drainedPositions.add(eventPos)
            drainedBeatIndices.add(meta.beatIndex)
        }

        // Assert 2: Final event drained and buffer is now empty
        assertEquals(listOf(300L), drainedPositions)
        assertEquals(listOf(2), drainedBeatIndices)
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun testDrainReadyEventsHandlesEmptyBufferAndFutureEventsSafely() {
        var invocations = 0

        // 1. Empty buffer check: must terminate immediately without hanging or throwing
        ringBuffer.drainReadyEvents(currentPlayedSample = 5000L, scratchMeta) { _, _ ->
            invocations++
        }
        assertEquals(0, invocations)

        // 2. Future events check: event is at sample 500, but playback head is only at 100
        ringBuffer.offer(samplePosition = 500L, trackIndex = 0, beatIndex = 0, subIndex = 0, isPrimary = true, isDownbeat = true)

        ringBuffer.drainReadyEvents(currentPlayedSample = 100L, scratchMeta) { _, _ ->
            invocations++
        }

        // Must not drain anything
        assertEquals(0, invocations)
        assertEquals(1, ringBuffer.size())
        assertEquals(500L, ringBuffer.peekSamplePosition())
    }
}