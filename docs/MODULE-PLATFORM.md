# Conductor module platform: many modules, side by side

Status: **design, being implemented.** Owner directive (2026-10-12): make Ostinato, TenorClef, Autotune and future mods usable as modules **alongside each other**, with full design rights. This document records the design and the decisions it changes.

## 1. What the existing projects need (survey, Verified by source)

| Shared resource | Used by |
|---|---|
| **MOVEMENT**: pathing plus movement keys | Ostinato travel/mine/build/follow/explore/farm/elytra/boat/air processes, combat movement, TenorClef tasks (36 files call `getPathingBehavior`, 17 `getCustomGoalProcess`, 11 `getExploreProcess`) |
| **LOOK**: player rotation | combat aim; Autotune `CombatPotions` and `CombatBuckets`; mining and building targeting (`LookBehavior`) |
| **HOTBAR**: selected slot and inventory moves | Autotune totem, potions and mending; mining tools; building blocks; TenorClef crafting (`SlotClickMixin`) |
| **INTERACT**: attack and use clicks | combat; mining (`CLICK_LEFT`); building (`CLICK_RIGHT`); TenorClef (`ClientBlockBreakMixin`, `ClientInteractWithBlockMixin`) |
| **SCREEN**: open GUIs and containers | TenorClef (`ClientOpenScreenMixin`, furnace and crafting), Ostinato GUI |
| **CHAT**: sending messages and commands | TenorClef chat control, Ostinato's 44 commands, Ostinato swarm (whisper transport) |
| HUD / render (not exclusive) | Autotune `AcsHud`, TenorClef overlay, Ostinato path rendering |

TenorClef alone is 723 files with 36 mixins into Minecraft. It will not become a contract-only module soon. A platform that only hosted contract-only modules would exclude the projects it is meant to combine.

## 2. Decisions

| # | Decision | Replaces |
|---|---|---|
| D1 | **Many modules at once.** Each module gets its own `Conductor` handle, bound to its `ModuleId`. | Stage 3 single consumer |
| D8 | **Modules are plain jars.** A module is declared in `META-INF/services/conductor.contract.ConductorModule` and found by `ModuleDiscovery` in `<game dir>/conductor/modules` or on the game's classpath. No loader metadata. The loader is touched only by a thin boot shim ([LOADER-INDEPENDENCE.md](LOADER-INDEPENDENCE.md)). | `"conductor"` Fabric entrypoint |
| D2 | **Two kinds of module, one coordination system.** *Contract modules* import only `conductor.contract` and are version-agnostic (one jar). *Native modules* may use Minecraft, Fabric or the engine directly, so they are version-specific, and they cooperate through the same leases, events and services. Existing mods join as native modules first and move features to contract capabilities over time. | contract-only modules |
| D3 | **Resource leases with priorities.** Every shared resource in §1 is leasable. A lease is held by at most one module. Equal or lower priority: the request is denied (first holder wins). Higher priority: **preemption**. The holder is notified, its contract operations on that resource are cancelled, and the lease is handed over only when the resource is shown free: by release evidence for MOVEMENT (Stage 4 rules), or by the holder's acknowledgement for resources without evidence signals yet. Priorities are fixed levels, not a scheduler: BACKGROUND < NORMAL < HIGH < SAFETY. | "no automatic preemption" |
| D4 | **Inter-module communication.** Typed **topics** (publish/subscribe), delivered on the game thread at the end of the tick in publish order, so there are no re-entrant calls. Typed **services** (provide/look up). Modules declare the services they **require**, and the manager starts them in dependency order. | none |
| D5 | **Management.** Lifecycle per module: `DISCOVERED → RUNNING ⇄ DISABLED`, plus `BLOCKED` (missing dependency) and `FAILED` (threw). Enable/disable at runtime; disabling revokes leases (with cancel), withdraws services and subscriptions, and blocks dependents. Failure isolation: an exception stops only that module. The manager publishes a `ModuleManagement` service, so a UI, chat or command module can drive it. | "no lifecycle manager" (from the original no-expansion fences) |
| D7 | **Content-based features, never version checks.** Modules ask `content(ContentId)` whether an item, block, entity type, effect or engine feature exists (PRESENT / ABSENT / UNKNOWN; ABSENT only after looking). A module declares optional `ModuleFeature`s with the content each needs. Before `onReady`, the host turns a feature on only if all of it is PRESENT, and logs every decision. Worked example: the spear part of a combat module is off on 1.21.4 while the rest runs. | version-specific builds of a module |
| D6 | **Engine activity attribution.** A module holding MOVEMENT may declare the engine processes it drives (a native TenorClef bridge driving `MineProcess`, say). Activity by those processes is that module's, not interference. Activity nobody declared stays DETECTED interference, as in Stage 4. | none |

**Unchanged:**
- Evidence-based release (no grant while the resource is unsafe; a timeout never releases).
- Context validity.
- The game-thread rule.
- Version-specific code stays in the runtime adapter.
- Ostinato is never rewritten, and it does not depend on Conductor.

## 3. How it works (contract view)

```java
public interface ConductorModule {
    ReleasePolicy releasePolicy();
    void onReady(ModuleContext conductor);
    default void onTick(ModuleContext conductor) {}
    default Priority priority() { return Priority.NORMAL; }            // default for this module's leases
    default Set<ServiceKey<?>> requires() { return Set.of(); }         // started only when these are provided
    default List<ModuleFeature> features() { return List.of(); }      // optional parts, see D7
    default void onLeaseGranted(ModuleContext c, Lease lease) {}            // a pending request was granted
    default void onLeaseRevoked(ModuleContext c, Lease lease, ModuleId by) {} // stop using it now
    default void onDisable(ModuleContext conductor) {}
}
```

`ModuleContext` (per module, extends `Conductor`) adds:

```java
ModuleId self();
LeaseResult acquire(ResourceKey resource);                 // GRANTED, PENDING (preempting), or DENIED (holder, reason)
LeaseResult acquire(ResourceKey resource, Priority p);
void release(Lease lease);
List<Lease> leases();                                      // everything currently held, by anyone
<T> void publish(Topic<T> topic, T message);
<T> Subscription subscribe(Topic<T> topic, Consumer<T> handler);
<T> void provide(ServiceKey<T> key, T service);
<T> Optional<T> service(ServiceKey<T> key);
FeatureStatus feature(String name);                        // D7
Map<String, FeatureStatus> features();
void declareEngineProcesses(Set<String> processes);        // D6, native modules
```

Navigation needs the MOVEMENT lease. `requestTravel` takes it automatically when it is free, and gives it back when the operation ends. To keep MOVEMENT across several trips, acquire it explicitly.

System topics, published by Conductor:
- `Topics.LEASES`: lease granted, revoked, released.
- `Topics.MODULES`: lifecycle changes.

## 4. How the existing projects map onto it

| Project | First step (native module) | Later (contract capabilities) |
|---|---|---|
| **Ostinato** (engine) | **Done:** the runtime adapter provides capabilities, and **Ostinato's module edition** (Ostinato branch `conductor-module`, 1.21.11; one jar, Ostinato plus its native module) makes Ostinato's own commands take the MOVEMENT lease through Ostinato's command execution guard (refused or queued when another module holds it; stopped with proof when a higher one takes over). It offers every Ostinato command to other modules through `EngineCommands`. Verified live, in the dev client and in a production Fabric launch. | Navigation and combat are contract capabilities. Mining, building, follow, explore and farm are reachable through `EngineCommands` now, and each can still become a full capability through the audit gate. |
| **TenorClef** | A native module (bridge) that takes MOVEMENT/INTERACT/HOTBAR/SCREEN leases around its tasks, declares the engine processes it drives, and publishes its task state on a topic. No rewrite. | Task by task, move travel/mining calls to contract operations. |
| **Autotune** (Ostinato `autotune` branch) | **Done as a contract module:** [`examples/agnostic-combat`](../examples/agnostic-combat) fights through the Stage 5 combat capability at HIGH priority, publishes fight state, and turns its spear, mace, wind and Autotune parts on only where the game and engine have them. Verified live on both versions. | Autotune's tuning stays inside the engine. A contract-level "combat settings" capability would be needed to let a module tune it. |

What is verified, and what is still only design, is tracked in §6 as the work lands.

## 5. Limits of this design

- **Native modules are trusted to honour leases.** Conductor can *detect* engine activity (MOVEMENT) but cannot stop a native module from writing keys or rotation directly. LOOK, HOTBAR, INTERACT, SCREEN and CHAT have no evidence signals yet, so hand-over there relies on acknowledgement.
- **Preemption hand-over is as slow as the engine's stop** (up to ~17 s observed on 1.21.11). A SAFETY module that must act *immediately*, such as a totem swap, should use a resource that isn't held, or accept the wait. Faster hand-over would need a stronger engine stop (`forceCancel`), which is an Ostinato decision.
- **No hot unloading of code.** Disable stops a module logically; its classes stay loaded.

## 6. Verification log

Each part is listed as it is implemented and tested, with evidence labels.

| Part | Status | Evidence |
|---|---|---|
| D1 many modules, per-module context | implemented | **Verified (unit):** `PlatformConformanceTest`. **Verified (live, both versions):** two modules side by side, loaded as plain jars ([live-run-combat.md](live-run-combat.md) §Multi-module). |
| D3 leases, priorities, preemption | implemented | **Verified (unit):** first holder wins; higher priority preempts; MOVEMENT hand-over waits for release evidence (a mutation that removed the evidence gate was caught); scoped travel lease. **Verified (live, both versions):** a HIGH combat module preempted a NORMAL walker mid-walk; the lease was granted only after the stop was proven. |
| Module discovery without a loader | implemented | **Verified (unit):** `ModuleDiscoveryTest` compiles and jars real modules, finds them through `META-INF/services`, and reports a broken entry without losing the others. **Verified (live, both versions).** |
| D4 topics, services, dependency order | implemented | **Verified (unit):** end-of-tick delivery in order, delivery cap, `requires()` blocking and unblocking. |
| D5 management, failure isolation | implemented | **Verified (unit):** disable revokes leases and cancels travel; a throwing module becomes FAILED and the others keep running. |
| D6 engine-process attribution | implemented | **Verified (unit).** No native module exists yet to exercise it live. |
| D7 content detection and features | implemented | **Verified (unit):** `VersionFeatureTest` (contract): the same module gets `[melee, spear, mace]` on a 1.21.11-like runtime and `[melee]` on a 1.21.4-like one; UNKNOWN keeps a feature off. `CoreRuntimeTest` (runtime, both versions): registry lookups, engine-feature table and discovery listing; `OstinatoLayerTest` (engine layer, run against both versions' Ostinato builds): the real engine-feature probe. **Verified (source):** 1.21.11 registers 7 spear items; 1.21.4 has none; `CombatSpear` and `CombatMace` exist on Ostinato's `1.21.11` branch and not on `1.21.4`. **Verified (live, both versions):** the same jar turned spear/mace/wind ON on 1.21.11 and OFF on 1.21.4 with the reasons logged; see [live-run-combat.md](live-run-combat.md). |
| Stage 5 combat capability | implemented | **Verified (unit):** `Stage5CombatTest` (10), `CombatAdapterTest` (6 per version); mutation that drops the use-key rule fails 2 tests. **Verified (live, both versions):** `requestCombat(hostiles)` killed a summoned zombie; cancel confirmed on evidence after 20 ticks. |
