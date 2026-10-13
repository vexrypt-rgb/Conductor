# Live run: Ostinato as a Conductor module, and the 1.21.11 release, in production (2026-10-13)

**What was released:**
- **Ostinato 1.21.0** for Minecraft 1.21.11, from Ostinato branch `ccr-b0151e6a-wtb5fn`: `main` + `autotune` + `bastion-refine`, plus the command execution guard.
- **Conductor 0.5.0**, with the new **Ostinato module**.

Release folder: `releases/ostinato-1.21.0-conductor-0.5.0-mc1.21.11/` (with its README and SHA256SUMS).

**How it was tested:**
- `scripts/prod-run.sh`: a real Fabric Loader 0.19.5 production launch (KnotClient; the game is remapped to intermediary at start-up), with the release jars in `mods/`, joining a local 1.21.11 server.
- The dev client (`scripts/live-run.sh`), from Ostinato's source.

| Run | Jars | Result |
|---|---|---|
| Production 1 | Ostinato unoptimized-fabric 1.21.0 only | loads ("Loading 6 mods: baritone 1.21.0 …"), joins, no errors; screenshot `production-ostinato-alone-1.21.11.png` |
| Production 2 | Ostinato + Conductor 0.4.0 + Ostinato module + `example-engine-commands` | full tour passed (below) |
| Production 3 | Ostinato + Conductor 0.4.0 + `example-block-highlighter` | overlays, tracers, `+` commands, settings saved, events; screenshot `production-block-highlighter-1.21.11.png` |
| **Final, production** | **the exact release files**: Ostinato 1.21.0, Conductor 0.5.0, Ostinato module 0.5.0, `example-engine-commands` 0.5.0 | full tour passed (below); screenshot `production-ostinato-module-1.21.11.png` |
| Dev client | same scenario from source | passed |

**The tour** (`example-engine-commands`, SAFETY priority, `selftest=true`):
1. "The player" types `#goto ~40 ~ ~`. Ostinato's guard asks the Ostinato module, which takes MOVEMENT (HIGH). The command runs, and the bot is busy.
2. The SAFETY module asks for MOVEMENT and gets **PENDING** (preempting). The Ostinato module stops Ostinato ("#goto ~40 ~ ~ stopped: example-engine-commands took over"). MOVEMENT is granted **26 ticks later, once the stop is proven**.
3. `EngineCommands.run(goto ~-20 ~ ~)` is accepted, and the bot is busy for the module.
4. "The player" types `#goto ~5 ~ ~`. **Refused** in chat: `[Baritone] Not run: movement is held by example-engine-commands-0.5.0 at SAFETY; …`. MOVEMENT stays with the module.
5. `EngineCommands.stop` → stop proven 7 ticks later → MOVEMENT released.
6. `run(mine diamond_ore)` without MOVEMENT → `LEASE_REQUIRED`; `run(version)` (not a task) → accepted.

**Ostinato checks:** the merge kept all 549 tests passing, and the guard adds 4 more (`CommandGuardTest`).

**Evidence:** `docs/evidence/module-runs/production-*.log.gz` and `ostinato-module-dev-1.21.11.log.gz`.

## Module edition: the module moved into Ostinato (branch `conductor-module`)

At the owner's request, Ostinato has a separate branch for its module:
- **`conductor-module`**: Ostinato plus `baritone.conductor.OstinatoModule`, one jar. Conductor's contract is a compile-only jar in `libs/`.
- **`ccr-b0151e6a-wtb5fn`**: the plain Fabric line, without any Conductor code.

Conductor's own copy (`modules/ostinato`) was removed so there is one source.

| Production run (module edition 1.21.0-module + Conductor 0.5.0 only) | Result |
|---|---|
| **unoptimized** jar + `example-engine-commands` | full tour passed: lease for `#goto`, SAFETY takeover after a proven stop (26 ticks), the player's `#goto` refused, stop proven, `LEASE_REQUIRED` without the lease |
| **standalone** (obfuscated) jar | **first try: Conductor's start-up threw `NoClassDefFoundError: baritone/api/event/listener/IGameEventListener` and the client crashed at start-up.** The obfuscated builds rename the classes Conductor uses. Fixed: Conductor now checks for them first, logs "Conductor is NOT running … needs Ostinato's unoptimized build" and stays off, and it never takes the game down. Re-run: the game started and joined, with the message logged. |

So the module edition ships as the unoptimized jar only, and the release README says Conductor needs the unoptimized build. Ostinato's 553 tests pass on the branch.
