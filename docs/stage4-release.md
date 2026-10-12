# Conductor Stage 4: Quarantine, evidence-based release, interference classification

Status: **implemented, unit-tested (70/70), and verified live** in Ostinato `main` (Minecraft 1.21.11) on 2026-10-12. Raw evidence: [`evidence/live-run-2026-10-12-stage4.jsonl.gz`](evidence/live-run-2026-10-12-stage4.jsonl.gz). No caller was migrated, no scheduling was changed, and no stop primitive was added.

## 1. Rules (normative)

**Resource state** is `ResourceState ∈ {AVAILABLE, QUARANTINED}`, the smallest representation the specification allows. It is kept separate from operation state and from ownership-token validity.

**Release evidence** is sampled every in-world tick (`ReleaseEvidence`). Every condition must hold, from `OBSERVED` readings only; an unknown or unobserved reading is never evidence.

| Condition | Why it is required |
|---|---|
| `PATH_SEGMENT_PRESENT = false` | Live: tracks real control after cancel |
| `FORCED_INPUTS = []` | Live: tracks real control after cancel; clears one tick before the segment |
| `NEXT_SEGMENT_PRESENT = false`, `PATH_CALCULATION_PENDING = false` | No queued or pending path |
| `CONTROLLING_PROCESS` empty, `TRAVEL_GOAL_ACTIVE = false` | Necessary only. Live, these went quiet at cancel while the engine still steered, so they are never sufficient on their own. |
| `SECONDARY_BACKEND_BOUND = false` (if the runtime has one) | A bound Tungsten reports "not pathing" ambiguously (audit O2), so it always blocks release |

**Release** happens only when the evidence holds for `ReleasePolicy.confirmStopWindowTicks` consecutive ticks. **There is no default**: without the setting, the resource is never released and nothing is granted. No other event releases the resource: not a cancel call, not a timeout, not a context change.

**Grants.** Every `requestTravel`, including the first, needs the resource AVAILABLE. That makes reuse depend on evidence alone. A conflicting request from the same consumer is BLOCKED.

**Cancellation states:**

| State | Entered when |
|---|---|
| CANCELLING | `cancel()` invoked |
| STOPPED_PATHING | No segment, next segment or calculation; inputs not yet shown clear |
| CANCELLED | Resource released on evidence. Authority is released with it. |
| UNCERTAIN | Optional `uncertainAfterTicks` passes without evidence (the resource stays quarantined); another process takes control; the engine routes to an unobservable backend; the engine goes quiet without a cancel ("outcome not established": the signals cannot show arrival) |

**Context change** invalidates the operation and its authority, invokes `cancel()` best-effort, restarts the evidence count, and quarantines the resource. Identity change grants nothing.

## 2. Interference classification

| Source | Class | How it shows | Contract response |
|---|---|---|---|
| Second request from the same consumer while an operation holds the resource | **BLOCKED** | n/a | `CONFLICTING_OPERATION`, no engine call |
| Second Conductor install on the same `IBaritone` | **BLOCKED** | n/a | install refused |
| Another Baritone process steering (live: `MineProcess`, `AirProcess`) | **DETECTED** | `CONTROLLING_PROCESS`, plus segment, inputs and calculation signals | Grant refused (`RESOURCE_NOT_RELEASED`); an active operation becomes UNCERTAIN; after release, the resource is re-quarantined |
| Engine activity with no named process | **DETECTED** | segment, inputs or calculation signals | Resource re-quarantined |
| Navigation `cancel()` stopping other processes (audit B6) | **DETECTED** (by Conductor's own action) | Live: mining stopped at once on cancel | Recorded; not prevented. It is existing engine behaviour. |
| Tungsten travel when Tungsten is bound | **DETECTED** only when it reports `true`, otherwise **UNDETECTABLE** | `SECONDARY_BACKEND_*` | Bound Tungsten blocks release entirely (fail closed) |
| Tungsten when not installed | **NOT_APPLICABLE** | discovery: `UNAVAILABLE` | n/a |
| Vanilla key presses (human player), or input written outside the override handler | **UNDETECTABLE** | none | Not claimed |
| `forceCancel()` by another caller (invalidates the calculation signal, R1) | **UNDETECTABLE** | none | Not claimed |
| Path calculation finishing after cancel (R1) | **DETECTED** if it installs a segment | `PATH_SEGMENT_PRESENT` | Release blocked while present. 0 occurrences in 24 live trials. |

Each DETECTED or BLOCKED event is written to the sink as `INTERFERENCE`, with the class as subject and the source in the detail.

## 3. Live verification (2026-10-12, release window 20 ticks, uncertainty timeout 1 200 ticks)

| Step | Result |
|---|---|
| Startup | Resource AVAILABLE after 20 quiet ticks |
| Travel 40 blocks, cancel after 20 ticks | CANCELLING, then STOPPED_PATHING after 99 ticks (the segment ran to its end, L1), then **CANCELLED after 119 ticks, with player speed 0.0 at confirmation**. Authority released, resource AVAILABLE. |
| Reuse | Second operation granted on evidence, ran to the end without cancel (1 block from target), then UNCERTAIN "outcome not established", resource released |
| Mining started | `INTERFERENCE DETECTED baritone.process.MineProcess`, resource QUARANTINED, travel refused with no engine call. AVAILABLE again 67 ticks after mining was cancelled. |
| Water cancel | CANCELLED 44 ticks after cancel (effective at the water's edge). About 196 ticks later, `INTERFERENCE DETECTED baritone.process.AirProcess` and the resource was re-quarantined. |
| Nether teleport | `CONTEXT_INVALIDATED` (epoch 2), resource immediately QUARANTINED ("evidence restarts"), AVAILABLE after a fresh 20-tick window. The pre-change observation was rejected. |

In this run, no release ever came before real movement stopped.

## 4. Limits

- **Evidence base:**
  - One Minecraft version so far (1.21.11) and one backend (Baritone);
  - Flat world with staircase, trench, water and long-path courses;
  - 24 cancel trials plus this Stage 4 run.
  - Not covered: elytra, parkour-heavy terrain, boats, Tungsten.
- **Cancel latency belongs to the engine.** A cancel lasted up to 344 ticks (17 s) on a long path (L1), and Conductor cannot shorten that without a different stop. `forceCancel()` exists but is an owner decision (live-run doc, Consequences).
- **The window value is the consumer's choice.** 20 ticks was chosen from the observed momentum (≤ 11 ticks) and is used by the harness only; the contract has no default.
- **"Outcome not established"** is deliberate: the observation set has no arrival signal. Adding one (position vs goal) would be new contract surface for a later stage.
