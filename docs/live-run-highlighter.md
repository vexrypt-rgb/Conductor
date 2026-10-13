# Live run: overlays, events, chat commands, messages and settings on 1.21.11 and 1.21.4 (2026-10-13)

**Module:** [`examples/block-highlighter`](../examples/block-highlighter), a plain jar from a modules folder, with `selftest=true` preset in its settings file. In selftest mode it types its own `+` commands through real chat, so the full path is exercised: chat → Ostinato's chat event → Conductor → module.

**Server script:**
1. Set the time; place three diamond ores near the player (one underground).
2. Send `tellraw @a "hello from tellraw"`.
3. Ten seconds later, replace one ore with stone.

**Evidence:**
- `docs/evidence/module-runs/block-highlighter-<version>.log.gz` (module log, the settings file afterwards, server log);
- screenshots `docs/evidence/screenshots/block-highlighter-<version>.png`.

| Check | 1.21.11 | 1.21.4 |
|---|---|---|
| Settings loaded from file (`selftest=true`) | yes | yes |
| `Topics.CHAT` | `hello from tellraw` received, plus the server's command feedback | same |
| `Topics.BLOCK_CHANGES` for the three placed ores | yes | yes |
| `findBlocks` and overlay | 3 ores boxed (through walls) with tracers; **screenshot** | same; **screenshot** |
| Typed `+highlight` (module command) | handled; reply shown in local chat (screenshot: `[example-block-highlighter-0.4.0] 3 x minecraft:diamond_ore within 8 blocks`) | same |
| Typed `+set … radius 12` | changed, module notified through `Topics.SETTINGS`, **file updated** (`radius=12`) | same |
| Typed `+set … radius 99` | refused ("not changed: … out of range"); value stays 12 | same |
| Typed `+settings`, `+modules` | answered locally | same |
| Ore replaced by stone (a single-block update) | `BLOCK_CHANGES` reports `minecraft:stone`; rescan finds 2 | same |
| Errors from Conductor or the module | none | none |

**Bug found and fixed in this run.** In the first run (`block-highlighter-before-single-block-fix-1.21.11.log.gz`), the stone replacement and one ore placement never arrived as block changes.

Cause (Verified by source): Ostinato fires `BlockChangeEvent` only for multi-block **section** updates. Its hook for single-block updates is commented out in `MixinClientPlayNetHandler`.

Fix: Conductor now reads the single-block update packet (`ClientboundBlockUpdatePacket`) itself, copying only the position and the block id off the network thread. The re-run above shows the stone change on both versions.
