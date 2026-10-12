# Conductor
Version agnostic modular system for bot capabilities in Minecraft

Conductor is the modularity and capability-contract layer for Ostinato. It evolves Ostinato in small stages rather than rewriting it.

## Status

| Stage | State | Document |
|---|---|---|
| 1. Audit | Complete (documentation only) | [docs/stage1-audit.md](docs/stage1-audit.md) |
| 2. Discovery + read-only observations | Implemented, unit-tested, **not run in a live game** | [docs/stage2-observations.md](docs/stage2-observations.md) |
| 3. Single-consumer navigation delegation | Not started | — |
| 4. Ownership, resource safety, interference classification | Not started | — |

Deferred capability candidates (combat and others): [docs/deferred-candidates.md](docs/deferred-candidates.md)

## Layout

- `contract/`: the normative contract and Java binding (`conductor.contract.Conductor`). Pure Java 21, no Minecraft dependency.
- `runtime-ostinato/`: runtime integration and version adapter for Ostinato `main` (Minecraft 1.21.11). Read-only in Stage 2.

## Build

```sh
# contract only (no Minecraft needed)
./gradlew :contract:test

# runtime-ostinato compiles against a local Ostinato checkout's classes (default ../Ostinato)
(cd ../Ostinato && ./gradlew compileJava compileApiJava)
./gradlew :runtime-ostinato:test            # or -PostinatoDir=/path/to/Ostinato
```
