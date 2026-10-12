# Vision: Conductor as a host for any client-side mod

**Goal (project owner, 2026-10-12):** Conductor should eventually host *any* kind of client-side mod as a module, for example a utility/"hack" client module for an anarchy server like 2b2t, or a legit PvP client module, written once and run on several Minecraft versions.

This document is a gap analysis, not a promise. It compares the goal with what Conductor is today, says what each kind of module would need, and points out which of the project's own core decisions would have to change. Every item here is **future work**.

## 1. Where Conductor is today

Conductor today is a **safe, version-independent wrapper around one Ostinato capability: travel**. A module can:
- see what the bot engine is doing;
- send it somewhere;
- stop it, and get proof that it has stopped.

It works on two Minecraft versions, with one module at a time. See [TUTORIAL.md](TUTORIAL.md) §10 for the full list of current limitations.

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

These points conflict with decisions recorded in the original specification. They are listed so you can decide deliberately, not so they are quietly ignored.

1. **More than one module at a time.** Stage 3 made Conductor single-consumer. Hosting many modules needs:
   - per-module identity;
   - per-resource ownership: movement, keys, camera, and so on.

   *Without* reopening anything, the allowed form is **first holder wins, others are refused**: contract-level exclusivity, no preemption. That is enough for HUD, ESP and chat modules, which don't fight over controls.
2. **Modules that interrupt each other.** For example, auto-totem taking over in the middle of a walk. That is **automatic preemption**, which is a hard scope fence today ("no automatic preemption, no universal resource scheduler"). A PvP or anarchy client probably needs some form of it, so reopening this fence is your call. Recommendation: if you do, make it explicit, per-resource and evidence-driven, the same way release works now.
3. **Rendering, packets, camera and input hooks.** These need privileged, version-specific hooks (mixins) inside each runtime adapter, which is allowed: "privileged access stays behind the runtime integration boundary". The catch is per-version cost. Every hook must be written and live-tested on every supported version, and the stop condition "the common surface across versions is too small to justify contract work" applies to each one.
4. **Fabric API or not.** Conductor currently avoids it, because Ostinato doesn't need it. HUD rendering and keybinds are much simpler with it. That is a dependency decision.
5. **GUI, hot reload/unload, marketplaces.** All explicitly deferred by the specification.
6. **Not just Ostinato.** HUD, chat and ESP modules don't need a bot engine at all. A general host would need a runtime that works without Ostinato (`runtime-vanilla`, say), with Ostinato as one optional capability provider.

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
