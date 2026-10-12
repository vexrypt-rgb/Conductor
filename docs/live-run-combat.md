# Live run: one combat module jar on 1.21.11 and 1.21.4 (2026-10-12)

**Question:** can one combat module jar run on two Minecraft versions, using the parts each version supports and skipping the rest without errors? The worked example is the spear: it exists on 1.21.11 but not on 1.21.4.

**Answer: yes. Verified live.**

## Setup

- Module: [`examples/agnostic-combat`](../examples/agnostic-combat), jar SHA-256 `e2a2cb1ce4db7ba77e8ff01b05d0b542afe2fcabe03ded33cbd58094a23d17bf`. The same file was loaded on both versions.
- Conductor mod for each version, Ostinato `main` (1.21.11) and Ostinato `1.21.4`, and a local vanilla server of the same version.
- `scripts/live-run.sh` with `HARNESS_SCENARIO=connect-only` and this `SERVER_ON_JOIN`:
  `difficulty easy; time set midnight; give Dev iron_sword, shield, iron_spear, mace; summon a zombie 5 blocks away`.
- Evidence (filtered client and server logs): `docs/evidence/module-runs/agnostic-combat-<version>.log.gz`.

## Results

| | 1.21.11 (Ostinato `main`) | 1.21.4 (Ostinato `1.21.4`) |
|---|---|---|
| `spear` feature | **ON** | **OFF**: `item:minecraft:iron_spear ABSENT, engine:ostinato:combat/spear ABSENT` |
| `mace` feature | **ON** | **OFF**: `engine:ostinato:combat/mace ABSENT`. The mace *item* exists on 1.21.4, but this engine build has no mace code. |
| `wind` feature | **ON** | **OFF**: `engine:ostinato:combat/wind ABSENT` |
| `autotune` feature | OFF (`main` has no Autotune; it is on the `autotune` branch) | OFF |
| Kit the module uses | sword, shield, spear, mace, wind charges | sword, shield |
| Kit check once items arrived | have sword, shield, spear, mace; missing wind charges (none given) | have sword, shield; missing nothing |
| `give … iron_spear` on the server | given | `Unknown item 'minecraft:iron_spear'` (server log) |
| Combat | `requestCombat(hostiles)` → EXECUTING; target `minecraft:zombie Zombie`; zombie killed (advancement *Monster Hunter*, ~3 s after summon) | same; zombie killed (~2 s after summon) |
| Stop | cancel after 100 ticks without a target → CANCELLING → **CANCELLED, confirmed 20 ticks later** (the release window) | same, **20 ticks** |
| Errors or exceptions from Conductor or the module | none | none |

## What this shows

- **Content checks, not version checks, carried the difference.** The module contains no version number. On 1.21.4 the spear part was never switched on: no spear in the kit, no "missing spear" report, no spear code path.
- **Features can depend on the engine too.** On 1.21.4 the mace exists as an item, but Ostinato's 1.21.4 build can't use it. Gating on both the item and `engine:ostinato:combat/mace` kept the module from expecting something the engine can't do. Gating on the item alone would have been wrong here.
- **Combat stops are proven, not assumed.** Both stops went through the Stage 5 evidence, which includes the use key (the shield is held through it). Here the stop window was the configured minimum of 20 ticks; the fight had already gone quiet.

## Not covered by this run

- PvP against a real player, and fights with the spear, mace or wind charges actually used. The engine chose melee against one zombie.
- Preempting a running fight by a SAFETY module, live. This is unit-tested in `PlatformConformanceTest` and `Stage5CombatTest`.
- Autotune (the `autotune` branch was not built for this run).
- Real servers: latency, anticheat.
