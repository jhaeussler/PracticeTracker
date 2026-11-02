package org.jhaeussler.practicetracker.drummachine.engine

/**
 * Pre-allocated, lock-free Single-Producer Single-Consumer (SPSC) ring buffer for passing playback events
 * from the audio thread to the UI thread with zero allocations.
 */
class PlaybackRingBuffer(val capacity: Int = 512)
{
    init {
        require(capacity > 0 && (capacity and (capacity - 1)) == 0) {
            "Capacity must be a power of 2 (e.g. 128, 256, 512, 1024)"
        }
    }

    private val mask = capacity - 1

    private val samplePositions = LongArray(capacity)
    private val beatIndices = IntArray(capacity)
    private val subIndices = IntArray(capacity)
    private val isPrimaryFlags = BooleanArray(capacity)
    private val isDownbeatFlags = BooleanArray(capacity)

    private val trackIndices = IntArray(capacity)

    @Volatile private var head: Long = 0L // Written exclusively by Audio Thread
    @Volatile private var tail: Long = 0L // Read exclusively by UI/Consumer Thread

    companion object {
        const val NO_EVENT: Long = -1L
    }

    /**
     * Pushes an event onto the ring buffer.
     * MUST be called exclusively by the audio thread (Single Producer).
     *
     * @return true if accepted, false if dropped due to consumer lag.
     */
    fun offer(
        samplePosition: Long,
        trackIndex: Int,
        beatIndex: Int,
        subIndex: Int,
        isPrimary: Boolean,
        isDownbeat: Boolean
    ): Boolean
    {
        val currentHead = head
        val currentTail = tail

        // If full, drop the event to protect real-time audio thread execution
        if (currentHead - currentTail >= capacity) {
            return false
        }

        val idx = currentHead.toInt() and mask

        samplePositions[idx] = samplePosition
        trackIndices[idx] = trackIndex
        beatIndices[idx] = beatIndex
        subIndices[idx] = subIndex
        isPrimaryFlags[idx] = isPrimary
        isDownbeatFlags[idx] = isDownbeat

        // Volatile store acts as a Release barrier: ensures array writes
        // are committed before head becomes visible to the consumer.
        head = currentHead + 1
        return true
    }

    /**
     * Inspects the next sample position without advancing the tail.
     * Returns [NO_EVENT] (-1L) if the buffer is empty.
     */
    fun peekSamplePosition(): Long
    {
        val currentTail = tail
        if (currentTail >= head) return NO_EVENT

        return samplePositions[currentTail.toInt() and mask]
    }

    /**
     * Polls the next event into [outMetadata].
     * Returns the sample position, or [NO_EVENT] (-1L) if the buffer is empty.
     */
    fun poll(outMetadata: MutableEventMetadata): Long
    {
        val currentTail = tail
        if (currentTail >= head) return NO_EVENT

        val idx = currentTail.toInt() and mask
        val pos = samplePositions[idx]

        outMetadata.trackIndex = trackIndices[idx]
        outMetadata.beatIndex = beatIndices[idx]
        outMetadata.subIndex = subIndices[idx]
        outMetadata.isPrimaryBeat = isPrimaryFlags[idx]
        outMetadata.isDownbeat = isDownbeatFlags[idx]

        tail = currentTail + 1
        return pos
    }

    fun clear() {
        tail = head
    }

    /**
     * Drains all events that have reached or passed [currentPlayedSample].
     * Inlined to guarantee zero allocations in real-time execution.
     */
    inline fun drainReadyEvents(
        currentPlayedSample: Long,
        scratchMeta: MutableEventMetadata,
        onEvent: (samplePos: Long, meta: MutableEventMetadata) -> Unit
    ) {
        var nextPos = peekSamplePosition()
        while (nextPos != NO_EVENT && nextPos <= currentPlayedSample) {
            val eventPos = poll(scratchMeta)
            onEvent(eventPos, scratchMeta)
            nextPos = peekSamplePosition()
        }
    }

    fun isEmpty(): Boolean = tail >= head

    fun size(): Int = (head - tail).toInt()
}
