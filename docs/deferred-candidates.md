# Deferred capability candidates

These capabilities may later be exposed through the Conductor contract. **None of them is in scope yet.** This file only keeps them on record. It authorises no work, no interfaces and no caller migration.

Basis: Ostinato `dfce2a2` (`main`). Process names are **Verified** against `src/main/java/baritone/process/` and the registrations in `Baritone.java`. Everything about their behaviour is **Unknown** until each candidate has its own audit.

## Candidates

| Candidate | Existing code (Verified names) | Why it is deferred |
|---|---|---|
| **Combat (PvP / PvE)** | `PvpProcess`, `PveProcess`, `PvpOpponent`, `PvpRecorder`, `MobProfile`, and the `Combat*` helpers (`CombatAim`, `CombatApproach`, `CombatClick`, `CombatDefense`, `CombatElytra`, `CombatExplosives`, `CombatGeometry`, `CombatInventory`, `CombatMace`, `CombatMelee`, `CombatMovement`, `CombatPearl`, `CombatPhase`, `CombatPolicy`, `CombatShield`, `CombatSpear`, `CombatSurvival`, `CombatSwing`, `CombatTargeting`, `CombatTools`, `CombatWind`) | Both processes are registered with priority 2 and force inputs directly (`PvpProcess.java:593`, `PveProcess.java:457`; stage1-audit §3.3). Combat therefore shares the movement resource. Arbitrating that through the contract would need a scheduler, preemption or a process-scheduling change, all of which are scope fences. |
| ↳ Combat on Ostinato branch `autotune` | Commit `9029ac0`, one commit on top of the audited `dfce2a2`. Adds the "Autotune Combat System": `CombatTuner`, `CombatFightTuner`, `CombatOutcome`, `CombatBuckets`, `CombatLag`, `CombatMending`, `CombatPotions`, `AcsStatus`, an `AcsHud` HUD, and durability-aware gear handling. Expands `PvpProcess`, `CombatPolicy`, `CombatInventory`, `CombatPearl` and others. Adds 14 test classes. | Same reason as combat above. **Verified by diff:** it changes none of the files the Stage 1 audit or Stage 2 adapter rely on (`baritone.movement`, `PathingBehavior`, `InputOverrideHandler`, `PathingControlManager`, `CustomGoalProcess`, `MixinMinecraft`, `BaritoneProvider`, `GameEventHandler`). Its only API change is five additive settings (`pvpAutotune`, `renderAcsHud`, `acsHudAnchor`, `acsHudOpacity`, `acsHudScale`). Its runtime behaviour is Unknown, and it has not been audited. |
| Mining | `MineProcess` | The specification says mining is reused, not rewritten. It shares the pathing and input resource. |
| Building | `BuilderProcess`, `BackfillProcess`, `PortalBuildProcess` (+ `portal/`) | Same as mining. |
| Farming | `FarmProcess` | Forces inputs directly (stage1-audit §3.3). |
| Exploration / following | `ExploreProcess`, `FollowProcess`, `GetToBlockProcess` | These overlap with navigation. Decide after Stage 3 whether they are navigation goals or separate capabilities. |
| Elytra / air / boat travel | `ElytraProcess` (+ `elytra/`), `AirProcess`, `BoatProcess` | These affect `isSafeToCancel()` (elytra) and force inputs directly. |
| Inventory pausing | `InventoryPauserProcess` | Temporary process: it may keep control through `cancelEverything()` by design (`PathingControlManager.java:70`). |

The list above is a starting inventory. Further candidates are added by the project owner's decision only.

## Gate for any candidate (all required, in order)

1. Stages 2–4 are complete for navigation, and Stage 4's interference classification covers this candidate. Navigation `cancel()` already stops every process (stage1-audit B6).
2. The candidate has its own Stage 1-style audit, labelled Verified / Reported / Unknown, covering:
   - how it stops, and whether a stop invocation differs from a confirmed stop;
   - whether its input, rotation and block-interaction overrides are cleared;
   - its threading and lifecycle;
   - whether resource-safety evidence is observable.
3. An explicit owner decision to add it to the roadmap.
4. The exposure works without a universal resource scheduler, automatic preemption, Baritone process-scheduling changes, new stop primitives, or repository-wide TenorClef migration. If it cannot, the candidate stays deferred under the stop condition "upholding quarantine requires a forbidden expansion".
