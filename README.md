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

Deferred capability candidates (combat and others): [docs/deferred-candidates.md](docs/deferred-candidates.md)

## Layout

- `contract/`: the normative contract and Java binding (`conductor.contract.Conductor`). Pure Java 21, no Minecraft dependency.
- `runtime-ostinato/`: runtime integration and version adapter for Ostinato `main` (Minecraft 1.21.11). Observations are read-only; navigation delegates to one held `IMovementEngine`.

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
