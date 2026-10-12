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
| D2 | **Two kinds of module, one coordination system.** *Contract modules* import only `conductor.contract` and are version-agnostic (one jar). *Native modules* may use Minecraft, Fabric or the engine directly, so they are version-specific, and they cooperate through the same leases, events and services. Existing mods join as native modules first and move features to contract capabilities over time. | contract-only modules |
| D3 | **Resource leases with priorities.** Every shared resource in §1 is leasable. A lease is held by at most one module. Equal or lower priority: the request is denied (first holder wins). Higher priority: **preemption**. The holder is notified, its contract operations on that resource are cancelled, and the lease is handed over only when the resource is shown free: by release evidence for MOVEMENT (Stage 4 rules), or by the holder's acknowledgement for resources without evidence signals yet. Priorities are fixed levels, not a scheduler: BACKGROUND < NORMAL < HIGH < SAFETY. | "no automatic preemption" |
| D4 | **Inter-module communication.** Typed **topics** (publish/subscribe), delivered on the game thread at the end of the tick in publish order, so there are no re-entrant calls. Typed **services** (provide/look up). Modules declare the services they **require**, and the manager starts them in dependency order. | none |
| D5 | **Management.** Lifecycle per module: `DISCOVERED → RUNNING ⇄ DISABLED`, plus `BLOCKED` (missing dependency) and `FAILED` (threw). Enable/disable at runtime; disabling revokes leases (with cancel), withdraws services and subscriptions, and blocks dependents. Failure isolation: an exception stops only that module. The manager publishes a `ModuleManagement` service, so a UI, chat or command module can drive it. | "no lifecycle manager" (from the original no-expansion fences) |
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
    void onReady(Conductor conductor);
    default void onTick(Conductor conductor) {}
    default Priority priority() { return Priority.NORMAL; }            // default for this module's leases
    default Set<ServiceKey<?>> requires() { return Set.of(); }         // started only when these are provided
    default void onLeaseGranted(Conductor c, Lease lease) {}            // a pending request was granted
    default void onLeaseRevoked(Conductor c, Lease lease, ModuleId by) {} // stop using it now
    default void onDisable(Conductor conductor) {}
}
```

`Conductor` (per module) adds:

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
```

Navigation needs the MOVEMENT lease. `requestTravel` takes it automatically when it is free, and gives it back when the operation ends. To keep MOVEMENT across several trips, acquire it explicitly.

System topics, published by Conductor:
- `Topics.LEASES`: lease granted, revoked, released.
- `Topics.MODULES`: lifecycle changes.

## 4. How the existing projects map onto it

| Project | First step (native module) | Later (contract capabilities) |
|---|---|---|
| **Ostinato** (engine) | Provides capabilities through the runtime adapter. Its chat commands and GUI drive the engine outside Conductor; that activity is attributed as *unmanaged engine activity* (DETECTED). | Navigation done. Mining, building, follow, explore, farm, combat as capabilities, each through the audit → observe → delegate → evidence gate. |
| **TenorClef** | A native module (bridge) that takes MOVEMENT/INTERACT/HOTBAR/SCREEN leases around its tasks, declares the engine processes it drives, and publishes its task state on a topic. No rewrite. | Task by task, move travel/mining calls to contract operations. |
| **Autotune** (Ostinato `autotune` branch) | A native module at SAFETY/HIGH priority that preempts MOVEMENT, LOOK and HOTBAR when a fight starts; it publishes fight state. | A combat capability over `PvpProcess`/`PveProcess` once audited (stop semantics, input clearing). |

What is verified, and what is still only design, is tracked in §6 as the work lands.

## 5. Limits of this design

- **Native modules are trusted to honour leases.** Conductor can *detect* engine activity (MOVEMENT) but cannot stop a native module from writing keys or rotation directly. LOOK, HOTBAR, INTERACT, SCREEN and CHAT have no evidence signals yet, so hand-over there relies on acknowledgement.
- **Preemption hand-over is as slow as the engine's stop** (up to ~17 s observed on 1.21.11). A SAFETY module that must act *immediately*, such as a totem swap, should use a resource that isn't held, or accept the wait. Faster hand-over would need a stronger engine stop (`forceCancel`), which is an Ostinato decision.
- **No hot unloading of code.** Disable stops a module logically; its classes stay loaded.

## 6. Verification log

(Filled in as each part is implemented and tested.)
