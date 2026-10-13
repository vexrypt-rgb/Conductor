# Live run: player, world and action capabilities on 1.21.11 and 1.21.4 (2026-10-13)

**Module:** [`examples/player-actions`](../examples/player-actions), a plain jar loaded from a modules folder. SHA-256 `663f79ac135bcc11092df5a1a86241bda3d7eb3de264f7fdb4028f6e6dae9d8b`, **the same file on both versions**.

**Setup:** `scripts/live-run.sh`, connect-only, with this `SERVER_ON_JOIN`:
- `difficulty easy`;
- `give` an iron sword and a shield;
- `effect give Dev speed`;
- `summon` a zombie with `NoAI` two blocks away.

**Evidence:** `docs/evidence/module-runs/player-actions-<version>.log.gz`.

| Check | 1.21.11 | 1.21.4 |
|---|---|---|
| `PLAYER_STATUS` | health 20/20, food 20, air 300, onGround, survival | same |
| `PLAYER_MOTION` | position and velocity (falling −0.0784/tick, the game's resting gravity) | same |
| `ACTIVE_EFFECTS` | `minecraft:speed`, amplifier 1, about 5980 ticks | same |
| `INVENTORY_SLOTS` | slot 0 iron sword (0/250), slot 1 shield (0/336) | same |
| `NEARBY_ENTITIES` | passive mobs at 22-30 blocks, then the zombie at 2.0 | same |
| `blockAt` below the player | `minecraft:grass_block` | same |
| `blockAt` 10,000 blocks away | empty: **not loaded** (not "air") | same |
| `findBlocks(bedrock, r=8, 3)` | three bedrock positions, nearest first | same |
| Leases HOTBAR, LOOK, INTERACT, CHAT | GRANTED | same |
| `selectHotbar(3)` | accepted; next tick `SELECTED_SLOT` = 3, **confirmed** | same |
| `sendChat` | accepted; server log: `<Dev> Conductor player-actions module: hello` | same |
| Second `sendChat` in the same tick | `RATE_LIMITED` | same |
| `look` toward the zombie | accepted; next tick yaw −90.0, **confirmed** | same |
| `attack(zombie)` | accepted; health 20.0 → 19.06 (empty hand), **hit confirmed** | same |
| `attack(-1)` | `TARGET_UNAVAILABLE` | same |
| `look` after releasing LOOK | `LEASE_REQUIRED` | same |

**Test-setup note:** on 1.21.11, `gamerule doMobSpawning` is rejected ("Incorrect argument"): game rules were renamed (the `spawn_mobs` form was accepted). This explains the natural mob spawns in earlier 1.21.11 runs.
