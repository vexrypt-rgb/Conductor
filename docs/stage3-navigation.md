# Conductor Stage 3: Single-consumer navigation delegation

Status: **implemented, unit-tested, and run live once** (see [live-run-2026-10-12.md](live-run-2026-10-12.md)). Every Stage 3 rule behaved as specified in a real client. Stage 4 has not been started.

Basis: Ostinato `dfce2a2` (`main`). Builds on Stage 2 (`docs/stage2-observations.md`) and the Stage 1 conditions (`docs/stage1-audit.md` §10).

## 1. What Stage 3 adds

One consumer can send one travel request to Ostinato's existing `IMovementEngine`, cancel it, and read the contract's view of its state.

| Binding call | Delegates to | Notes |
|---|---|---|
| `requestTravel(NavigationGoal)` | `IMovementEngine.goTo(MovementGoal)`, exactly once | Goal kinds BLOCK, GET_TO_BLOCK, NEAR, XZ. Follow is not offered. |
| `cancel(OperationId)` | `IMovementEngine.cancel()`, at most once per operation | Result is at most **CANCELLING**. |
| `operationStatus(OperationId)` | nothing; contract state only | Never reads the engine's `status()`, `isPathing()` or `activeBackend()` (audit S1). |

- **Single consumer, not a shared service.** `ConductorOstinato.install` refuses a second install on the same `IBaritone`. The consumer gets **one held** `HybridMovementEngine` instance, rather than a fresh one per call as TenorClef's adapter uses (audit B3).
- **No callers migrated.** TenorClef and Ostinato are unchanged. No scheduler, preemption, stop primitive or process-scheduling change was added.

## 2. Rules (normative for Stage 3)

| Rule | Behaviour |
|---|---|
| Game thread only | Off-thread calls are rejected before any engine call and recorded. |
| Contract-level exclusivity | At most one operation. A conflicting request is rejected **without** an engine call. This is not engine-wide exclusivity: other Ostinato code can still drive movement. |
| One grant per installation | Once the engine has been called, the grant is spent, even if the engine declines or throws. Reusing the resource needs resource-safety evidence, which is Stage 4. Later requests are rejected with `RESOURCE_NOT_RELEASED`. |
| Observable backend only | If Ostinato would route travel to Tungsten (`MovementBackends.preferTungstenTravel()`), the request is rejected with `BACKEND_UNOBSERVABLE` **before** any engine call, because Tungsten's stop cannot be observed (audit O2). If the engine reports a Tungsten backend anyway, the operation is `UNCERTAIN`. |
| Cancel requested ≠ confirmed | `cancel()` moves EXECUTING to CANCELLING only. STOPPED_PATHING and CANCELLED are defined but unreachable until Stage 4 evidence rules exist. A cancel that throws moves the operation to UNCERTAIN. |
| Context change | The operation loses context validity and authority. `cancel()` is invoked best-effort, and the state becomes CANCELLING, not CANCELLED. **No new operation is granted.** A cancel carrying the old id is rejected with `AUTHORITY_INVALIDATED`. |
| Diagnostics | The sink records: authority granted and invalidated, every state change, every cancel invocation (marked "not confirmation"), and every rejection with its reason. |

## 3. Comparison to direct use

| Aspect | Direct `IMovementEngine` use | Through Conductor | Evidence |
|---|---|---|---|
| Engine calls for "go to block, then cancel" | `goTo(GET_TO_BLOCK pos)`, `cancel()` | identical | **Verified by test** (`delegationMakesTheSameEngineCallsAsDirectUse`), call for call on a mock |
| Extra engine reads | caller's choice | none | **Verified by test** (`contractReadsNothingFromTheEngineInstance`) |
| Engine behaviour below `IMovementEngine` | unchanged | unchanged: the same API, the same implementation | Verified by construction. Live: the contract trial and direct trials showed the same post-cancel behaviour (segment runs to completion, L1). |
| Second request, Tungsten routes, follow goals, off-thread calls | allowed | rejected (fail closed) | Deliberate contract restrictions, not behaviour changes for accepted requests |

**Stop-condition check ("Stage 3 cannot preserve single-consumer IMovementEngine behaviour"): not triggered.** For every request the contract accepts, the engine receives exactly the calls a direct caller would make. The contract adds rejections, never different engine calls.

The real `HybridMovementEngine` cannot be exercised off-game: `MovementBackends.preference()` reads `BaritoneAPI.getSettings()`, whose static initialiser constructs a full `Baritone` around `Minecraft.getInstance()` (**Verified**, `BaritoneAPI.java`). A behavioural comparison of real movement therefore needs a live client.

## 4. Interference recorded for Stage 4 (no action taken)

- `cancel()` keeps its engine-wide effect (audit B6): it stops every Baritone process, including mining and building, and any Tungsten travel. Direct use has the same effect, so behaviour is preserved, but the contract does **not** claim the cancel is limited to this operation. Expected class: *Detected*.
- A settings change between the route check and the next game tick could still let `CustomGoalProcess` hand travel to Tungsten while the engine reported BARITONE. Likelihood Unknown. Expected class: *Undetectable* by the contract today.
- Movement started outside the contract (TenorClef, chat commands, other processes) is not blocked. Contract-level exclusivity only.

## 5. Tests

Run on 2026-10-12, `./gradlew clean test`, against Ostinato `dfce2a2` classes. **No live client run.**

| Suite | Tests | Failures |
|---|---|---|
| `contract` `Stage2ConformanceTest` | 18 | 0 |
| `contract` `ContractTypesTest` | 5 | 0 |
| `contract` `Stage3ConformanceTest` | 15 | 0 |
| `runtime-ostinato` `OstinatoRuntimeTest` | 7 | 0 |
| `runtime-ostinato` `Stage3NavigationTest` | 9 | 0 |

`Stage3ConformanceTest` covers the specification's list:
- cancellation requested vs confirmed;
- context invalidation vs resource release;
- mere sessionId/epoch change vs confirmed disappearance (no grant after either);
- no new grant while the resource is unsafe or uncertain;
- off-thread calls do not start or stop movement;
- sink evidence for authority and state transitions.

Resource quarantine vs ownership-token validity is Stage 4.

**Mutation checks.** Each injected fault was reverted afterwards and the suite re-run clean.

| Injected fault | Tests that failed |
|---|---|
| The port also calls `engine.status()` | 2 adapter tests |
| The one-grant limit removed | 6 tests across both modules |

## 6. Recommendation for Stage 4

**Update:** the live harness now exists (`live-harness/`, `scripts/live-run.sh`), and the first run settled several of the Unknowns below; see `live-run-2026-10-12.md`. The original recommendation follows.

Stage 4 (quarantine, evidence-based release, interference classification) should **wait for a live runtime harness**. Its release rule depends on facts the audit left Unknown and that only controlled runtime tests can settle:
- whether the quiescence composite (audit §7.2) is sufficient;
- how long Baritone's safe-point cancel takes;
- whether race R1 occurs in practice;
- whether Tungsten releases its keys.

Writing release logic before those are known would mean choosing evidence rules without evidence. Under the specification's stop conditions, that should not be done.
