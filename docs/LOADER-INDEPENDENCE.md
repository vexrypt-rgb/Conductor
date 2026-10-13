# Not relying on Fabric (or Forge, or NeoForge)

Project owner (2026-10-13): Conductor should not have to rely on Fabric. Loaders may be used as a *reference* for a Minecraft version, once, but Conductor and its modules are written once and must not depend on them.

## 1. Where Conductor stands (Verified by source)

| Part | Depends on a mod loader? |
|---|---|
| `contract/` (everything modules see) | **No.** Plain Java 21. |
| Modules (`examples/*`) | **No.** Since this change they are plain jars, declared with the JDK's own `META-INF/services/conductor.contract.ConductorModule`. No `fabric.mod.json`, no `mods.toml`. They go in `<game dir>/conductor/modules/`. |
| Module discovery (`ModuleDiscovery`) | **No.** `java.util.ServiceLoader` plus one class loader per jar. |
| Runtime (`runtime/`), bootstrap and tick | **No loader API, and no other mod.** It uses Minecraft classes only; no engine is even on its compile classpath. Per-version names come from Mojang's official mappings (with Parchment), not from a loader's mappings. |
| Game hooks (`conductor-mod/.../mixin`: client tick, outgoing chat, block/chunk/chat/death packets, world rendering) | **No loader API.** They are SpongePowered Mixins, the bytecode tool that Fabric, Quilt, Forge and NeoForge all ship. Each hook only calls a static method in `conductor.runtime.Hooks`. |
| `conductor-mod/` boot shim (`ConductorHostMod`, 40 lines) | **Yes, by design:** it is the Fabric shim. It answers "where is the game directory?" and "when may client code start?", then hands over to the loader-free code. |
| Optional engine layer (`engine-ostinato/`) | **No loader API.** It uses Ostinato's classes and is loaded only when Ostinato is installed (a `ServiceLoader` provider that checks for Ostinato's classes first). |
| Ostinato itself (optional) | **Yes.** Ostinato is built as a Fabric mod (`available_loaders=fabric`). Conductor never changes Ostinato and no longer needs it. |

So *Conductor* depends on Fabric in exactly one 40-line class, *modules* don't depend on it at all, and **Conductor needs no other mod**. Ostinato is an add-on that brings travel and combat; without it, everything else works ([live-run-no-ostinato.md](live-run-no-ostinato.md)).

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

1. **Done: Conductor runs without an engine.** The runtime needs only Minecraft; Ostinato (a Fabric mod) is optional. A loader-free Conductor no longer has to bring Fabric along for the engine's sake.
2. **Hooks into the game need bytecode changes.** Conductor now has its own small hook layer (8 Mixin injections, shared by both versions except the render hook). Without a loader, something must still apply them: a Java agent that runs Mixin itself, or hand-written ASM transforms. That is the remaining work.

**Reported (not checked here):** Mojang announced that Java Edition stops obfuscating its code from the 26.x line on. If so, "reference once per version" gets much cheaper: names are readable in the game jar itself, with no mapping step.

## 4. Decision taken now, and what is next

**Now (done):**
- Modules no longer use loader metadata.
- Discovery is loader-free.
- The Fabric dependency is confined to one boot shim.
- Conductor needs no other mod: its own Mixin hooks replace Ostinato's event bus, and Ostinato is an optional engine layer. Verified live in production Fabric launches on both versions with no Ostinato installed.

**Next, in order:**
1. **NeoForge and Forge shims.** Now useful on their own: Conductor no longer needs Ostinato, so it can run where no Ostinato build exists. Each shim is the two calls above plus registering `conductor.mixins.json`.
2. **A Java-agent boot.** Applies Conductor's Mixins without a loader. This is what makes Conductor runnable with no loader at all.
3. **Loader-neutral packaging for the runtime.** Ship `conductor-<mc>.jar` (contract, adapter, discovery) separately from the tiny per-loader shims.
