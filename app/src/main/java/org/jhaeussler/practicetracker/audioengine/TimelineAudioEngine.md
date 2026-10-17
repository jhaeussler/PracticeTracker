# Timeline Audio Engine

A high-performance, real-time cyclic audio synthesizer and mixer for Android, built with Kotlin.

The engine renders arbitrary rhythmic patterns defined on a normalized timeline $[0.0, 1.0)$ into 16-bit PCM mono audio buffers. It powers both traditional **Metronomes** (uniform grids, compound meters, triplets) and **Drum Machines** (asymmetrical beat intervals, micro-timing, polyphonic voices).

---

## Key Features

* **Beat-Segmented Timeline:** Rhythms are defined as fractional coordinates $[0.0, 1.0)$ on a master measure cycle, partitioned into dynamically evaluated beat segments.
* **Two-Tier Latching:**
  * **Beat Boundary:** BPM changes and subdivision updates latch immediately after the currently active beat finishes.
  * **Downbeat Boundary (Measure Wrap):** Structural meter/time-signature changes (`nominalBeats`) wait until the current measure finishes to prevent rhythm truncation.
* **Sub-Sample Clock (Zero Drift):** Floating-point sample accumulators prevent timing drift over hours of continuous playback.
* **16-Voice Polyphony & Additive Mixing:** Multiple simultaneous sounds (e.g., Kick + Hi-Hat) blend into a 32-bit accumulator with saturation clamping to prevent digital clipping.
* **Dynamic SoundBank:** Audio sample buffers (`ShortArray`) can be hot-swapped while running with zero audio clicks or dropouts.
* **Zero-Allocation Audio Loop:** Lock-free and memory-allocation-free in the real-time audio thread (`THREAD_PRIORITY_URGENT_AUDIO`).

---

## Architecture Overview

```
                      Normalized Timeline [0.0 .. 1.0)
     ├── Beat 0 [0.0 .. 0.25) ──┼── Beat 1 [0.25 .. 0.50) ──┼── Beat 2 [0.50 .. 1.0) ──┤
     │  (e.g., played @ 60 BPM) │  (User turns dial to 120) │  (Played @ 120 BPM)       │
     ▼                          ▼                           ▼                           ▼
[ 48,000 samples ]        [ Beat Latch ]              [ 24,000 samples ]          [ Cycle Wrap ]
                          - BPM switches to 120                                   - Meter changes
                          - Subdivisions latch                                      latch here
```

### Audio Pipeline per Chunk

```
fillNextChunk(buffer, bpm)
 ├── 1. Determine active Beat Segment duration (S_beat) based on active BPM
 ├── 2. Slice chunk into boundaries: min(chunkRemaining, beatRemaining)
 ├── 3. Accumulate in-flight voices from previous slices into mixBuffer (32-bit)
 ├── 4. Trigger scheduled events for this slice; allocate voices from pool
 ├── 5. Advance sample counters & check latching checkpoints
 └── 6. Saturate and clamp 32-bit mixBuffer into 16-bit PCM output buffer
```

---

## Data Contracts

### 1. `TimelineEvent`
Represents an individual audio trigger on the timeline:

```kotlin
data class TimelineEvent(
    val positionRatio: Double,   // 0.0 <= positionRatio < 1.0 (timeline offset)
    val soundId: SoundId,        // Audio sound to trigger from SoundBank
    val gain: Float = 1.0f,      // Velocity/Volume (0.0 to 1.0)
    val metadata: EventMetadata
)
```

### 2. `EventMetadata`
Metadata used for boundary detection and UI sync:

```kotlin
data class EventMetadata(
    val beatIndex: Int,          // Logical beat group (0, 1, 2...)
    val subIndex: Int = 0,       // Subdivision step inside the beat
    val isPrimaryBeat: Boolean,  // true = defines a Beat Segment boundary
    val isDownbeat: Boolean      // true = downbeat of the measure (0.0)
)
```

### 3. `TimelinePattern`
Immutable compiled representation of a measure:

```kotlin
data class TimelinePattern(
    val nominalBeats: Int = 4,   // Basis for BPM scaling (e.g. 4 for 4/4)
    val events: List<TimelineEvent> // Strictly sorted by positionRatio ascending
)
```

### 4. `SoundBank`
Immutable registry of PCM audio buffers:

```kotlin
class SoundBank(sampleMap: Map<SoundId, ShortArray>) {
    fun get(soundId: SoundId): ShortArray?
    fun copyWith(soundId: SoundId, samples: ShortArray): SoundBank
}
```

---

## Latching Guarantees

| Parameter Changed | Latching Checkpoint | Behavior |
| :--- | :--- | :--- |
| **Tempo (`bpm`)** | **Beat Boundary** | The active beat finishes its full duration at the old tempo. The next beat starts immediately at the new tempo. |
| **Subdivisions / Accents / Mutes** | **Beat Boundary** | When `newPattern.nominalBeats == activePattern.nominalBeats`, changes take effect at the start of the next beat. |
| **Time Signature / Meter** | **Downbeat (Measure End)** | When `newPattern.nominalBeats != activePattern.nominalBeats`, all beats in the current measure finish before switching. |
| **SoundBank / Samples** | **Next Trigger** | In-flight voices finish playing their existing buffer. New triggers immediately read the updated sound. |

---

## Usage Guide

### 1. Initialization

```kotlin
val soundBank = SoundBank(
    mapOf(
        SoundId.CLICK_ACCENT to accentPcm,
        SoundId.CLICK_BEAT to beatPcm,
        SoundId.CLICK_SUBDIVISION to subPcm
    )
)

val engine = TimelineAudioEngine(sampleRate = 48000, maxVoices = 16)
engine.setSoundBank(soundBank)
```

### 2. Standard 4/4 Metronome Pattern

```kotlin
val pattern4_4 = TimelinePattern(
    nominalBeats = 4,
    events = listOf(
        TimelineEvent(0.00, SoundId.CLICK_ACCENT, 1.0f, EventMetadata(0, 0, isPrimaryBeat = true, isDownbeat = true)),
        TimelineEvent(0.25, SoundId.CLICK_BEAT, 1.0f, EventMetadata(1, 0, isPrimaryBeat = true, isDownbeat = false)),
        TimelineEvent(0.50, SoundId.CLICK_BEAT, 1.0f, EventMetadata(2, 0, isPrimaryBeat = true, isDownbeat = false)),
        TimelineEvent(0.75, SoundId.CLICK_BEAT, 1.0f, EventMetadata(3, 0, isPrimaryBeat = true, isDownbeat = false))
    )
)
engine.setPattern(pattern4_4)
```

### 3. Drum Machine Pattern with Micro-Timing (Laid-Back Snare)

```kotlin
val drumPattern = TimelinePattern(
    nominalBeats = 4,
    events = listOf(
        // Kick on Beat 1 (0.0)
        TimelineEvent(0.000, SoundId.DRUM_KICK, 1.0f, EventMetadata(0, 0, isPrimaryBeat = true, isDownbeat = true)),
        
        // Kick on Beat 2 (0.25)
        TimelineEvent(0.250, SoundId.DRUM_KICK, 1.0f, EventMetadata(1, 0, isPrimaryBeat = true, isDownbeat = false)),
        
        // Snare slightly dragged (micro-timing offset at 0.258 instead of 0.250)
        TimelineEvent(0.258, SoundId.DRUM_SNARE, 0.9f, EventMetadata(1, 1, isPrimaryBeat = false, isDownbeat = false)),
        
        // Hi-Hat 8th-note offbeat (0.375)
        TimelineEvent(0.375, SoundId.DRUM_HIHAT_CLOSED, 0.7f, EventMetadata(1, 2, isPrimaryBeat = false, isDownbeat = false))
    )
)
engine.setPattern(drumPattern)
```

### 4. Audio Playback Loop (Service / AudioTrack)

```kotlin
val buffer = ShortArray(512)

while (isRunning) {
    // 1. Render next chunk (BPM changes passed here latch at beat boundary)
    engine.fillNextChunk(buffer, currentBpm)

    // 2. Write to Android AudioTrack
    audioTrack.write(buffer, 0, buffer.size)

    // 3. Drain UI events matching hardware playback head
    val currentHeadSample = getContinuousPlaybackHead(audioTrack)
    while (engine.playbackQueue.peek()?.let { it.samplePosition <= currentHeadSample } == true) {
        val event = engine.playbackQueue.poll()!!
        updateUiState(event.metadata)
    }
}
```

---

## Real-Time Guarantees

* **Zero Garbage Collection Overhead:** `fillNextChunk()` allocates zero heap objects during playback.
* **Deterministic Latency:** Clamping, mixing, and voice allocation are $O(1)$ operations with bounded execution time.
* **Saturation Safety:** Uses 32-bit addition before 16-bit clamping (`Short.MIN_VALUE .. Short.MAX_VALUE`), eliminating integer wrap distortion when multiple loud samples overlap.