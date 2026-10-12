# Conductor
Version agnostic modular system for bot capabilities in Minecraft

Conductor is the modularity and capability-contract layer for Ostinato. It evolves Ostinato in small stages rather than rewriting it.

## Status

| Stage | State | Document |
|---|---|---|
| 1. Audit | Complete (documentation only) | [docs/stage1-audit.md](docs/stage1-audit.md) |
| 2. Discovery + read-only observations | Complete; verified live | [docs/stage2-observations.md](docs/stage2-observations.md) |
| 3. Single-consumer navigation delegation | Complete; 54/54 tests pass; verified live | [docs/stage3-navigation.md](docs/stage3-navigation.md) |
| 4. Ownership, resource safety, interference classification | Complete; 70/70 tests; verified live on 1.21.11 | [docs/stage4-release.md](docs/stage4-release.md) |

**New here? Start with the [tutorial](docs/TUTORIAL.md)**: what Conductor does, every capability, and how to build a module (worked example in [`examples/walk-and-stop`](examples/walk-and-stop)).

**Limitations (today):**
- Travel is the only action, and only one module can run per game.
- No rendering/HUD, chat, keybinds, packets or GUI.
- Ostinato and Fabric client only, on Minecraft 1.21.11 and 1.21.4.
- Stops take as long as Ostinato's engine takes (up to about 17 s observed).

Full list: [tutorial §10](docs/TUTORIAL.md#10-limits-you-should-know-about).

**Long-term goal:** host any client-side mod (utility/anarchy and legit PvP modules alike). The gap analysis and a staged path are in [docs/VISION-client-mod-host.md](docs/VISION-client-mod-host.md).

Deferred capability candidates (combat and others): [docs/deferred-candidates.md](docs/deferred-candidates.md)

## Layout

- `contract/`: the normative contract, the Java binding (`Conductor`) and the module interface (`ConductorModule`). Pure Java 21, no Minecraft dependency.
- `runtime-ostinato/`: the runtime adapter for Ostinato. Shared code is in `common/`; `1.21.11/` (Ostinato `main`) and `1.21.4/` (Ostinato `1.21.4`) differ only in `MinecraftGameAccess`. Includes `ConductorBootstrap`, which starts a module.
- `examples/walk-and-stop/`: example module, built for both versions.
- `live-harness/`: test-only mod for live runs, built for both versions.

## Live harness

`live-harness/` is a test-only Fabric mod. `scripts/live-run.sh <workdir>` starts a vanilla 1.21.11 server, then runs Ostinato's dev client under Xvfb with the harness, which records every observation plus player position and velocity per tick. `scripts/analyze-live.py <workdir>/harness.jsonl` summarises the cancel trials. See the script header for prerequisites.

## Build

```sh
# contract only (no Minecraft needed)
./gradlew :contract:test

# runtime-ostinato compiles against a local Ostinato checkout's classes (default ../Ostinato)
(cd ../Ostinato && ./gradlew compileJava compileApiJava)
./gradlew :runtime-ostinato:test            # or -PostinatoDir=/path/to/Ostinato
```
