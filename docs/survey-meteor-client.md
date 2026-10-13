# Survey: what Meteor Client needs, and what Conductor offers

**Purpose (project owner, 2026-10-13):** download the latest Meteor Client, see which features it uses, and add more to Conductor.

**Inspection basis:** `MeteorDevelopment/meteor-client` `master` @ `2385f7b4` (2026-10-09), which targets **Minecraft 26.2**. Meteor is GPL-3.0. This is a survey of *what it does*, and no Meteor code was copied into Conductor. The source was read, not built or run.

**Method:** `scripts/survey/classify-meteor-modules.py` sorts each of Meteor's 171 modules by the game access its source uses: events, utilities, and calls into Minecraft. This is **a heuristic** (regular expressions over source), good for ranking, not exact per module. Results: `docs/evidence/meteor-module-needs.json`.

## 1. Meteor at a glance (Verified by source)

| | Count |
|---|---|
| Modules | 171: combat 30, movement 35, player 31, render 47, world 24, misc 19 |
| HUD elements | 15 (armour, inventory, potion timers, radar, compass, text, ...) |
| Mixins into Minecraft | 209 |
| Event types | 66 |

Handlers by event type, across modules and HUD:

| Event | Handlers |
|---|---|
| TickEvent.Pre | 70 |
| TickEvent.Post | 48 |
| Render3DEvent | 38 |
| PacketEvent (send, receive, sent) | 40 |
| MouseClickEvent | 12 |
| KeyInputEvent | 12 |
| Render2DEvent | 10 |
| OpenScreenEvent | 10 |
| PlayerMoveEvent | 7 |
| GameLeft / GameJoined | 11 |

Most used utilities:

| Utility | Calls |
|---|---|
| `Rotations.rotate` | 43 |
| `InvUtils.findInHotbar` | 40 |
| `PathManagers` (Baritone) | 37 |
| `InvUtils.swap` | 35 |
| `BlockUtils.place` | 28 |
| `InvUtils.move` | 25 |
| `Friends` | 25 |

**Design note:** Meteor's `Rotations.rotate(yaw, pitch, priority, ...)` is a priority queue for who may turn the player. Conductor's LOOK lease with priorities is the same idea, generalised to every shared control.

## 2. What Meteor modules need (heuristic, number of modules)

| Need | Modules | Conductor before this survey | Conductor now |
|---|---|---|---|
| Block / world reads (block states, scanning) | 55 | none | **`blockAt`, `findBlocks`** (loaded chunks only; "not loaded" is never reported as air) |
| Inventory reads | 51 | item counts only | **`INVENTORY_SLOTS`** (slot, item, count, durability), **`SELECTED_SLOT`** |
| Player state (health, food, effects, position) | 49 | block position only | **`PLAYER_STATUS`**, **`PLAYER_MOTION`**, **`ACTIVE_EFFECTS`** |
| Packets (read, send, cancel) | 45 | none | none. Ostinato's event bus delivers packets loader-free; a contract would expose *semantic* events, not raw packets (§3). |
| 3D world rendering (ESP, tracers, boxes) | 39 | none | none. Ostinato's render pass and line renderer exist on both versions (§3). |
| Input (keys, mouse, keybinds) | 37 | none | none (needs a per-version hook) |
| Inventory actions (select, swap, move) | 34 | none | **`selectHotbar`** (HOTBAR lease). Slot moves are next. |
| Look / rotation | 33 | LOOK lease, no action | **`look(yaw, pitch)`** (LOOK lease) |
| Nearby entities | 32 | combat target only | **`NEARBY_ENTITIES`** (32 blocks, nearest first) |
| Block actions (place, break, interact) | 27 | none | none (next; needs a block-action audit like Stage 1) |
| Motion writes (velocity, physics) | 20 | none | none. Deliberately not planned as contract; a native-module concern (§4). |
| Screens / containers | 17 | SCREEN lease only | none |
| Use item | 16 | none | **`useItem(offhand)`** (INTERACT lease) |
| Camera (freecam, zoom) | 14 | none | none |
| Pathing (Baritone) | 11 | travel | travel, combat (Stage 5) |
| Connection (reconnect, leave) | 10 | context tracking | context tracking |
| 2D HUD | 9 (+15 HUD elements) | none | none (needs a per-version hook) |
| Entity actions (attack, interact) | 8 | combat (engine) | **`attack(entityId)`** (INTERACT lease) |
| Chat send / receive | 7 | CHAT lease only | **`sendChat`** (CHAT lease, one per tick, rejects characters servers kick for) |

Coverage (greedy, heuristic): the reads plus the five actions above cover the most common needs. Most Meteor modules also need **rendering or packets**, which is why those are the next items, not "later".

## 3. What Ostinato already offers loader-free (Verified by source, both branches)

Ostinato's `IGameEventListener` delivers these on the game thread. Conductor can build on them **without any mod-loader code**:

| Ostinato event | Same API on 1.21.4 and 1.21.11? | Conductor use |
|---|---|---|
| `onRenderPass(RenderEvent)`: PoseStack and projection, every frame | event yes; the line renderer (`IRenderer`) differs slightly | **3D overlays**: boxes, lines and tracers declared by modules, drawn by a small per-version renderer |
| `onReceivePacket` / `onSendPacket` (`PacketEvent`) | yes (packet classes are per version) | semantic events: chat received, entity spawn and death, teleport |
| `onBlockChange(BlockChangeEvent)` | yes | `Topics.BLOCK_CHANGES` (block ids, version-agnostic) |
| `onChunkEvent(ChunkEvent)` | yes | chunk loaded/unloaded topic (stash finders, ESP caches) |
| `onSendChatMessage(ChatEvent)`, cancellable | yes | **chat commands** for modules (prefix, as Baritone's `#` works) |
| `onPlayerDeath()` | yes | death topic (auto-respawn, logging) |

## 4. Deliberate limits

- **No raw packets in the contract.** Packet classes change every version, so a module that handles raw packets is version-specific by nature. The contract offers *meanings* ("chat received"), and native modules may still use raw packets.
- **No physics or anticheat-bypass writes in the contract.** Motion writes (fly, speed, no-fall, velocity) and packet cancelling depend on version and server. They stay native-module territory, behind the MOVEMENT lease.
- **Server rules.** Many Meteor modules are for anarchy servers. Conductor stays neutral: it provides capabilities, and modules decide how to use them. Every action is still lease-checked, validated (for example chat characters that get players kicked) and recorded in the diagnostic sink.

## 5. Order of work (each step: contract, then adapter, then unit tests, then live on both versions)

1. **Done:** player, world and action reads and actions (§2, bold). Verified live on both versions ([live-run-player-actions.md](live-run-player-actions.md)).
2. **Events and commands:** chat commands with a prefix, local messages to the player, and topics for block changes, chunks, death and received chat.
3. **3D overlays:** boxes, lines and tracers from modules, drawn in Ostinato's render pass. A per-version renderer, about 50 lines each.
4. **Settings:** typed module settings, saved to `conductor/config/`, editable by chat command or by a GUI module through a service.
5. Inventory moves (swap, move, drop), block place/break (audit first), 2D HUD, keybinds.
