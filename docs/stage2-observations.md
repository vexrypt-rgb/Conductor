# Conductor Stage 2: Discovery and read-only observations

Status: **implemented and unit-tested; not run in a live game.** Stage 3 has not been started.

Basis: Ostinato `dfce2a2` (`main`, Minecraft 1.21.11). The Stage 1 conditions this stage carries are listed in `docs/stage1-audit.md` §10.

## 1. Scope

| In Stage 2 | Not in Stage 2 |
|---|---|
| Context identity (sessionId, epoch, dimension) and invalidation | Any movement operation (Stage 3) |
| Capability discovery (no operations listed) | Ownership authority and tokens (Stage 3/4) |
| Read-only observations with availability and freshness | `movementResourceState`, quarantine, evidence-based release (Stage 4) |
| Game-thread enforcement; off-thread rejection | Cancellation state machine (Stage 3/4) |
| In-process diagnostic sink | Combining signals into safety or stop verdicts (Stage 4) |

Existing movement is unchanged. No Ostinato or TenorClef source was modified. The only runtime change Conductor makes to Ostinato is registering one event listener (`IEventBus.registerEventListener`), and that listener only reads tick and world events.

## 2. Modules and dependency direction

```
consumer ──> conductor.contract.Conductor (Java binding: direct calls, no serialisation)
               │
               ▼
           ContractService  ── enforces the rules below
               │ calls through
               ▼
           RuntimeIntegration (SPI, owned by the contract)
               ▲ implemented by
               │
runtime-ostinato: OstinatoRuntimeIntegration ─ GameAccess (privileged MC reads)
                                             ─ OstinatoSignals (version adapter) ──> Ostinato API (read-only)
                                             ─ SecondaryBackendAccess ──> TungstenMovementBackend (read-only)
```

- `contract/`: pure Java 21 with no Minecraft or Ostinato dependency. It holds the normative types and rules, and is fully unit-testable.
- `runtime-ostinato/`: the runtime integration boundary and the version adapter for Ostinato `main`. All Minecraft-specific and privileged access is here.
- **Deviation to note.** At run time, calls follow the specification's diagram: contract → runtime → adapter → engines. At compile time, the contract→runtime edge is inverted through an SPI that the contract owns, so the contract compiles and tests without Minecraft. The graph is still acyclic, and existing engines still do not depend on Conductor. Getting the literal compile-time direction would mean making the contract depend on Minecraft. **This needs an owner decision if the diagram is meant literally at compile time.**

## 3. Normative rules (Stage 2)

### 3.1 Context validity
- `ContextId = (sessionId, epoch, dimension)`. An observation is valid only if all three equal the current context.
- The runtime integration moves the context on Ostinato lifecycle signals (game thread only):

| Signal (Ostinato) | Transition |
|---|---|
| `TickEvent` IN with no current context | new session, new epoch |
| `TickEvent` IN, connection object changed | old context ended; new session, new epoch |
| `TickEvent` IN, dimension changed | same session, new epoch |
| `WorldEvent` POST with non-null world | same session, new epoch, even in the same dimension |
| `WorldEvent` POST with null world, or `TickEvent` OUT | context ended (none current) |

- Epochs are never reused, even across sessions.
- **A context transition is an identity and lifecycle signal only.** It invalidates observations. It is never evidence that the engine stopped, that overrides cleared, or that the resource is safe.

### 3.2 Observations
- Availability (`OBSERVED`, `NOT_OBSERVED`, `NOT_APPLICABLE`, `UNKNOWN`) and freshness (`FRESH`, `STALE`) are evaluated independently.
- Only `OBSERVED` carries a value. A sampling exception, a missing sample, or an ambiguous `false` from a source becomes `UNKNOWN`, never a default or a negative.
- `observe(kind, policy)` samples now. `revalidate(observation, policy)` re-checks a held observation against the current context and the policy.
- An old-context observation is rejected (`CONTEXT_INVALIDATED`) however recent it is. Freshness is not evaluated for it.

### 3.3 Freshness
- `FreshnessPolicy(requiredFreshness, maxAgeTicks, maxAgeDuration)`:
  - `SAME_TICK` takes no bounds.
  - `BOUNDED_AGE` needs at least one bound, and every configured bound must hold.
- **There is no default policy.** A null policy is rejected with `NO_FRESHNESS_POLICY` before anything is sampled.
- A negative age (clock went backwards) counts as `STALE`.

### 3.4 Threading
- Contract restriction (intentional, not an engine requirement): every `Conductor` call must arrive on the game thread.
- Off-thread call: rejected with `OFF_THREAD` before any tick, clock, context, capability or engine read. The violation is recorded in the sink with `tick = -1` and `context = null`.
- An off-thread lifecycle event is not applied (the tracker is game-thread-confined) and is recorded.

### 3.5 Diagnostic sink
- `InMemoryDiagnosticSink`: in-process, bounded, test-readable.
- **Sink thread-safety is no longer Unknown.** Stage 1 found no existing sink, so this one was built thread-safe on purpose. Its only shared state is a `ConcurrentLinkedQueue` and atomics, so recording from any thread is safe by the `java.util.concurrent` contract. It does not log asynchronously, schedule work, or marshal to another thread.
- Stage 2 records: context established, invalidated and ended; observation rejections with their reason; provider faults; off-thread violations. Authority and resource-state transitions do not exist yet (Stage 3/4).

### 3.6 Discovery
- `discover()` returns the contract version, the current context (if any), the tick, and capability descriptors.
- **Every capability lists no operations in Stage 2.**

## 4. Adapter mapping (Ostinato `main`)

| Kind | Ostinato accessor (read-only) | Value rules | Label |
|---|---|---|---|
| `PATH_SEGMENT_PRESENT` | `IPathingBehavior.getCurrent() != null` | OBSERVED | Verified mapping |
| `NEXT_SEGMENT_PRESENT` | `IPathingBehavior.getNext() != null` | OBSERVED | Verified mapping |
| `PATH_CALCULATION_PENDING` | `IPathingBehavior.getInProgress().isPresent()` | OBSERVED. Empty does not prove no calculation thread is running (R1). | Verified mapping |
| `CONTROLLING_PROCESS` | `IPathingControlManager.mostRecentInControl()` → process class name | OBSERVED (`Optional`). Uses the class name because `displayName()` runs process code. | Verified mapping |
| `TRAVEL_GOAL_ACTIVE` | `ICustomGoalProcess.isActive()` | OBSERVED | Verified mapping |
| `FORCED_INPUTS` | `IInputOverrideHandler.isInputForcedDown(i)` for every `Input` | OBSERVED (set). Does not see vanilla key state that Tungsten writes. | Verified mapping |
| `SECONDARY_BACKEND_BOUND` | `TungstenMovementBackend.INSTANCE.isAvailable()` | OBSERVED | Verified mapping; the first call binds reflection and may post a chat line (same probe as `HybridMovementEngine.goTo`) |
| `SECONDARY_BACKEND_PATHING` | `TungstenMovementBackend.INSTANCE.isPathing()` | not bound → NOT_APPLICABLE; `true` → OBSERVED(true); **`false` → UNKNOWN** (the source returns `false` on reflection failure too) | Verified mapping |
| `SECONDARY_BACKEND_STOPPING` | `TungstenMovementBackend.INSTANCE.isStopping()` | Same rule as above. | Verified mapping |
| dimension | `level.dimension().identifier()` | Same accessor Ostinato's `WorldProvider` uses | Verified usage in Ostinato |
| game thread | `Minecraft.isSameThread()` | Same check Ostinato's `BlockStateInterface` uses | Usage Verified; Minecraft semantics Reported |
| connection identity | `Minecraft.getConnection()` (compared by reference, held weakly) | | Reported (Minecraft internals) |

`TungstenMovementBackend` is Ostinato-internal, not API. Using it is privileged access confined to `runtime-ostinato`.

**Raw signals only.** No kind is evidence of pathing stopped, overrides cleared, or resource safety. Stage 1 §7.2 records why each signal is insufficient on its own, and composing them is Stage 4 work.

## 5. Test status

Every test runs off-game with fakes and mocks. **No runtime test against a live client has been run.**

| Suite | Covers | Result |
|---|---|---|
| `contract` `Stage2ConformanceTest` | Availability vs freshness; old-context rejection (dimension, world replacement, session, connection, no context); no-policy rejection without sampling; missing data → UNKNOWN; unsupported kinds; off-thread rejects with zero reads and is recorded; discovery has no operations; sink records transitions and reasons | see §7 |
| `contract` `ContractTypesTest` | Policy validation (no implicit bounds); backwards clock → STALE; sample invariants; epochs never reused; sink bounded and safe under 8 threads | see §7 |
| `runtime-ostinato` `OstinatoRuntimeTest` | Each kind maps to its read accessor, with **no other engine interaction** (Mockito `verifyNoMoreInteractions`); Tungsten `false` → UNKNOWN; discovery availability; lifecycle events → context identity; old-dimension observation rejected; rejection before the first tick; off-thread calls touch no engine mock and leave the context alone | see §7 |

## 6. Stop-condition status after Stage 2

| Condition | Status |
|---|---|
| Off-thread rejection impossible without unsafe engine/sink access | **Cleared.** Rejection happens before any read, and the sink is safe by construction. |
| Upholding the rules needs a scheduler, stop primitive, lifecycle manager, migration or scheduling change | **Not triggered.** Stage 2 adds one read-only listener. |
| Common observation surface across versions too small | **Not assessed.** Only one version adapter exists. |
| Safety/disappearance evidence; override evidence for Tungsten on `main` | **Unchanged from Stage 1.** Stage 2 makes the raw signals observable but establishes no evidence. Tungsten `false` readings remain UNKNOWN. |

## 7. Results

Pending: `contract` tests pass (23/23, run locally). `runtime-ostinato` has not been compiled or tested yet; it waits on an Ostinato build.
