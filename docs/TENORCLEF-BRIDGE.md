# TenorClef as a Conductor module: bridge design

Status: **design. Not built, not tested.** Inspection basis: TenorClef `0460461` (release 0.26.2).

Goal (project owner): TenorClef, Ostinato and Autotune run as modules **alongside each other**.

## 1. Facts the design rests on (Verified by source)

| # | Fact | Where |
|---|---|---|
| T1 | TenorClef is ~820 source files with 36 mixins, uses Yarn names (`MinecraftClient`), and is built per Minecraft version against an Ostinato dist jar. It cannot become a contract-only module. | `build.gradle` (`ostinatoVersions = [11601, 12104, 12111, 260300]`), `src/main/java` |
| T2 | All TenorClef behaviour runs through one `TaskRunner`, which does nothing while `active` is false. | `tasksystem/TaskRunner.java` (`tick()`: `if (!active ...) return`) |
| T3 | User tasks enter through one method, `UserTaskChain.runTask(mod, task, onFinish)`, which calls `TaskRunner.enable()`. 85 call sites go through `AltoClef.runUserTask` to reach it. | `chains/UserTaskChain.java` |
| T4 | Besides user tasks, survival chains act on their own while the runner is enabled: `MobDefenseChain`, `FoodChain`, `MLGBucketFallChain`, `WorldSurvivalChain`, `UnstuckChain`, and others. | `chains/` |
| T5 | TenorClef's own stop is `AltoClef.stopTasks()`: it cancels the user chain and stops the current chain. | `AltoClef.java:393` |
| T6 | TenorClef drives Ostinato processes directly (custom goal, mine, explore, follow, build, and so on) and writes inputs and hotbar slots through mixins. | MODULE-PLATFORM §1 survey |

## 2. Design: a native module that gates TenorClef's runner

A small class, `TenorClefBridge implements ConductorModule`, lives **in TenorClef's jar** and is declared in `META-INF/services/conductor.contract.ConductorModule`. Conductor finds it on the game's classpath (`ModuleDiscovery`). Without Conductor installed, nothing reads that file, so the bridge is never loaded and TenorClef behaves exactly as today (a soft dependency: `"suggests": {"conductor": "*"}`).

The bridge is a **native module** (D2): it is built for each TenorClef version, like TenorClef itself.

**Rule: TenorClef acts only while it holds its leases.**

1. **Before a task starts:** a one-line hook at the top of `UserTaskChain.runTask` (T3) asks the bridge for MOVEMENT, INTERACT, HOTBAR and SCREEN at the bridge's priority (NORMAL).
   - All granted: the task starts, as today.
   - Any PENDING: the task is queued and starts in `onLeaseGranted`, once Conductor has proven the previous holder stopped.
   - Any DENIED: the task is refused with a chat message naming the holder ("movement is held by agnostic_combat").
2. **While TenorClef runs:**
   - `declareEngineProcesses(...)` lists the Ostinato processes TenorClef drives (D6), so their activity counts as TenorClef's own, not interference.
   - The bridge publishes `tenorclef:task` (task name, started/finished/failed) every time it changes.
3. **On preemption** (`onLeaseRevoked`): the bridge calls `AltoClef.stopTasks()` (T5) and `TaskRunner.disable()` (T2), and publishes `tenorclef:task preempted by <module>`. This also covers the survival chains (T4), since they run only through the runner.
   - MOVEMENT hand-over still waits for Conductor's release evidence, as in Stage 4. TenorClef's stop is believed only once the bot is seen to be quiet.
4. **When the task finishes** (`onTaskFinish`): the bridge releases its leases. Survival chains that want to act while no user task runs must ask for leases the same way, through the same hook placed in `TaskRunner.enable()`.
5. **A service for other modules:** the bridge provides `ServiceKey<TenorClefTasks>("tenorclef:tasks")` with `run(String command)` and `stop()`. Contract modules call it with TenorClef's own command text (`"get iron_ingot 3"`), so they stay version-agnostic and need no TenorClef classes.

```java
// sketch: the hook in UserTaskChain.runTask, first line
if (!ConductorGate.mayStart(task, () -> runTask(mod, task, onFinish))) return;   // queued or refused
```

`ConductorGate` is a TenorClef class. With no Conductor present, it always says yes.

## 3. Why this design and not another

- **Gating at the runner, not at each mixin.** One choke point (T2/T3) instead of 36 mixins. It is also the place TenorClef itself uses to stop.
- **Acquire before acting.** If TenorClef started moving first and asked afterwards, MOVEMENT would read as busy (its own activity), and the request could never be granted. Asking first also means a higher-priority fight never has to wait for a TenorClef task to start up.
- **No change to Ostinato and no new Conductor mechanism.** Everything used here already exists and is unit-tested: leases, PENDING with evidence hand-over, `declareEngineProcesses`, topics and services. The multi-module live run ([live-run-combat.md](live-run-combat.md) §Multi-module) exercises the same preemption path between two contract modules.

## 4. Limits

- **Cooperation is trusted.** TenorClef's mixins can still write keys and slots without a lease. The bridge makes TenorClef *honour* leases; Conductor can't enforce them (MODULE-PLATFORM §5).
- **Chat commands typed by a person** still go through `runTask`, so they are gated as well. A person pressing keys is not gated.
- **Per-version builds.** The bridge is built with each TenorClef version. Contract modules that use it through the `tenorclef:tasks` service stay version-agnostic.

## 5. Steps to build it

1. Add `ConductorGate` and `TenorClefBridge` to TenorClef (about 150 lines), plus the hooks in `UserTaskChain.runTask` and `TaskRunner.enable`.
2. Unit-test the gate against `ModuleHost` with a fake runtime, the same way `PlatformConformanceTest` does.
3. Live-run TenorClef with Conductor and `examples/agnostic-combat` on 1.21.11. Expected:
   - a TenorClef `@goto` task is preempted by the combat module;
   - the stop is confirmed on evidence;
   - the fight runs;
   - the TenorClef task is reported as preempted, not silently lost.
