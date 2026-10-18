# Architecture Design Document: Modernized Rhythm Subsystem

## 1. Executive Summary & Architectural Vision

The rhythm subsystem transitions from a rigid, metronome-specific step sequencer into a **reactive, mode-agnostic, multi-layered cyclic rhythm architecture**.

Musical concepts (time signatures, subdivisions, cross-beat tuplets, and continuous drum grooves) are completely isolated in the **Domain Layer**. They compile into an immutable, flat **`TimelinePattern`** evaluated along a normalized phase cycle $\phi \in [0.0, 1.0)$.

The **Android Service Layer** (`RhythmService`) and **Audio Engine** (`TimelineAudioEngine`) are strictly mode-agnostic: they possess no knowledge of metronome bars or drum pads. They execute arbitrary sample-accurate timeline events, stream 16-bit PCM audio, and synchronize visual state with the digital-to-analog converter (DAC).

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       PRESENTATION LAYER (COMPOSE UI)                       │
│              (Observes RhythmSession; triggers intent-free mutations)       │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                    REPOSITORY LAYER: RhythmSession (SSOT)                   │
│                                                                             │
│   - StateFlows: mode, bpm, isRunning, metronomeConfig, activePattern        │
│   - Pure Domain Mutators: setBpm(), setBeatCount(), toggleAccent(), etc.    │
│   - Reactive Compilation: Automatically emits recompiled TimelinePatterns   │
└──────────────────┬───────────────────────────────────────────┬──────────────┘
                   │                                           │
┌──────────────────▼──────────────────┐     ┌──────────────────▼──────────────┐
│       MODE 1: METRONOME DOMAIN      │     │     MODE 2: DRUM MACHINE DOMAIN │
│                                     │     │                                 │
│   [ MetronomeConfig ]               │     │   [ DrumMachineConfig ]         │
│   - Primary Meter / Subdivisions    │     │   - Multi-Track Continuous Pads │
│   - Cross-Beat Tuplets (3:2)        │     │   - Microtiming & Swing Offsets │
│   - Counter-Meter (Polymeter LCM)   │     │   - Per-event velocities        │
│                  │                  │     │                  │              │
│                  ▼                  │     │                  ▼              │
│       [ GridPatternGenerator ]      │     │    [ FreeformPatternGenerator ] │
└──────────────────┬──────────────────┘     └──────────────────┬──────────────┘
                   │                                           │
                   └──────────────────┬────────────────────────┘
                                      ▼
                        TimelinePattern (Immutable)
                                      │
┌─────────────────────────────────────▼───────────────────────────────────────┐
│                    SERVICE LAYER: RhythmService (Android)                   │
│                                                                             │
│   - Lifecycle: Foreground Service, Notification, AudioFocus, WakeLock       │
│   - Headless Playback: High-priority thread, mono PCM AudioTrack streaming  │
│   - 64-bit unwrapping of track.playbackHeadPosition (prevents 24.8h rollover│
│   - DAC Sync: Drains playbackQueue -> updates RhythmSession visual state    │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
┌─────────────────────────────────────▼───────────────────────────────────────┐
│                 AUDIO ENGINE LAYER: TimelineAudioEngine                     │
│                                                                             │
│   - Normalized Clock: S_cycle = round(N * (60 / BPM) * f_s)                 │
│   - Polyphonic Additive Mixer: 16-voice pool, saturation clamping [-32k, 32k│
│   - SoundBank: O(1) enum table (synthesized clicks + 808 synthetic drums)   │
│   - Lock-Free Pattern Latching: Beat- and cycle-boundary atomic swaps       │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Component Specifications

### 2.1 State Management: `RhythmSession` (Repository Layer)

`RhythmSession` is the **Single Source of Truth (SSOT)** for playback state. It completely eliminates the legacy architecture's circular intent dispatching.

* **Reactive Compilation:** Whenever `metronomeConfig` (or in the future, `drumMachineConfig`) changes, the repository recompiles it into an immutable `TimelinePattern` and emits it via `activePattern`.
* **Transport Control:** Exposes simple commands (`start`, `stop`, `toggle`) that dispatch OS lifecycle intents to `RhythmService`.
* **Visual Synchronization:** Receives hardware-synchronized `PlaybackEvent` signals from the service and exposes them via `StateFlow` for UI consumption.

#### Core Contract
```kotlin
enum class RhythmMode {
    METRONOME,
    DRUM_MACHINE
}

interface RhythmSession {
    // Read-only StateFlows for UI observation
    val mode: StateFlow<RhythmMode>
    val bpm: StateFlow<Int>
    val isRunning: StateFlow<Boolean>
    val metronomeConfig: StateFlow<MetronomeConfig>
    val activePattern: StateFlow<TimelinePattern>
    val currentPlaybackEvent: StateFlow<PlaybackEvent?>

    // Domain Mutators (Pure Kotlin, zero Android Context)
    fun setMode(newMode: RhythmMode)
    fun setBpm(newBpm: Int)
    fun setBeatCount(count: Int)
    fun toggleBeatAccent(index: Int)
    fun toggleBeatMute(index: Int)
    fun setSubdivision(subdivision: Subdivision)
    fun setCounterMeter(counterMeter: MeterTrack?)

    // Internal Service Bridge (Called exclusively by RhythmService)
    fun updatePlaybackEvent(event: PlaybackEvent)
    fun setRunning(running: Boolean)

    // Service Transport Launchers
    fun start(context: Context)
    fun stop(context: Context)
    fun toggle(context: Context)
}
```

---

### 2.2 Domain Layer: Tasks & Modes

The Domain Layer translates musical parameters into the flat timeline representation.

#### 1. Mode 1: Metronome (`MetronomeConfig` + `GridPatternGenerator`)
* **`Subdivision(pulses, overBeats)`:** Ratio model supporting intra-beat ($N:1$, e.g., straight, 8ths, 16th triplets) and cross-beat tuplets ($3:2$ quarter-note triplets).
* **`MeterTrack`:** Represents a metric bar length ($M$ beats), accents, mute sets, and voice assignments.
* **Polymetric Scaling ($\text{LCM}$):** When a `counterMeter` is defined (e.g. 4/4 against 5/4), the compiler computes $\text{nominalBeats} = \text{LCM}(M_1, M_2) = 20$, scales the cycle, and merges both meters into a single drift-free loop.
* **Deduplication:** Subdivision pulses that collide with primary beats ($\pm 10^{-5}$) are suppressed, granting primary beats acoustic priority.
* **Mute Handling:** Muted beats are preserved with `gain = 0.0f` to keep visual UI synchronization continuous without allocating voices or rendering audio.

#### 2. Mode 2: Drum Machine (`FreeformPatternGenerator` — Phase 5)
* Accepts continuous floating-point placements along $[0.0, 1.0)$ for polyphonic drum voices (Kick, Snare, Hi-Hats, Clap).
* Compiles into the exact same `TimelinePattern` data structure without altering the audio engine or service.

---

### 2.3 Service Layer: `RhythmService` (Android Lifecycle & Streaming)

`RhythmService` is a headless background container. It has zero knowledge of musical structure; it simply streams `TimelinePattern` data to the hardware DAC.

#### Responsibilities
1. **OS Lifecycle & System Services:**
   * Acquires a `PowerManager.PARTIAL_WAKE_LOCK` with a 60-second re-acquire heartbeat.
   * Manages `AudioManager.AUDIOFOCUS_GAIN` and handles audio focus ducking/loss.
   * Registers `ACTION_AUDIO_BECOMING_NOISY` to pause playback when headphones are unplugged.
   * Publishes an ongoing Foreground Notification reflecting active BPM and Mode.
2. **Real-Time Streaming Loop:**
   * Runs an isolated Java thread configured with real-time Linux priority (`Process.THREAD_PRIORITY_URGENT_AUDIO`).
   * Configures an `AudioTrack` instance using `CHANNEL_OUT_MONO`, `ENCODING_PCM_16BIT`, and low-latency performance mode.
   * Renders in fixed chunks (e.g., 512 samples) via `TimelineAudioEngine.fillNextChunk(buffer, bpm)`.
3. **64-Bit Continuous Playback Head Unwrapping:**
   * `AudioTrack.playbackHeadPosition` wraps around every $2^{32}$ frames (approx. 24.8 hours at 48kHz).
   * The service maintains a 64-bit continuous sample counter:
     $$\text{ContinuousSamples} = (\text{wrapCount} \ll 32) \mid (\text{playbackHeadPosition} \ \& \ \text{0xFFFFFFFFL})$$
4. **DAC Synchronization Queue Draining:**
   * As samples exit the DAC, the service drains all entries from `TimelineAudioEngine.playbackQueue` where $\text{samplePosition} \le \text{ContinuousSamples}$.
   * The latest physical event is forwarded to `RhythmSession.updatePlaybackEvent(event)`.

---

### 2.4 Audio Engine Layer: `TimelineAudioEngine` & `SoundBank`

The audio engine is a sample-accurate, lock-free real-time synthesis and mixing engine.

#### 1. Mathematical Timing Foundation
Given tempo (BPM) and hardware sample rate $f_s$:
$$S_{\text{cycle}} = \text{round}\left(\text{nominalBeats} \times \frac{60.0}{\text{BPM}} \times f_s\right)$$
For any event at normalized position $\phi_k \in [0.0, 1.0)$, its target hardware trigger sample within the cycle is:
$$\text{SampleOffset}_k = \text{round}(\phi_k \times S_{\text{cycle}})$$
Cumulative floating-point drift across hours of playback is mathematically zero.

#### 2. Polyphonic Additive PCM Mixer
* Maintains a pre-allocated pool of `Voice` instances (default max 16).
* Slices render buffers along beat boundaries. In-flight voices from previous slices are rendered first; newly triggered voices within the slice are allocated and mixed second.
* Accumulates into a 32-bit integer scratch buffer and applies saturation clamping:
  $$\text{output}[i] = \max(-32768, \min(32767, \text{mixAccumulator}[i]))$$

#### 3. Lock-Free Pattern Latching
Pattern swaps do not cut off active sounds or jump phase:
* **Tier 1 (Beat Boundary):** If the incoming pattern has the same `nominalBeats` (e.g., muting a beat or changing subdivision), the engine latches the pattern at the end of the currently playing beat.
* **Tier 2 (Cycle Boundary):** If `nominalBeats` differs (meter or polymeter change), the engine latches the pattern when the current cycle wraps to downbeat $0.0$.

#### 4. `SoundBank`
* An immutable lookup table (`Array<ShortArray?>`) indexed by `SoundId.ordinal`, providing $O(1)$ single-instruction lookups during real-time rendering.
* Populated with clean zero-crossing synthesized clicks (`ClickSynthesizer`) and algorithmic 808 drum voices (`SyntheticDrumSynthesizer`), with support for loading 16-bit PCM WAV assets (`WavAudioDecoder`).

---

## 3. End-to-End Execution Sequence Diagrams

### 3.1 Reactive Pattern Update During Active Playback

This diagram demonstrates what happens when a user toggles an accent or subdivision in the UI while playback is running:

```
[ UI / User ]      [ RhythmSession ]    [ RhythmService ]       [ TimelineAudioEngine ]
      │                    │                    │                         │
      │─ toggleAccent(2) ─>│                    │                         │
      │                    │── recompile ──────>│                         │
      │                    │   (via Generator)  │                         │
      │                    │                    │                         │
      │                    │── StateFlow emit ─>│                         │
      │                    │   (activePattern)  │── engine.setPattern() ─>│
      │                    │                    │                         │ (AtomicReference.set)
      │                    │                    │                         │
      │                    │                    │                         │ [ Audio Thread Loop ]
      │                    │                    │                         │ Current beat finishes...
      │                    │                    │                         │ Latch Checkpoint reached!
      │                    │                    │                         │ pendingPattern.getAndSet(null)
      │                    │                    │                         │ Seamlessly adopts new pattern
      │                    │                    │                         │ [ Zero Audio Dropout ]
```

---

### 3.2 Real-Time Audio Streaming & DAC UI Synchronization

This diagram illustrates the audio rendering and visual feedback pipeline:

```
[ RhythmService (AudioThread) ]           [ TimelineAudioEngine ]             [ Hardware DAC ]
              │                                      │                               │
              │── engine.fillNextChunk(buffer) ─────>│                               │
              │                                      │── Voice mixing & clamping ───>│
              │                                      │── playbackQueue.add(event)    │
              │<─ buffer filled ─────────────────────│                               │
              │                                                                      │
              │── audioTrack.write(buffer) ─────────────────────────────────────────>│
              │                                                                      │ (Audio plays out speaker)
              │                                                                      │
              │── getContinuousPlaybackSamples()                                     │
              │   (Reads track.playbackHeadPosition)                                 │
              │                                                                      │
              │── Drain engine.playbackQueue where samplePos <= playedSamples        │
              │                                                                      │
  [ To RhythmSession ]                                                               │
  updatePlaybackEvent(event) ────────────────────────────────────────────────────────┘
```

---

## 4. Key Architectural Trade-Offs & Decisions

| Decision | Alternative Considered | Why Chosen |
| :--- | :--- | :--- |
| **Reactive SSOT Repository** | Direct Intent commands from UI to Service | Eliminates circular state loops. UI mutations are synchronous, instant, and 100% testable on the JVM. |
| **Ratio-Based Subdivisions ($P:B$)** | Intra-beat only ($N:1$) | Unlocks cross-beat tuplets (e.g. $3:2$ quarter-note triplets) without special-case hacks in the audio engine. |
| **Polymeter via $\text{LCM}$ Scaling** | Independent drifting clocks | Guarantees drift-free metric phasing (e.g. 4/4 vs 5/4 across 20 beats) along a single normalized master cycle. |
| **Lock-Free Pattern Latching** | Mutex / Synchronized engine locks | Prevents audio thread priority inversions and buffer underruns (glitches/pops). |

