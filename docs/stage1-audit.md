# Conductor Stage 1 Audit: IMovementEngine cancellation, evidence, and lifecycle

Status: **complete**. Documentation and source inspection only. No code was changed in Ostinato or TenorClef. No runtime tests were run, and no claim in this document comes from executing anything.

## 0. Basis and labels

| Item | Value |
|---|---|
| Inspection basis | Ostinato `dfce2a2cf1a3297934f6a5904628f8c0bbd50f87` (`main`), shared tree under `src/` |
| Secondary (outside the basis, labelled where used) | TenorClef `04604611e4f983da83133c99065f0fabe79f6564` (optional consumer); vendored Tungsten 1.16.1 port at `TenorClef/vendor/tungsten-1.16.1` (upstream `3ndetz/Tungsten@5cb12ad`) |
| Not available | The 1.21.x Tungsten source that Ostinato `main` binds to by reflection. `TenorClef/vendor/tungsten` is an empty checkout. |
| Labels | **Verified**: read in source at the stated commit. **Reported**: stated in docs/comments or inferred from a different tree. **Unknown**: insufficient evidence. |

"Verified" here always means verified *by static reading*. Runtime behaviour, timing, and frequency are Unknown unless stated otherwise.

The per-version overlays in `versions/` are documented as unused; other Minecraft versions build from separate branches that were not inspected (`versions/README.md`, Reported). All findings apply only to the shared `main` tree.

## 1. Safety distinctions carried through this audit

- **Context validity**: does an operation, observation, or result belong to the current session and world? A changed sessionId, epoch, or dimension invalidates it immediately.
- **Ownership authority**: which contract operation may request movement now? Identity change revokes old authority and grants nothing new.
- **Engine-resource safety**: can the movement resource be reused without overlap from a prior operation? Identity change does **not** establish this.
- **Confirmed runtime disappearance**: evidence that the old engine instance or execution context can no longer control the resource. Identity change does **not** establish this.

A changed sessionId is never proof that the old engine stopped controlling movement. Nothing in this audit relies on it. Ostinato has no movement-level session or epoch concept today: the only `epoch` usages are in waypoints and swarm framing (**Verified**, grep at basis).

## 2. Boundary and engine-instance ownership

| # | Finding | Label | Evidence |
|---|---|---|---|
| B1 | `IMovementEngine` exposes `goTo`, `follow`, `status`, `activeBackend`, `isPathing`, `cancel`, `statusLine`. | Verified | `src/api/java/baritone/api/movement/IMovementEngine.java:12-43` |
| B2 | The only implementation is `HybridMovementEngine`. It is constructed only via `HybridMovementEngine.forBaritone` / `MovementBackends.engine(IBaritone)`, and **each call returns a new instance**. | Verified | `HybridMovementEngine.java:32-34`, `MovementBackends.java:50-52` |
| B3 | No Ostinato code constructs or holds an engine instance. The only in-workspace caller (TenorClef `MovementEngineAdapter`) builds a **fresh engine on every call**: `goTo`, `isPathing`, `cancel` and `statusLine` each get a new instance. | Verified (Ostinato); Verified at TenorClef `0460461` (outside basis) | grep at basis; `TenorClef/.../MovementEngineAdapter.java:97-102, 123, 191, 230, 245` |
| B4 | Engine instance state (`lastStatus`, `lastBackend`, `activeGoal`) is per-instance and unsynchronised. The movement resource it drives is shared: the `IBaritone` it wraps, plus the static singleton `TungstenMovementBackend.INSTANCE` over Tungsten's static `PATHFINDER`/`EXECUTOR`. | Verified | `HybridMovementEngine.java:22-25`; `TungstenMovementBackend.java:38, 87-100` |
| B5 | The primary `IBaritone` is created once per client, and `destroyBaritone` refuses to destroy it. It outlives world, dimension, and server changes. | Verified | `BaritoneProvider.java` (`createBaritone`, `destroyBaritone`) |
| B6 | `IMovementEngine.cancel()` is **engine-wide by its own contract**: "Stops travel on every backend and every pathing process with it". The implementation calls `PathingControlManager.cancelEverything()`, which calls `onLostControl()` on **every** registered process (mining, builder, farm, …), and cancels Tungsten regardless of who started it. | Verified | `IMovementEngine.java:39`; `HybridMovementEngine.java:182-193`; `PathingControlManager.java:63-74` |
| B7 | Docs: MovementEngine is travel-only; mining and builder stay on classic processes. `CustomGoalProcess` keeps its own Tungsten intercept. | Reported (docs); intercept Verified | `docs/MOVEMENT_ENGINE.md`; `CustomGoalProcess.java:159-218` |

**Consequence.** The thing that "owns" movement is the long-lived shared `IBaritone` plus the Tungsten singleton, not an engine instance. An engine instance is a stateless-in-practice façade. Its status fields describe only what *that object* did.

## 3. Signal inventory

### 3.1 `HybridMovementEngine` self-reported signals

| Signal | What the source does | What it is evidence of | Label |
|---|---|---|---|
| `status() == CANCELLED` | `cancel()` sets `lastStatus = CANCELLED` **unconditionally** after its calls, and swallows every `Throwable` from the Baritone cancel calls. `cancelEverything()`'s boolean result (whether the segment was actually cancelled) is ignored. | That **this instance invoked** `cancel()`. Not that pathing stopped, not that overrides cleared. Equivalent to "cancel requested". | Verified (`HybridMovementEngine.java:182-193`) |
| `status() == IDLE` | Default for a fresh instance. A `PATHING` → `IDLE` transition happens lazily inside `status()` when `!isPathing()`. | Nothing about the shared resource on a fresh instance, which is the normal TenorClef case (B3). | Verified (`:24, 142-151`) |
| `isPathing() == false` | Checks Tungsten **only if** `lastBackend == TUNGSTEN`. The default is `BARITONE`, so a fresh instance never checks Tungsten. Then checks `CustomGoalProcess.isActive()` and `PathingBehavior.isPathing()`. Swallows exceptions as `false`. | Insufficient alone. Misses Tungsten follow or path started through another instance or outside the engine. Misses paused-but-held paths (see 3.2). Misses in-flight path calculation. A thrown exception reads as "not pathing". | Verified (`:23, 159-179`) |
| `PathResult` / `activeBackend()` | Instance-local. | Which backend accepted *this instance's* last request only. | Verified |

**Finding S1 (Verified).** No `HybridMovementEngine` signal, alone or combined, establishes pathing-stopped, override-clear, or resource safety. The candidate pair `status() ∈ {CANCELLED, IDLE} && !isPathing()` is **insufficient by source inspection**, and that settles the Unknown the specification left open for Stage 1.

### 3.2 Baritone pathing signals (shared resource, `IBaritone`)

| Signal | Source behaviour | Evidence value | Label |
|---|---|---|---|
| `PathingBehavior.cancelEverything()` return value | Returns `isSafeToCancel()`. If `false`, the current segment is **not** cancelled and keeps executing and holding inputs. Processes are still told `onLostControl()`. | `true` means the segment was cancelled this call. `false` means it was not, yet. | Verified (`PathingBehavior.java:383-390`) |
| Deferred cancel at the next safe point | With no process in control, every `preTick` calls `cancelSegmentIfSafe()`. The segment is cut, and keys cleared, at the first tick where `safeToCancel` holds. | Baritone's existing mechanism completes a deferred cancel without any new primitive. Duration is Unknown and depends on the movement in progress (e.g. mid-parkour, elytra). | Verified (`PathingControlManager.java:86-95`; `PathingBehavior.java:363-381`) |
| `getCurrent() == null && getNext() == null` | Cleared by `secretInternalSegmentCancel()` (with `clearAllKeys()` + `stopBreakingBlock()`), on segment finish or fail (with `clearAllKeys()`), or on the OUT tick. | Candidate *pathing-stopped* evidence for the Baritone executor. Point-in-time only (see race R1). | Verified (`:194-199, 410-421`) |
| `getInProgress().isEmpty()` | `inProgress` is set before the calculation thread is scheduled, and nulled by that thread when it finishes. `forceCancel()` nulls it **while the thread may still be running**. | Candidate evidence that no calculation can still deliver a path. Invalid after any `forceCancel()`. | Verified (`:424-430, 552-553, 623-625`) |
| `PathingBehavior.isPathing()` | `hasPath() && !pausedThisTick`. It is `false` during a requested pause while the path is still held and will resume. | `false` ≠ stopped. | Verified (`:344-346`, `tickPath` `:158-169`) |
| `CustomGoalProcess.isActive()` | `state != NONE`. It covers Tungsten travel that CustomGoal itself started. | Process intent, not executor state. | Verified (`CustomGoalProcess.java:110-113`) |
| `IPathingControlManager.mostRecentInControl()` | Process that issued a command this tick. Cleared by `cancelEverything()`. | Which process (if any) is steering Baritone pathing. | Verified (`PathingControlManager.java:76-79`) |

**Race R1: delayed result after cancel (Verified in source; runtime frequency Unknown).**
`AbstractNodeCostSearch.cancel()` sets a **non-volatile** `cancelRequested`, and `calculate()` **resets it to `false` on entry** (`AbstractNodeCostSearch.java:61, 95-104`). A cancel issued after `inProgress` is assigned, but before the executor thread enters `calculate()`, is therefore erased. When the thread finishes and `current == null`, the result is **installed as `current`** (`PathingBehavior.java:571-576`). The guard there is a start-position check, not an identity or epoch check. The same path applies after `forceCancel()`, which nulls `inProgress` without stopping the thread. Consequences:

- A single `current == null` observation is not stable pathing-stopped evidence.
- An old operation's calculation can re-arm Baritone pathing after cancellation. This is precisely the "delayed result" the contract must reject. Ostinato does not reject it today.
- With no process in control, the next `preTick` would call `cancelSegmentIfSafe()` on the reinstalled segment (Verified flow). Whether the reinstalled segment drives inputs for one or more ticks first is Unknown.

**Partial-cancel hazard (Verified).** `PathingControlManager.cancelEverything()` throws `IllegalStateException` if a non-temporary process stays active after `onLostControl()`. That aborts the loop over the remaining processes. `HybridMovementEngine.cancel()` swallows the exception and still reports `CANCELLED` (`PathingControlManager.java:68-73`; `HybridMovementEngine.java:184-192`).

### 3.3 Input override clearance

| Channel | Source behaviour | Observable? | Label |
|---|---|---|---|
| Baritone forced keys (`IInputOverrideHandler`) | A plain `HashMap<Input,Boolean>`. `isInputForcedDown(Input)` is public. `clearAllKeys()` empties the map. | Yes, through the public API on the game thread. **Not attributable**: 24 classes outside the handler write forced keys directly, including Air, Boat, Pvp, Farm, Builder, Mine, Elytra and the kinematic controllers. | Verified (`InputOverrideHandler.java:45-84`; grep at basis) |
| Player movement input swap | Only `onTick` (IN) swaps `player.input` between `PlayerMovementInput` and `KeyboardInput`, based on `inControl()`. `inControl()` stays `true` for a non-primary Baritone even when idle. On OUT ticks it returns early. | Map-cleared ≠ player input restored. Restoration happens on the next IN tick. | Verified (`:95-131`) |
| Block breaking / placing | `BlockBreakHelper.stopBreakingBlock()` is called only in the segment-cancel and pause paths. `clearAllKeys()` alone does not stop an in-progress break. | Separate from key state. | Verified (`PathingBehavior.java:160-165, 417-418`) |
| Rotation (`LookBehavior`) | `target` is consumed and nulled every player-update. It persists only while some writer re-asserts it each tick, and is cleared on world events. | Self-expiring; not a persistent override. | Verified (`LookBehavior.java:193-245, 265-267`) |
| Tungsten inputs | Not visible to Ostinato's override handler. In the vendored **1.16.1** port, the executor writes **vanilla `KeyMapping` state** (`options.keyForward.setPressed(...)` …) and player rotation directly. On `stop`, it releases those keys and sets `path = null` in the same executor tick, so `isRunning()` becomes `false`. | 1.16.1 tree: release coincides with `!isRunning()` **if** an executor tick runs. Ostinato `main`'s Tungsten: **Unknown**, source unavailable. Vanilla key state cannot be told apart from human input. | Verified for the TenorClef vendored 1.16.1 tree only; Reported/Unknown for `main` |
| Tungsten stop mechanism | `cancel()` sets `PATHFINDER.stop` and `EXECUTOR.stop` flags (asynchronous) and calls `FollowEntityTask.stop()` if active. Exceptions are swallowed. `isStopping()` exposes "search still winding down". CustomGoal already waits up to `TUNGSTEN_STOP_PATIENCE = 40` ticks for it. | Invocation sets flags only. Completion is observable via `isPathing()` (follow active ∥ pathfinder active ∥ executor running) **only when Tungsten is bound**. | Verified (`TungstenMovementBackend.java:161-235`; `CustomGoalProcess.java:55-58, 180-186`) |

**Finding O1 (Verified).** "Pathing stopped" and "overrides cleared" are separately observable on the Baritone side. Neither is attributable to a specific operation: all signals are shared, global, and written by many processes.
**Finding O2 (Unknown).** For Tungsten-backed travel on Ostinato `main`, override clearance is not observable through any Ostinato API, and the governing source was not available.

## 4. Lifecycle hooks

| Hook | Behaviour | Label |
|---|---|---|
| `TickEvent` OUT (player or world is null), emitted every client tick per Baritone | `PathingBehavior` runs `secretInternalSegmentCancel()` + `PathingControlManager.cancelEverything()` unconditionally (no safe-to-cancel gate). `InputOverrideHandler` does nothing. | Verified (`MixinMinecraft.java:90-99`; `PathingBehavior.java:136-141`; `InputOverrideHandler.java:97-99`) |
| Tungsten on OUT | Cancelled only indirectly, via `CustomGoalProcess.onLostControl()` → `cancelTungsten()`, and **only if CustomGoal started it** (`tungstenTravelActive`). Tungsten travel or follow started through `HybridMovementEngine` or TenorClef's own `TungstenBridge` is **not** cancelled by OUT ticks. | Verified (`CustomGoalProcess.java:211-225`); no OUT handler in `baritone.movement` |
| `WorldEvent` (`Minecraft.setLevel`, dimension change) | `GameEventHandler` closes and re-inits the world cache. `LookBehavior` clears its target. **`PathingBehavior` has no `onWorldEvent` handler**: a dimension change does not by itself cancel pathing. Whether an OUT tick always intervenes is Unknown. | Verified (absence of handler); Unknown (tick ordering) |
| Engine-instance lifecycle | None. Engine instances have no registration, close, or dispose method. | Verified |

## 5. Threading

| # | Finding | Label |
|---|---|---|
| T1 | `HybridMovementEngine`, `TungstenMovementBackend` (except `synchronized` `isAvailable`/`probe`), `PathingBehavior.cancelEverything/forceCancel`, and `InputOverrideHandler` contain **no thread checks**. | Verified |
| T2 | Shared state read by the candidate signals is not thread-safe: `PathingBehavior.current/next/safeToCancel/pausedThisTick` are non-volatile, and the override map is a plain `HashMap`. Off-thread reads are unreliable. | Verified (fields); effect Unknown |
| T3 | Precedent for thread detection without engine side effects: `BlockStateInterface` checks `ctx.minecraft().isSameThread()` and throws before doing work. | Verified (`BlockStateInterface.java:72-74`); semantics of `isSameThread()` Reported (Minecraft internals) |
| T4 | Path calculation runs on `Baritone.getExecutor()` threads and writes `current`/`next` under `pathPlanLock`. Game-thread readers in `tickPath` hold the same lock, but the public getters do not. | Verified |
| T5 | Engine threading *requirements* (as opposed to the contract's own game-thread restriction) are not documented anywhere in source. | Unknown (unchanged) |

## 6. Existing diagnostic and logging mechanisms

| Mechanism | Thread behaviour | Suitability as the contract's diagnostic sink | Label |
|---|---|---|---|
| `Helper.logDirect` / `logNotification` | Builds the component on the caller's thread and posts delivery via `Minecraft.execute(...)`. | Chat output, not test-readable. Whether submission is safe from any thread is Reported (Minecraft internals). | Verified (`Helper.java:142-180`) |
| `FaultBook` | Static and `synchronized`, with file I/O to `faults.jsonl`. `record()` invokes a context supplier that reads `ctx.playerFeet()` (game state) on the **caller's** thread, wrapped in a try/catch. | Not suitable as is. It reads game state from the caller's thread, does file I/O, and serves a different purpose. Off-thread safety is **Unknown**. | Verified (`FaultBook.java:47-92`; `PathingBehavior.java:97-110`) |
| Any in-process, test-readable diagnostic sink | **None exists.** | Must be introduced in Stage 2. Its thread-safety cannot be inherited from existing code. | Verified (absence) |

## 7. Assessment: what can establish reuse?

### 7.1 Confirmed runtime disappearance: **not observable in normal operation**

- The controlling runtime is the primary `IBaritone` plus the Tungsten static singletons (B4, B5). These persist across world, dimension and server changes for the lifetime of the client process (**Verified**).
- An engine instance going away proves nothing. It owns nothing (B2, B3).
- The OUT-tick cleanup is an engine *action* on Baritone pathing. It is not disappearance, and it does not cover engine- or TenorClef-started Tungsten travel (§4).
- **Conclusion (Verified by structure).** The rule's path (b), confirmed runtime disappearance, is effectively unavailable within a client session. Reuse can only come from path (a), resource-safety evidence.

### 7.2 Engine-resource safety: **partially observable, conservatively, not attributably**

There is a candidate composite of **existing public accessors**, read on the game thread. It needs no new primitive, scheduler, or engine change:

- Baritone executor idle: `getCurrent() == null && getNext() == null` (Verified observable)
- No calculation able to re-arm: `getInProgress().isEmpty()`, valid only if no `forceCancel()` occurred since (Verified observable; validity caveat Verified)
- No process steering: `mostRecentInControl().isEmpty()` and `CustomGoalProcess.isActive() == false` (Verified observable)
- No forced movement keys: `isInputForcedDown(i) == false` for MOVE_* / JUMP / SNEAK (Verified observable)
- Tungsten idle when bound: `TungstenMovementBackend.INSTANCE.isPathing() == false && !isStopping()`, read **directly**, not through a fresh engine instance (Verified observable)
- Tungsten key release: **Unknown** for `main` (O2)

The quality of this evidence:

- **It is engine-wide quiescence, not operation-scoped release.** If the whole shared resource is idle, no prior operation can be controlling it, so it is *sufficient in principle* on the Baritone side. That sufficiency is Unknown until controlled tests, because of race R1 and the deferred safe-point cancel. It is not *necessary*: any unrelated process (e.g. mining) keeps it false, and the resource stays Quarantined. That is fail-closed, as the specification requires. Using quiescence as evidence does **not** claim engine-wide exclusivity.
- **It must be sustained, not sampled.** R1 means a single snapshot can be overturned by a late calculation result. Two things bound the window: `inProgress` being non-null for the thread's lifetime (absent `forceCancel`), and the `primaryTimeoutMS`/`failureTimeoutMS` settings (default 500/2000 ms; Verified as settings, whether the thread honours them Reported). `confirmStopWindowTicks` stays a provisional concept with no numeric default.
- **Tungsten-backed operations cannot reach `Cancelled`** on Ostinato `main` without the Tungsten 1.21.x source or controlled tests. They resolve to `Uncertain` → Quarantined.

### 7.3 Mapping to the evidence-driven cancellation state machine

| State | Supportable from source today? |
|---|---|
| Executing | Yes: `PathResult.isAccepted()` (instance-local) |
| Cancelling | Yes: `cancel()` invoked. `status() == CANCELLED` **is this state, not `Cancelled`** (S1). |
| StoppedPathing | Baritone: candidate composite (current/next/inProgress/process) — Verified observable, sufficiency Unknown. Tungsten: `isPathing() == false && !isStopping()` — Verified observable, sufficiency Unknown. |
| Cancelled | Baritone: StoppedPathing + no forced movement keys, sustained across the window — observable, sufficiency Unknown pending tests. Tungsten on `main`: **not reachable** (O2). |
| Uncertain | Always reachable. It is the correct outcome wherever the above is not established. |

## 8. Interference observations (recorded for Stage 4; no action taken)

These are classification inputs only. No caller migration and no scheduling change are proposed.

1. **Best-effort `cancel()` is engine-wide (B6).** On epoch change, the canonical rule's step 3 will also stop non-contract mining, builder, and other processes, plus any Tungsten travel started by TenorClef. Expected class: *Detected* (process `onLostControl` is visible). Using it is permitted by the specification ("best-effort cancel() if the engine remains accessible"). The side effect is recorded here so that it is not mistaken for contract-level exclusivity.
2. **Non-contract writers to the same resource:** 24 classes outside the handler force keys; `CustomGoalProcess` starts Tungsten itself; TenorClef starts Tungsten via `TungstenBridge` and falls back to `forceCancel()` + `onLostControl()` (TenorClef `MovementEngineAdapter.java:240-257`, outside the basis). They are Detectable through the quiescence composite. They are *Undetectable* as to which writer holds the resource.
3. **`forceCancel()` by any caller** invalidates the `inProgress` signal (R1). Expected class: *Undetectable* from contract-visible state after the fact.

## 9. Stop-condition status

| Stop condition | Status | Basis |
|---|---|---|
| No reliable safety or confirmed-disappearance evidence can be established from source (and later controlled tests) | **Not triggered, conditionally.** Disappearance is unavailable (§7.1). Baritone-side safety has an observable, conservative composite whose sufficiency needs controlled tests (§7.2). Tungsten-on-`main` override evidence is Unknown. | §7 |
| Upholding quarantine requires a new scheduler, stop primitive, lifecycle manager, caller migration, or core scheduling change | **Not triggered.** The composite uses existing accessors only. Quarantine as `{Available, Quarantined}` fails closed whenever the composite is false. Baritone's existing safe-point deferral completes Baritone cancels with no new primitive. | §3.2, §7.2 |
| Off-thread rejection cannot be implemented without unsafe engine or sink access, and no safe fallback exists | **Not triggered.** Detection without an engine call has an existing precedent (`isSameThread()`, T3). Safe recording is Unknown (§6), so the fallback is reject without recording and document the limitation, which the specification permits. | §5, §6 |
| Stage 3 cannot preserve single-consumer IMovementEngine behaviour | **Not assessable in Stage 1.** No finding prevents delegation. Note that "direct use" today means a fresh instance per call (B3). | — |
| Override or pathing evidence is permanently unobservable for normal cancels | **Not triggered for Baritone.** **Open for Tungsten on `main`**: not shown to be *permanent*, but unobservable with the source available here. | O1, O2 |
| Common observation surface across target versions too small | **Not assessed.** Only `main` was inspected, and per-version branches were out of scope. | §0 |

## 10. Recommendation

**Conditional GO to Stage 2 (discovery and read-only observations), and nothing further.**

Conditions carried forward. These are constraints, not new design:

1. Stage 2 must treat every signal in §3 as an *observation* with availability and freshness. No signal in this audit may be promoted to proof of `Cancelled` or of resource safety without later controlled tests.
2. `status() == CANCELLED` must be treated as *Cancelling* (cancel requested), never *Cancelled* (S1).
3. `IMovementEngine` status and `isPathing()` must not be read through a throwaway instance as evidence of shared-resource state (B3, §3.1).
4. Tungsten-backed operations stay `Uncertain` → Quarantined until the 1.21.x Tungsten source is inspected or controlled tests establish key release. If that cannot be done, the stop condition "override evidence permanently unobservable" applies **to the Tungsten backend**.
5. Race R1 means old-context calculation results can re-arm Baritone pathing today. The contract must reject such results on its side, as context-validity rule 1 already requires. It must not expect Ostinato to do so.

Stage 1 ends here. Stage 2 has not been started.
