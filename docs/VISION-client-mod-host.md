# Vision: Conductor as the unified, version-agnostic Minecraft modding platform

**North star (project owner, 2026-10-12):** treat Conductor as a project that may become **the single, unified, version-agnostic platform for Minecraft mods**. A mod is written once against Conductor's contract and runs on every Minecraft version Conductor supports, next to every other Conductor mod.

**Earlier goal, same day, now a subset of the north star:** host *any* kind of client-side mod as a module. Examples: a utility/"hack" client module for an anarchy server like 2b2t, or a legit PvP client module.

## 0. What the north star means for every design decision

Every change to Conductor should be judged by one question: *does this still work if Conductor becomes the only platform mods target?* In practice:

1. **The contract is the product.** Modules depend only on `conductor.contract`. It must stay pure Java, versioned, and backwards compatible:
   - adding is fine;
   - removing or changing meaning needs a new major contract version and a migration note.

   Anything version-specific belongs in a runtime adapter, never in the contract.
2. **Ask about things, not versions.** Modules never branch on a Minecraft version number. They ask whether a thing exists (`content(ContentId.item("minecraft:iron_spear"))`) and declare features that switch off cleanly when it doesn't (`ModuleFeature`).

   This keeps working on versions released after the module was written. Spears are the worked example: [TUTORIAL.md](TUTORIAL.md) §7b.
3. **Capabilities, not hooks.** A module asks for "navigation" or "the HOTBAR lease", not "a mixin into `MultiPlayerGameMode`". Each capability comes from an adapter per version and is listed in discovery, so a module can see what this game offers before using it.
4. **Many mods, one game.** Shared things (movement, look, hotbar, clicks, screens, chat) are leased, with priorities and evidence-based hand-over ([MODULE-PLATFORM.md](MODULE-PLATFORM.md)). A platform that hosts every mod must stop them fighting over the same controls.
5. **Honest answers.** UNKNOWN is never reported as ABSENT, and "cancelling" is never reported as "cancelled". A platform everyone depends on cannot guess.
6. **No mod loader is a dependency.** Loaders (Fabric, Forge, NeoForge) and Mojang's mappings are *references*, used once per version or per loader inside a thin shim or adapter. Modules and the contract never depend on them.
7. **Existing mods are welcome as they are.** Native modules (version-specific, mixins allowed) join the same leases, topics and services. Moving to contract-only is gradual and never a precondition.

What it would still take to get there (none of it exists yet; each item is future work). Done since this list was first written: **Conductor runs without Ostinato** (its own Mixin hooks; Ostinato is an optional engine layer, see [live-run-no-ostinato.md](live-run-no-ostinato.md)).

| Gap | Why it matters for "the only platform" |
|---|---|
| More Minecraft versions per adapter | "Version-agnostic" is only as wide as the adapters that exist. Today: 1.21.11 and 1.21.4. |
| Loader independence | Modules and discovery are loader-free (plain jars, `META-INF/services`). Conductor's only Fabric code is a 40-line boot shim. Still to do: NeoForge/Forge shims, and a loader-free boot for engine-free modules. See [LOADER-INDEPENDENCE.md](LOADER-INDEPENDENCE.md). |
| Rendering, input, world-scan, packet, sound and GUI capabilities | Most mod types need at least one (see §2). |
| Data-driven content (enchantments, biomes, recipes) | These live in per-world registries, not built-in ones. `content()` answers UNKNOWN for them today. |
| Contract versioning checks at load time | A module built for contract 2.x must be refused cleanly by a 1.x host, not crash. |
| Server-side and common (both sides) modules | Conductor is client-only today. |
| Sandboxing or permissions | Today every module is trusted code in the same JVM. |

This document is a gap analysis, not a promise. It compares the goal with what Conductor is today, says what each kind of module would need, and points out which of the project's own core decisions would have to change. Every item here is **future work**.

## 1. Where Conductor is today

Conductor today is a **version-independent module platform that needs only Minecraft**, with Ostinato as an optional movement engine. A module can:
- read the player, inventory, entities and blocks; act (hotbar, look, attack, use, chat); draw overlays; get game events, chat commands and saved settings: all from Conductor's own hooks;
- with Ostinato installed: see what the bot engine is doing, send it somewhere or into a fight, stop it, and get proof that it has stopped;
- run next to other modules, sharing controls through leases with priorities and preemption;
- talk to other modules through topics and services;
- switch parts of itself on or off depending on what the game and engine actually contain (content detection).

It works on two Minecraft versions (1.21.11 and 1.21.4), with many modules at once. See [TUTORIAL.md](TUTORIAL.md) §10 for the full list of current limitations.

The hard part, built over Stages 1–4, is the **method**:
- stopping is proven with evidence rather than assumed;
- stale or other-context information is rejected;
- interference is classified;
- everything is tested live on each version.

The method carries over to new capabilities. The capabilities themselves do not exist yet.

## 2. What different modules would need

| Module type | Examples | Needs from Conductor | Exists today? |
|---|---|---|---|
| Automation | goto, follow, mine, build, farm | engine capabilities like navigation | **travel only**. Mining, building and farming are [deferred candidates](deferred-candidates.md). |
| HUD / overlay (legit PvP) | keystrokes, CPS, armour/potion HUD, coordinates, FPS, crosshair | **rendering hooks**, read-only player state, settings storage | no |
| Input helpers (legit PvP) | toggle sprint, zoom, keybinds | **input/keybind capability**: owning a key without fighting the engine | no |
| Combat assist | auto-totem, aim assist, crystal/anchor, kill aura | **combat capability** on top of Ostinato's PvP/PvE code, including the `autotune` branch | no: deferred, and the hardest case (see §3) |
| Utility / anarchy | ESP, tracers, freecam, waypoints, coordinate tools, base finding | rendering, **world scanning**, camera control | no |
| Movement exploits | fly, speed, elytra-fly, no-fall, scaffold | **packet and physics access**, movement ownership shared with the engine | no; also needs privileged per-version hooks |
| Chat / social | auto-reply, chat filters, spam control | **chat capability** (read/send) | no |
| Management | module list, toggles, config screens | **GUI** | no; deferred by the specification |

## 3. What would have to change (decisions for the project owner)

These points conflicted with decisions recorded in the original specification. Points 1 and 2 have since been decided by the project owner (full design rights, 2026-10-12), and are implemented as described in [MODULE-PLATFORM.md](MODULE-PLATFORM.md) D1 and D3. The rest are still open.

1. **More than one module at a time.** Stage 3 made Conductor single-consumer. Hosting many modules needs:
   - per-module identity;
   - per-resource ownership: movement, keys, camera, and so on.

   *Without* reopening anything, the allowed form is **first holder wins, others are refused**: contract-level exclusivity, no preemption. That is enough for HUD, ESP and chat modules, which don't fight over controls.
2. **Modules that interrupt each other.** For example, auto-totem taking over in the middle of a walk. That is **automatic preemption**, which is a hard scope fence today ("no automatic preemption, no universal resource scheduler"). A PvP or anarchy client probably needs some form of it, so reopening this fence is your call. Recommendation: if you do, make it explicit, per-resource and evidence-driven, the same way release works now.
3. **Rendering, packets, camera and input hooks.** These need privileged, version-specific hooks (mixins) inside each runtime adapter, which is allowed: "privileged access stays behind the runtime integration boundary". The catch is per-version cost. Every hook must be written and live-tested on every supported version, and the stop condition "the common surface across versions is too small to justify contract work" applies to each one.
4. **Fabric API or not.** Conductor avoids it: its hooks are its own Mixins, which keeps it loader-neutral. HUD rendering and keybinds would be simpler with Fabric API, at the cost of a Fabric dependency.
5. **GUI, hot reload/unload, marketplaces.** All explicitly deferred by the specification.
6. **Not just Ostinato. (Done.)** HUD, chat and ESP modules don't need a bot engine at all. Conductor's runtime now needs only Minecraft; Ostinato is one optional capability provider (`EngineLayer`), found at start-up when installed.

## 4. A staged path that keeps the method

Each step follows the same discipline as Stages 1–4: audit, read-only observation, then delegation, then live evidence, with stop conditions. Each step ends with a live run on every supported version.

| Step | Adds | Why this order |
|---|---|---|
| A | Multi-module hosting with first-holder-wins ownership; a shared Conductor mod jar so modules stop bundling it | Unblocks everything else, and needs no fence reopened |
| B | Read-only player/world observations: position, health, inventory summary, nearby entities | Low risk; useful to every module type |
| C | Rendering/HUD capability (per-version hooks) | Enables most legit-PvP modules |
| D | Chat and keybind capabilities | Small, self-contained |
| E | Deferred engine capabilities (mining, building), using the deferred-candidates gate | Same method as navigation |
| F | Combat capability, plus a decision on preemption (§3.2) | Shares controls with movement, so it is the hardest; needs your decision |
| G | Packet/physics capabilities for movement modules | Most version-specific and the riskiest; last |

## 5. Things to keep in mind

- **Server rules and anticheat.** Every timing in the docs was measured on a local server. Real servers add latency, and many run anticheat. A module's behaviour can differ there, and Conductor's evidence windows may need retuning. Anarchy servers such as 2b2t allow modified clients; most other servers do not.
- **Honesty over features.** Conductor's value is that it tells the truth: "cancelling", not "cancelled"; "unknown", not "no". New capabilities should keep that, even when a looser API would be easier to use.
