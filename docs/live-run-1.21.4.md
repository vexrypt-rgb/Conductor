# Live runs on Minecraft 1.21.4 (Ostinato branch `1.21.4`, 2026-10-12)

Same live harness and Stage 4 scenario as on 1.21.11 ([stage4-release.md](stage4-release.md)). Raw data:
[`evidence/live-run-2026-10-12-stage4-1.21.4.jsonl.gz`](evidence/live-run-2026-10-12-stage4-1.21.4.jsonl.gz).

## Stage 4 on 1.21.4: all checks passed

| Step | Result |
|---|---|
| Startup | AVAILABLE after 20 quiet ticks |
| Travel, cancel after 20 ticks | **CANCELLED exactly 20 ticks after cancel** (the evidence window), speed 0.0 |
| Reuse | Granted on evidence; ran to the end (1 block from target), then UNCERTAIN "outcome not established" |
| Another process steering (`ExploreProcess`) | Detected; grant refused (`RESOURCE_NOT_RELEASED`); AVAILABLE again after it was stopped |
| Water | Cancel confirmed. The player stopped at x≈4.1, before the pool (x≥10), so `AirProcess` had nothing to do. |
| Nether teleport | Pre-change observation rejected (`CONTEXT_INVALIDATED`); context moved to epoch 2 |

## The two engines behave differently, and the same contract handled both

| | 1.21.11 (`main`) | 1.21.4 |
|---|---|---|
| Walking before cancel | sprinting (`MOVE_FORWARD`+`SPRINT`, ~0.28 blocks/tick) | walking (`MOVE_FORWARD`, 0.153) |
| `cancel()` | segment keeps running (≈ 99 ticks in this scenario) | segment and inputs clear **on the cancel tick** |
| Physical stop after control release | ~6 ticks | 8 ticks |
| CANCELLED reported | 119 ticks after cancel | 20 ticks after cancel |

This is the point of evidence-based release: Conductor makes no assumption about how a given engine version stops. It waits until it can see the stop.

## Ostinato 1.21.4 bug found: mining by name deadlocks the game

**Verified** (thread dump + source diff). `BlockOptionalMeta.ServerLevelStub.load()` on the 1.21.4 branch passes `Minecraft.getInstance()` as the executor for `ReloadableServerRegistries.reload(...)`. `main` uses `ForkJoinPool.commonPool()`. The first `mineByName` triggers `load()` and then `registryAccess.join()` **on the render thread**, which waits for work only the render thread can run, so the game freezes.

```
"Render thread" WAITING (parking) on CompletableFuture
  at BlockOptionalMeta$ServerLevelStub.registryAccess(BlockOptionalMeta.java:289)   // registryAccess.join()
  at BlockOptionalMeta$ServerLevelStub.holder(BlockOptionalMeta.java:293)
  at BlockOptionalMeta.getDrops(BlockOptionalMeta.java:255)
```

**Suggested fix (Ostinato `1.21.4` branch, one line, matching `main`):** in `src/api/java/baritone/api/utils/BlockOptionalMeta.java`, `load()`: replace `Minecraft.getInstance()` with `ForkJoinPool.commonPool()` as the last argument of `ReloadableServerRegistries.reload`. Not applied: it is an Ostinato change on a branch outside this work. Meanwhile the harness uses `ExploreProcess` for its interference step.

## Version-agnostic module: the same jar on both versions

The example module ([`examples/walk-and-stop`](../examples/walk-and-stop)) is built once (SHA-256 `78d6ca6f1d39c3738059540dbdb2dcb7265742f8d4a673fe56a7f8533264b446`). Its bytecode references only `conductor.contract.*` and the JDK. The **same jar** was loaded on each client next to that version's Conductor mod. Logs: [`evidence/module-runs/`](evidence/module-runs).

| | 1.21.4 | 1.21.11 |
|---|---|---|
| Module found via `"conductor"` entrypoint | yes | yes |
| `onReady` context | `(session 1, epoch 1, overworld)` | `(session 1, epoch 1, overworld)` |
| Walk 20 blocks (target from `PLAYER_POSITION`) | EXECUTING | EXECUTING |
| Cancel → confirmed stop | 20 ticks | 45 ticks |
