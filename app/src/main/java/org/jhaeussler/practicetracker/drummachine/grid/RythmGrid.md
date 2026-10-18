
# Rhythmic Concepts

To support advanced metronome capabilities—including **16th-note triplets**, **quarter-note triplets**, and future **polymetric practice modes**—the domain model distinguishes between three distinct rhythmic mechanisms:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             RHYTHMIC TAXONOMY                               │
├──────────────────────────┬──────────────────────────┬───────────────────────┤
│ 1. Intra-Beat Subdiv.    │ 2. Cross-Beat Tuplets    │ 3. Polymeter          │
│    (Local $N:1$)         │    (Polyrhythmic $P:B$)  │    (Phase Alignment)  │
│                          │                          │                       │
│ - Divides 1 beat         │ - Spans $B > 1$ beats    │ - 2 meters, same BPM  │
│ - 8ths, 16ths            │ - Quarter-note triplets  │ - 4/4 vs 5/4          │
│ - 8th & 16th triplets    │ - 3 pulses over 2 beats  │ - Resolves at LCM     │
└──────────────────────────┴──────────────────────────┴───────────────────────┘
```

---

## Intra-Beat Subdivisions ($N : 1$)
An intra-beat subdivision partitions **exactly one primary beat** ($B = 1$) into $N$ equal slices.

* **Eighth notes ($2:1$):** 2 pulses per quarter beat.
* **Eighth-note triplets ($3:1$):** 3 pulses per quarter beat.
* **Sixteenth notes ($4:1$):** 4 pulses per quarter beat.
* **Sixteenth-note triplets / Sextuplets ($6:1$):** 6 pulses per quarter beat (3 pulses per eighth note).

$$\Delta \phi_{\text{step}} = \frac{1.0}{M \times N} \quad \text{where } M = \text{total beats in measure}$$

---

## Cross-Beat Tuplets / Polyrhythms ($P : B$)
A cross-beat tuplet partitions **multiple beats** ($B > 1$) into $P$ equal pulses.

* **Quarter-note triplets ($3:2$):** 3 evenly spaced pulses in the time of **2 quarter notes** (a half note).
* In $4/4$ time ($M = 4$), quarter triplets occur in two 2-beat windows (Beats 0–1 and Beats 2–3):

```text
Beat Index:     |     Beat 0      |     Beat 1      |     Beat 2      |     Beat 3      |
Quarter Pulses: |-----------------|-----------------|-----------------|-----------------|
Triplets (3:2): ^           ^           ^           ^           ^           ^           ^
Normalized phi: 0.0000      0.1667      0.3333      0.5000      0.6667      0.8333      1.0000
```

General step formula for a cycle of $M$ nominal beats:
$$\Delta \phi_{\text{step}} = \frac{B}{M \times P}$$

---

## Polymeters (Metric Phasing via $\text{LCM}$)
A polymeter runs **two or more distinct meters concurrently at the exact same tempo (BPM)**. Because the pulse durations are identical, the measures drift in phase until they resolve at the **Least Common Multiple (LCM)** of their beat counts.

* **Example:** $4/4$ vs. $5/4$ at $120\text{ BPM}$:
  * Meter A loops every 4 beats ($5$ bars total).
  * Meter B loops every 5 beats ($4$ bars total).
  * Resolution point: $\text{LCM}(4, 5) = 20\text{ beats}$.

```text
Beat:     0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 (20)
Meter A: [▼  .  .  .][▼  .  .  .][▼  .  .  .][▼  .  .  .][▼  .  .  .]  ▼ (Loops)
Meter B: [▼  .  .  .  .][▼  .  .  .  .][▼  .  .  .  .][▼  .  .  .  .]  ▼ (Loops)
          ^                                                            ^
        Downbeat 0                                                Sync Point
```

* **Timeline Engine Integration:**
  $$\text{nominalBeats} = \text{LCM}(M_1, M_2)$$
  $$T_{\text{cycle}} = \text{nominalBeats} \times \frac{60.0}{\text{BPM}}$$
  The entire 20-beat polymetric relationship compiles down to a single immutable $[0.0, 1.0)$ `TimelinePattern`.

---
