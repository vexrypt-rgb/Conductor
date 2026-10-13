# Not relying on Fabric (or Forge, or NeoForge)

Project owner (2026-10-13): Conductor should not have to rely on Fabric. Loaders may be used as a *reference* for a Minecraft version, once, but Conductor and its modules are written once and must not depend on them.

## 1. Where Conductor stands (Verified by source)

| Part | Depends on a mod loader? |
|---|---|
| `contract/` (everything modules see) | **No.** Plain Java 21. |
| Modules (`examples/*`) | **No.** Since this change they are plain jars, declared with the JDK's own `META-INF/services/conductor.contract.ConductorModule`. No `fabric.mod.json`, no `mods.toml`. They go in `<game dir>/conductor/modules/`. |
| Module discovery (`ModuleDiscovery`) | **No.** `java.util.ServiceLoader` plus one class loader per jar. |
| Runtime adapter (`runtime-ostinato/`), bootstrap and tick | **No loader API.** It uses Minecraft and Ostinato classes. Ticks come from Ostinato's own event bus. Per-version names come from Mojang's official mappings (with Parchment), not from a loader's mappings. |
| `conductor-mod/` boot shim (`ConductorHostMod`, 40 lines) | **Yes, by design:** it is the Fabric shim. It answers "where is the game directory?" and "when may client code start?", then hands over to the loader-free code. |
| Ostinato itself | **Yes.** Ostinato is built as a Fabric mod (`available_loaders=fabric`) and uses Mixin. Conductor never changes Ostinato. |

So *Conductor* depends on Fabric in exactly one 40-line class, and *modules* don't depend on it at all. What still ties a running game to Fabric is the bot engine: Conductor runs where Ostinato runs.

## 2. The model: references, not dependencies

What you described ("reference them once per version, never rely on them") is the model, made explicit:

| Layer | Written | Uses |
|---|---|---|
| Modules | **once, ever** | the contract only |
| Contract | **once, ever** (evolves compatibly) | nothing |
| Runtime adapter, per Minecraft version | **once per version** | Mojang's mappings as the *reference* for names (`MinecraftGameAccess` is the only per-version file today) |
| Boot shim, per loader | **once per loader**, about 40 lines, rarely touched | the loader's "game dir" and "client start" hooks only |

Adding NeoForge or Forge means one more shim with those two calls. Nothing above the shim changes, and no module is rebuilt.

## 3. Going further: no loader at all

A Java agent (`-javaagent:conductor.jar`) could boot Conductor without any loader. It would find the game directory and start code from `Minecraft`'s own startup, using Mojang's mappings as the per-version reference. That is the strongest form of "not relying on Fabric". It runs into two hard facts:

1. **Ostinato is a Fabric/Mixin mod.** While Conductor's engine is Ostinato, the game has Fabric anyway. A loader-free Conductor would need either an engine that loads without a loader, or a `runtime-vanilla` without an engine (see VISION §0). That runtime can't move the bot, but can host HUD, chat and observation modules.
2. **Hooks into the game need bytecode changes.** Ticks, rendering and input hooks would need a small instrumentation layer of Conductor's own, written per version against Mojang's mappings. That is a lot of code. Loaders exist mainly to do this job well.

**Reported (not checked here):** Mojang announced that Java Edition stops obfuscating its code from the 26.x line on. If so, "reference once per version" gets much cheaper: names are readable in the game jar itself, with no mapping step.

## 4. Decision taken now, and what is next

**Now (done, unit-tested):**
- Modules no longer use loader metadata.
- Discovery is loader-free.
- The Fabric dependency is confined to one boot shim.

**Next, in order:**
1. **NeoForge shim.** Only useful once there is a NeoForge build of the engine. Ostinato's build has NeoForge plumbing from Baritone but ships Fabric only, so this is an Ostinato decision.
2. **`runtime-vanilla` plus a Java-agent boot.** For engine-free modules, written against Mojang's mappings. This is what makes Conductor runnable with no loader at all.
3. **Loader-neutral packaging for the runtime.** Ship `conductor-<mc>.jar` (contract, adapter, discovery) separately from the tiny per-loader shims.
