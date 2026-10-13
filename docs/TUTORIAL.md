# Conductor tutorial

This guide explains what Conductor is, everything it can do today, and how to build things with it. It starts simple and goes step by step. No prior knowledge of Conductor is needed; basic Java and Minecraft modding help.

**Contents**
1. [What Conductor is, in one minute](#1-what-conductor-is-in-one-minute)
2. [Four ideas that explain every rule](#2-four-ideas-that-explain-every-rule)
3. [Set up and build](#3-set-up-and-build)
4. [Your first module, step by step](#4-your-first-module-step-by-step)
5. [Everything Conductor can do (capability reference)](#5-everything-conductor-can-do)
6. [When Conductor says no: every rejection, explained](#6-when-conductor-says-no)
7. [Testing your module](#7-testing-your-module)
8. [Supporting another Minecraft version](#8-supporting-another-minecraft-version)
9. [Adding a new capability (combat, mining, ...)](#9-adding-a-new-capability)
10. [Limits you should know about](#10-limits-you-should-know-about)
11. [Many modules side by side: leases, topics, services, management](#11-many-modules-side-by-side)
12. [One jar, every version: optional features (the spear example)](#12-one-jar-every-version-optional-features)
13. [Adding content: items, blocks, dimensions, recipes](#13-adding-content-items-blocks-dimensions-recipes)
14. [Server modules: code that runs on the server](#14-server-modules-code-that-runs-on-the-server)

---

## 1. What Conductor is, in one minute

**Conductor** gives your code a **small, safe, version-independent way to use the game**: the player, the world, actions, events, overlays, settings and commands. It needs nothing but Minecraft and the Conductor mod.

**Ostinato** is an optional Minecraft bot engine (based on Baritone): it finds paths, walks, mines, builds and fights. When it is installed, Conductor also offers travel and combat on top of it.

```
 your module (one jar, any version)  ──►  Conductor contract  ──►  Conductor mod for this Minecraft version  ──►  Minecraft
                                                                                     └──(optional)──►  Ostinato
```

- **Your module** only talks to the Conductor contract. It never imports Minecraft, mod-loader (Fabric, Forge, NeoForge) or Baritone classes. It isn't even a Fabric mod: it's a plain jar ([LOADER-INDEPENDENCE.md](LOADER-INDEPENDENCE.md)).
- **The contract** (`contract/`) is plain Java with no Minecraft code. It holds the rules.
- **The Conductor mod** (`conductor-mod/`, built on `runtime/` and the optional `engine-ostinato/` layer) is the only part that knows Minecraft (and Ostinato, when it's there). There is one per Minecraft version (today **1.21.11** and **1.21.4**), built from the same shared code. It hooks the game with its own Mixins, finds your module and runs it.

So **your module is one jar that works on every supported Minecraft version**. You install the Conductor mod that matches your game, plus your module. The same example jar was run live on both 1.21.11 and 1.21.4.

What Conductor gives you today:

| You can... | With |
|---|---|
| Ask what is available | `discover()` |
| Read what the bot engine is doing (with Ostinato) | `observe(...)`, `revalidate(...)` |
| Send the bot somewhere (with Ostinato) | `requestTravel(...)` |
| Make it fight players or mobs (Ostinato's own combat) | `requestCombat(...)` (§5.9) |
| Read the player, inventory, effects and nearby entities | `observe(PLAYER_STATUS / PLAYER_MOTION / INVENTORY_SLOTS / ACTIVE_EFFECTS / NEARBY_ENTITIES ...)` (§5.10) |
| Read blocks, or find blocks around the player | `blockAt(...)`, `findBlocks(...)` (§5.10) |
| Switch hotbar slot, turn, attack, use an item, chat | `selectHotbar`, `look`, `attack`, `useItem`, `sendChat` (§5.10) |
| Draw boxes, lines and tracers in the world | `showOverlay(...)` (§5.11) |
| React to block changes, chunks, deaths and received chat | `subscribe(Topics.BLOCK_CHANGES / CHUNKS / DEATHS / CHAT, ...)` (§5.12) |
| Give the player chat commands, and talk back | `command(...)`, `showMessage(...)` (§5.12) |
| Have settings the player can change, saved between games | `settings()`, `get(...)`, `+set ...` (§5.13) |
| Stop it, and know when it has *really* stopped | `cancel(...)`, `operationStatus(...)` |
| Know whether the bot is free to use | `resourceStatus()` |
| See why anything happened | the diagnostic sink |
| Share the controls with other modules | leases: `acquire(...)`, `release(...)` (§11) |
| Talk to other modules | topics: `publish`/`subscribe`; services: `provide`/`service` (§11) |
| Turn other modules on and off | the `ModuleManagement` service (§11) |
| Ask whether an item, block, mob, effect or engine feature exists in *this* game | `content(...)` (§12) |
| Ship parts of your module that only work where their content exists | `features()`, `feature(...)` (§12) |

> **Long-term goal.** Conductor is being built so that it could become *the* unified, version-agnostic platform for Minecraft mods: write a mod once, run it on every supported version next to every other mod. See [VISION-client-mod-host.md](VISION-client-mod-host.md) §0 for what that means and what is still missing.

---

## 2. Four ideas that explain every rule

Conductor is strict on purpose. Every rule comes from one of these four ideas.

1. **"Asking to stop" is not "stopped".** In live tests, after `cancel()` the bot kept walking for up to **17 seconds** until it finished its current stretch of path. So Conductor says *CANCELLING* right away and only says *CANCELLED* once it can **see** that the bot has let go.
2. **Missing information is never good news.** If Conductor can't read something, the answer is *unknown*, never "no". Unknown never counts as safe.
3. **Old information goes stale.** A reading taken before you changed dimension, reconnected, or loaded a new world no longer counts. Every reading is stamped with the *context* it came from (session, epoch, dimension).
4. **Only the game thread may touch the game.** Every Conductor call must happen on Minecraft's main (game) thread. Calls from other threads are refused before anything is touched.

Keep these in mind and none of the rejections in §6 will surprise you.

---

## 3. Set up and build

You need:
- **JDK 21**
- **An Ostinato checkout for each Minecraft version you build the Conductor mod for**, with its classes compiled. The Conductor mod bundles the optional Ostinato engine layer, which is compiled against them. This is a build-time need only: the built mod runs with or without Ostinato.
  - `main` branch, for 1.21.11: put it next to Conductor as `../Ostinato`
  - `1.21.4` branch: put it next to Conductor as `../Ostinato-1.21.4`, or pass `-PostinatoDir_1_21_4=/path`

  Writing a **module** needs none of this: a module only needs the `contract` project.

```sh
# 1. compile Ostinato's classes (once per checkout)
(cd ../Ostinato && ./gradlew compileJava compileApiJava)
(cd ../Ostinato-1.21.4 && ./gradlew compileJava compileApiJava)

# 2. build and test Conductor
./gradlew test                                # contract, runtime and engine-layer tests on both Minecraft versions
./gradlew :conductor-mod-1.21.11:build :conductor-mod-1.21.4:build
./gradlew :example-walk-and-stop:build        # a module: one jar for every version
```

What to install:

| Jar | Where it goes | Where it's built | Per version? |
|---|---|---|---|
| Ostinato (**optional**: only for travel and combat) | `mods/` | Ostinato's own build (the `unoptimized` jar) | yes |
| **Conductor mod** | `mods/` | `conductor-mod/<mc>/build/libs/conductor-mod-<mc>-<v>.jar` (not the `-dev` one) | yes: pick your game's version |
| **Your modules** | **`conductor/modules/`** in the game directory (created on first start) | `examples/walk-and-stop/build/libs/example-walk-and-stop-<v>.jar` | **no: the same jar everywhere, under any loader** |

---

## 4. Your first module, step by step

We'll build the example that ships in [`examples/walk-and-stop`](../examples/walk-and-stop). It waits until the bot is free, walks 20 blocks east, cancels after 2 seconds, and waits until the stop is confirmed.

A module is **one Java class plus a one-line service file**. It is a plain jar, not a Fabric (or Forge) mod.

### Step 1: create the project

```
examples/my-module/
  build.gradle
  src/main/java/my/pkg/MyModule.java
  src/main/resources/META-INF/services/conductor.contract.ConductorModule
```

`build.gradle`: the module sees **only** the contract, so the compiler itself stops you from using version-specific classes:

```groovy
dependencies {
    compileOnly project(':contract')
}
```

Register it in `settings.gradle`, next to the example:

```groovy
include 'my-module'
project(':my-module').projectDir = file('examples/my-module')
```

### Step 2: declare the module (one line)

`src/main/resources/META-INF/services/conductor.contract.ConductorModule` contains the name of your module class:

```
my.pkg.MyModule
```

This is the JDK's standard service mechanism (`java.util.ServiceLoader`); no mod loader is involved. Conductor names the module after the jar file (`my-module-1.0.0`). Several modules in one jar: one class name per line.

### Step 3: write the module

A module implements `ConductorModule`:

```java
public interface ConductorModule {
    ReleasePolicy releasePolicy();                     // how long the bot must look idle before it counts as free
    void onReady(ModuleContext conductor);             // once, when you're in a world
    default void onTick(ModuleContext conductor) {}    // every game tick (20 per second)
    // optional, see §11 and §12: priority(), requires(), features(), onLeaseGranted, onLeaseRevoked, onDisable
}
```

`ModuleContext` is your handle to Conductor. It has everything in §5, plus the multi-module tools in §11 and §12.

**You never have to think about threads:** these methods are always called on the game thread.

Here is the heart of the example (full file: [`WalkAndStop.java`](../examples/walk-and-stop/src/main/java/conductor/example/WalkAndStop.java)):

```java
@Override
public ReleasePolicy releasePolicy() {
    // No default on purpose; 20 ticks (1 s) covered every stop seen in testing. Give up proving a stop after 60 s.
    return new ReleasePolicy(OptionalLong.of(20), OptionalLong.of(1200));
}

@Override
public void onTick(ModuleContext conductor) {
    ticksInStep++;
    switch (step) {
        case WAIT_FOR_RESOURCE -> {
            // A request is only granted while the bot is free, so check first.
            if (conductor.resourceStatus().state() == ResourceState.AVAILABLE) {
                walk(conductor);
            }
        }
        case WALKING -> {
            if (ticksInStep == 40) {                                  // 2 seconds
                conductor.cancel(operation);                          // → CANCELLING ("requested")
                next(Step.STOPPING);
            }
        }
        case STOPPING -> {
            OperationState state = conductor.operationStatus(operation).status().orElseThrow().state();
            if (state == OperationState.CANCELLED) {                  // really stopped
                log("stop confirmed after %d ticks", ticksInStep);
                next(Step.DONE);
            }
        }
        case DONE -> { }
    }
}

private void walk(Conductor conductor) {
    // The player's position comes from Conductor, not from Minecraft classes.
    Optional<BlockPoint> here = conductor.observe(ObservationKinds.PLAYER_POSITION, FreshnessPolicy.sameTick())
            .observation().flatMap(o -> o.observedValue());
    if (here.isEmpty()) {
        return;                                                       // try again next tick
    }
    BlockPoint target = here.get().offset(20, 0, 0);
    OperationResult result = conductor.requestTravel(NavigationGoal.getToBlock(target.x(), target.y(), target.z()));
    if (!result.isAccepted()) {
        log("travel refused: %s (%s)", result.rejection().orElseThrow(), result.detail());
        return;                                                       // try again next tick
    }
    operation = result.status().orElseThrow().id();
    next(Step.WALKING);
}
```

The pattern to remember is **a small state machine driven by `onTick`**. Never wait or sleep inside a module: just check again on the next tick.

### Step 4: build and run

```sh
./gradlew :my-module:build
```

Put `examples/my-module/build/libs/my-module-<v>.jar` into `<game dir>/conductor/modules/`. Ostinato and the **Conductor mod for your Minecraft version** go in `mods/` as usual. That's all: the same module jar works on every version. (For testing, `-Dconductor.modulesDir=/some/folder` points Conductor at another folder.)

> **Version-agnostic rule:** import only `conductor.contract.*` and the JDK. If you import a Minecraft class, your module is tied to one version again. Ask Conductor for what you need instead, and if Conductor doesn't offer it yet, see §9.

---

## 5. Everything Conductor can do

All of these are methods on `Conductor`. Call them only from `onReady` / `onTick`, which is the game thread.

### 5.1 `discover()`: what is available

Returns the contract version, the current context, and a list of **capabilities**:

| Capability id | Availability | Operations | Observation kinds |
|---|---|---|---|
| `navigation` | `AVAILABLE` on Ostinato | `requestTravel`, `cancel`, `operationStatus` | the 6 engine kinds below |
| `navigation.secondary-backend` | `AVAILABLE` only if Tungsten is installed | none | the 3 `SECONDARY_BACKEND_*` kinds |
| `player` | `AVAILABLE` | none | `PLAYER_POSITION` |

### 5.2 `observe(kind, freshnessPolicy)`: read what the engine is doing

```java
ObservationResult<Boolean> r = conductor.observe(ObservationKinds.PATH_SEGMENT_PRESENT, FreshnessPolicy.sameTick());
if (r.isAccepted()) {
    Observation<Boolean> o = r.observation().get();
    o.availability();     // OBSERVED, NOT_OBSERVED, NOT_APPLICABLE or UNKNOWN
    o.observedValue();    // Optional: present only when OBSERVED
}
```

| Kind | Value | Meaning | Good to know |
|---|---|---|---|
| `PATH_SEGMENT_PRESENT` | Boolean | The engine is walking a stretch of path | **Tracks real control** (live-tested) |
| `FORCED_INPUTS` | Set of key names | Keys the engine is holding (`MOVE_FORWARD`, `JUMP`, `CLICK_LEFT`, ...) | **Tracks real control** (live-tested) |
| `NEXT_SEGMENT_PRESENT` | Boolean | A next stretch is planned | |
| `PATH_CALCULATION_PENDING` | Boolean | A path is being calculated | |
| `CONTROLLING_PROCESS` | Optional class name | Which engine process is steering (e.g. `MineProcess`) | Goes empty the moment you cancel, **even though the bot keeps moving** |
| `TRAVEL_GOAL_ACTIVE` | Boolean | The travel process is active | Same caveat as above |
| `SECONDARY_BACKEND_BOUND` | Boolean | Tungsten is installed and linked | |
| `SECONDARY_BACKEND_PATHING` | Boolean | Tungsten is moving | Only `true` is trustworthy; Tungsten's `false` is reported as UNKNOWN |
| `SECONDARY_BACKEND_STOPPING` | Boolean | Tungsten is winding down | Same as above |
| `PLAYER_POSITION` | `BlockPoint(x, y, z)` | The block the player stands in | Use this instead of Minecraft's position classes |

**Freshness policies.** You must always pass one; there is no default.
- `FreshnessPolicy.sameTick()`: only a reading from this exact tick counts.
- `FreshnessPolicy.maxAge(OptionalLong.of(ticks), Optional.of(duration))`: a reading counts while it is younger than the given ticks and/or time.

### 5.3 `revalidate(observation, freshnessPolicy)`: is an old reading still good?

It rejects the reading if it came from another context (`CONTEXT_INVALIDATED`) or is too old (`STALE`). A stale reading still tells you what *was* seen: availability and freshness are separate.

### 5.4 Travel: `requestTravel`, `cancel`, `operationStatus`

```java
NavigationGoal.block(x, y, z)          // stand on this block
NavigationGoal.getToBlock(x, y, z)     // reach this block (next to it is fine)
NavigationGoal.near(x, y, z, range)    // get within range
NavigationGoal.xz(x, z)                // reach this column, any height
```

The life of a travel operation:

```
requestTravel ─► EXECUTING ──cancel()──► CANCELLING ──► STOPPED_PATHING ──► CANCELLED   (bot really stopped)
                     │                       │
                     │                       └── no proof within uncertainAfterTicks ─► UNCERTAIN
                     ├── bot went idle by itself ─► UNCERTAIN ("outcome not established": Conductor can't prove arrival)
                     └── another process took over, or the context changed ─► UNCERTAIN / CANCELLING
```

| State | Meaning |
|---|---|
| `EXECUTING` | Accepted, the bot is working on it |
| `CANCELLING` | You asked to stop; the bot may still be moving |
| `STOPPED_PATHING` | The path is gone, but keys may still be held |
| `CANCELLED` | Proven stopped; you no longer hold the bot |
| `UNCERTAIN` | Conductor cannot prove the outcome. Treat it as "did not succeed". |

`OperationStatus` also has `contextValid` (false after a dimension change or reconnect) and `authorityHeld` (whether this operation may still drive the bot).

### 5.5 `resourceStatus()`: is the bot free?

- `AVAILABLE`: the bot has looked completely idle for your `confirmStopWindowTicks`. A new request will be accepted.
- `QUARANTINED`: in use, still stopping, used by something else (mining, drowning protection, ...), or not yet proven idle.

The bot counts as idle when, **all at once**: no path, no next path, no calculation, no keys held, no process steering, no travel goal, and no Tungsten linked. Each of these must be actually *read*; unknown never counts.

### 5.6 `ReleasePolicy`: your two timing knobs

```java
new ReleasePolicy(OptionalLong.of(20),    // confirmStopWindowTicks: idle this long → AVAILABLE / CANCELLED
                  OptionalLong.of(1200))  // uncertainAfterTicks: after cancel, give up proving after this → UNCERTAIN
```

Neither has a default. Without `confirmStopWindowTicks`, **nothing is ever granted** (fail closed). A timeout never makes the bot "free"; only evidence does.

### 5.7 The diagnostic sink: why did that happen?

Everything important is written to an in-process log you can read with `sink.snapshot()`:

| Event | When |
|---|---|
| `CONTEXT_ESTABLISHED` / `CONTEXT_INVALIDATED` / `CONTEXT_ENDED` | world, dimension or connection changed |
| `AUTHORITY_GRANTED` / `AUTHORITY_INVALIDATED` / `AUTHORITY_RELEASED` | an operation gained or lost the right to drive the bot |
| `OPERATION_STATE_CHANGED` | e.g. `CANCELLING -> STOPPED_PATHING` |
| `CANCEL_INVOKED` | the engine's cancel was called ("invocation is not confirmation") |
| `RESOURCE_STATE_CHANGED` | `AVAILABLE` ↔ `QUARANTINED`, with the reason |
| `INTERFERENCE` | something else touched the bot; subject is `DETECTED` or `BLOCKED` |
| `OBSERVATION_REJECTED` / `OPERATION_REJECTED` | any refusal, with its reason |
| `PROVIDER_FAULT` | the engine threw while being read |
| `OFF_THREAD_VIOLATION` | someone called from the wrong thread |

The Conductor mod creates the sink. Reading it from a module is not exposed yet; in tests (§7) you build `ContractService` with your own sink and read it directly.

### 5.8 Interference: what Conductor can and can't see

| Something else... | Class | What Conductor does |
|---|---|---|
| A second request from your own module while one is running | **BLOCKED** | Refused (`CONFLICTING_OPERATION`) |
| A second Conductor installation | **BLOCKED** | Refused |
| Another Ostinato process steering (mining, drowning protection, ...) | **DETECTED** | Bot marked busy; your request is refused, or your running operation becomes `UNCERTAIN` |
| Tungsten installed | **DETECTED** only when it reports `true`, otherwise **UNDETECTABLE** | Bot never counted as free |
| A human pressing keys | **UNDETECTABLE** | Not claimed |

### 5.9 Combat: `requestCombat(goal)`

Combat hands a fight to Ostinato's own combat code: `PvpProcess` for players, `PveProcess` for mobs, the same code behind its `#pvp` and `#pve` commands. Conductor doesn't fight; it starts, watches and stops the engine's fight with the same safety rules as travel.

```java
OperationResult r = conductor.requestCombat(CombatGoal.hostiles());   // or players(), player("Alex"), mob("minecraft:zombie")
OperationId fight = r.status().orElseThrow().id();

Optional<String> target = conductor.observe(ObservationKinds.COMBAT_TARGET, FreshnessPolicy.sameTick())
        .observation().flatMap(o -> o.observedValue()).flatMap(t -> t);  // "minecraft:zombie Zombie", or empty

conductor.cancel(fight);          // CANCELLING; CANCELLED only once the evidence shows the bot let go
```

What to know:
- **Combat and travel share the bot's movement.** Only one of them runs at a time (`CONFLICTING_OPERATION`), and both need `resourceStatus()` to be `AVAILABLE`.
- **The engine picks the weapons** from what the player carries: sword, axe, shield, and on engine builds that have the code, spear, mace, wind charges and so on. Ask `content(ContentId.engine("ostinato:combat/spear"))` to find out which (see §12).
- **The use key is part of the stop evidence.** Ostinato raises shields and eats by holding the game's use key directly. Conductor therefore waits until `USE_KEY_HELD` is false before calling the bot free. A person holding right-click also keeps the bot "busy".
- **A fight doesn't end on its own.** With no target, the engine waits for one. Cancel when you're done; the worked example stops after a while without a target.
- **Inventory:** `ObservationKinds.INVENTORY_ITEMS` gives the inventory as item id → count, so you can check a kit without Minecraft classes.

### 5.10 Player, world and one-shot actions

These are the everyday tools of client mods (the [Meteor survey](survey-meteor-client.md) shows which are used most).

**Reads** (observations, with a freshness policy as always):

| Kind | Value |
|---|---|
| `PLAYER_STATUS` | health, absorption, food, saturation, air, onGround, sprinting, sneaking, inWater, fallFlying, game mode |
| `PLAYER_MOTION` | exact x/y/z, velocity per tick, yaw, pitch |
| `NEARBY_ENTITIES` | entities within 32 blocks, nearest first: id, type id, name, position, distance, health, player?, hostile? |
| `INVENTORY_SLOTS` | non-empty slots: 0-8 hotbar, 9-35 main, 36-39 armour (feet to head), 40 offhand; item id, count, durability |
| `SELECTED_SLOT` | 0-8 |
| `ACTIVE_EFFECTS` | effect id, amplifier, ticks left |

**Blocks:**

```java
Optional<String> below = conductor.blockAt(here.offset(0, -1, 0)).block();   // empty = chunk not loaded, never "air"
List<BlockPoint> ores = conductor.findBlocks(Set.of("minecraft:diamond_ore"), 16, 10).found(); // nearest first
```

**Actions.** Each needs its lease, so two modules never fight over the same control:

| Action | Lease |
|---|---|
| `selectHotbar(slot)` | HOTBAR |
| `look(yaw, pitch)` | LOOK |
| `attack(entityId)`, `useItem(offhand)` | INTERACT |
| `sendChat(text)` (a `/command` too) | CHAT |

```java
conductor.acquire(Resources.HOTBAR);
ActionResult r = conductor.selectHotbar(3);      // accepted = the game was told
// next tick: observe(SELECTED_SLOT) == 3 confirms it
```

The rules:
- **Accepted means "the game was told".** Confirm with the next observation, as the example does. The server can still refuse.
- **Engine conflict:** while the bot is moving or fighting (`resourceStatus()` not `AVAILABLE`), the engine sets look, hotbar and clicks itself. Those actions are refused with `RESOURCE_NOT_RELEASED` instead of being silently overwritten. Chat is not affected.
- **Chat:** one message per tick, at most 256 characters, and no characters servers kick for (control codes, `§`).
- **Every action is recorded** in the diagnostic sink (`ACTION`).

Full worked example: [`examples/player-actions`](../examples/player-actions/src/main/java/conductor/example/actions/PlayerActions.java), verified live on both versions ([live-run-player-actions.md](live-run-player-actions.md)).

### 5.11 Overlays: draw in the world

```java
List<OverlayShape> shapes = List.of(
    OverlayShape.block(ore, 0xFF00FFFF).throughWalls(true),          // outline one block, seen through walls
    OverlayShape.tracer(x + 0.5, y + 0.5, z + 0.5, 0xFF00FFFF),     // a line from the crosshair to a point
    OverlayShape.line(x1, y1, z1, x2, y2, z2, 0x80FF0000),           // any line; colour is 0xAARRGGBB
    OverlayShape.box(x1, y1, z1, x2, y2, z2, 0xFFFFFF00));           // any box
conductor.showOverlay(shapes);        // replaces this module's overlay; drawn every frame until replaced
conductor.showOverlay(List.of());     // clear it
```

Each module has its own overlay, so modules never erase each other's. It disappears when the module stops. There is no lease, because drawing doesn't change the game. Limit: 4096 shapes per module.

### 5.12 Events, commands and messages

**Events** arrive as topic messages on the game thread at the end of the tick (§11.2):

| Topic | Message |
|---|---|
| `Topics.BLOCK_CHANGES` | `BlockChange(at, block)`: a block the server changed |
| `Topics.CHUNKS` | `ChunkChange(chunkX, chunkZ, loaded)` |
| `Topics.DEATHS` | `PlayerDeath()` |
| `Topics.CHAT` | `ChatMessage(text, sender)`: received chat; sender only for player chat |
| `Topics.SETTINGS` | `SettingChanged(module, name, value)` |

**Chat commands.** The player types `+name args...`. The line never reaches the server, and your handler runs on the game thread:

```java
conductor.command("highlight", "rescan and report", call -> {        // call.args() = the words after the name
    conductor.showMessage(found.size() + " found");                  // a line in the player's own chat, labelled with your module
});
```

Conductor's own commands:
- `+help`
- `+modules`
- `+enable <module>`, `+disable <module>`
- `+settings <module>`
- `+set <module> <setting> <value>`

Command names are first come, first served. The prefix is `+` (change it with `-Dconductor.commandPrefix`).

### 5.13 Settings

```java
static final Setting<Integer> RADIUS = Setting.integer("radius", "search radius", 8, 1, 16);   // also bool, decimal, text
@Override public List<Setting<?>> settings() { return List.of(RADIUS); }
...
int r = conductor.get(RADIUS);
```

- **Saved between games**, in `<game dir>/conductor/config/<module>.properties` (plain text).
- **The player changes them** with `+set <module> radius 12`; out-of-range values are refused.
- **A settings-screen module** can list and change any module's settings through `ModuleManagement.settings(...)` and `setSetting(...)`.
- **Every change is published** on `Topics.SETTINGS`.

Worked example with all of §5.11-5.13: [`examples/block-highlighter`](../examples/block-highlighter/src/main/java/conductor/example/highlight/BlockHighlighter.java). It is a version-agnostic "block ESP".

---

## 6. When Conductor says no

Every refusal has a reason. Here is what each one means and what to do.

| Reason | What it means | What to do |
|---|---|---|
| `OFF_THREAD` | Called from the wrong thread | Call from `onReady` / `onTick` only |
| `NO_FRESHNESS_POLICY` | `observe` without a policy | Pass `FreshnessPolicy.sameTick()` or `maxAge(...)` |
| `NO_CURRENT_CONTEXT` | Not in a world | Wait for the next tick |
| `CONTEXT_INVALIDATED` | The reading is from before a dimension or world change | Read again |
| `STALE` | The reading is too old for your policy | Read again |
| `UNSUPPORTED_KIND` | This runtime doesn't offer that observation | Check `discover()` |
| `RESOURCE_NOT_RELEASED` | The bot isn't proven free (busy, still stopping, something else steering, or no `ReleasePolicy`) | Wait for `resourceStatus()` to be `AVAILABLE`; check `detail()` for why |
| `CONFLICTING_OPERATION` | Your previous operation still holds the bot | Wait for `CANCELLED`, or for it to finish |
| `BACKEND_UNOBSERVABLE` | Ostinato would use Tungsten, whose stop can't be observed | Set Ostinato's `movementBackend` to `baritone`, or remove Tungsten |
| `ENGINE_DECLINED` | The engine refused (e.g. no path) | Try another goal once the bot is free again |
| `UNKNOWN_OPERATION` | That operation id isn't yours | Use the id from your own `requestTravel` |
| `AUTHORITY_INVALIDATED` | The operation's context changed, so it can no longer act | Start a new operation once the bot is free |
| `NO_NAVIGATION` | The runtime has no travel support | Check `discover()` |
| `INVALID_ARGUMENT` | null goal or id | Fix the call |
| `NO_COMBAT` | The runtime has no combat support | Check `discover()` |
| `NO_ACTIONS` | The runtime offers no actions or block access | Check `discover()` |
| `LEASE_REQUIRED` | Your module doesn't hold the action's lease | `acquire(...)` the resource first (§5.10) |
| `TARGET_UNAVAILABLE` | No such entity, it's dead, or it's out of reach (or no player position for a block search) | Read `NEARBY_ENTITIES` again |
| `RATE_LIMITED` | A second chat message in one tick | Send it next tick |
| `LEASE_HELD` | Another module holds MOVEMENT | Wait, or acquire MOVEMENT at a higher priority (§11.1) |

---

## 7. Testing your module

**Unit tests without Minecraft.** Modules and the contract are plain Java, so you can test a module against a fake runtime: implement `RuntimeIntegration` and `NavigationPort` with whatever values you want. See `contract/src/test/java/conductor/contract/FakeRuntime.java` and `FakePort.java` for small, complete examples. Build a `ContractService` around them, call `tick()` to simulate game ticks, and drive your module's `onTick` yourself.

**Live tests in a real game.** `scripts/live-run.sh` starts a local server and Ostinato's client under a virtual display, with the live harness mod. It records every reading, plus the player's real position and speed, every tick:

```sh
# Conductor's own scenarios (Stage 4 checks, evidence trials)
MC=1.21.4 OSTINATO_DIR=../Ostinato-1.21.4 scripts/live-run.sh /tmp/run   # needs /tmp/run/server/server.jar (vanilla, same version)
python3 scripts/analyze-live.py /tmp/run/harness.jsonl

# your module, in a real game: the Conductor mod as a mod, your module jar in a modules folder;
# the harness only joins the world and waits. SERVER_ON_JOIN can script the server (items, mobs, difficulty).
mkdir -p /tmp/modules && cp examples/my-module/build/libs/my-module-0.4.0.jar /tmp/modules/
HARNESS_SCENARIO=connect-only HARNESS_HOLD=60 CONDUCTOR_MODULES=/tmp/modules \
EXTRA_MODS=conductor-mod/1.21.4/build/libs/conductor-mod-1.21.4-0.4.0.jar \
MC=1.21.4 OSTINATO_DIR=../Ostinato-1.21.4 scripts/live-run.sh /tmp/run
grep my-module /tmp/run/client.log
```

Read the script header for the details.

---

## 8. Supporting another Minecraft version

Thanks to the shared code, a new version is mostly configuration:

1. **Add the version to `mcTargets`** in `build.gradle`, with its Parchment and Fabric Loader versions and, if there is an Ostinato branch for it, where its checkout lives (or `legacy: true`, see step 3).
2. **Create `runtime/<mc>/`** with a 2-line `build.gradle` and its own `MinecraftGameAccess.java`. That class holds every Minecraft call that can differ between versions (thread check, dimension name, connection, reach, selected slot). For 1.21.4 versus 1.21.11 the differences were a handful of renamed methods.
3. **Create `conductor-mod/<mc>/`** with a 3-line `build.gradle`, its `conductor.mixins.json`, and the per-version render hook: `LevelRendererMixin` and `VersionLineDrawer` (overlay drawing changed a lot between versions). The client-tick hook and the entrypoints are shared in `conductor-mod/common`. The chat and packet hooks and the content Mixins are shared by the 1.21 versions in `conductor-mod/modern` and only need checking there.

   **An older version** (one whose APIs differ too much for `runtime/modern` and `conductor-mod/modern`) is a `legacy: true` target, as 1.16.1 is. It gets no Parchment and no Ostinato. It brings its own `MinecraftPlayerAccess`, `ContentInstaller`, chat and packet Mixins. See [`live-run-1.16.1.md`](live-run-1.16.1.md).

   **A newer version with no Ostinato branch adapted yet** is a `noEngine: true` target. 26.3 is one; it is also `unobfuscated: true` (Mojang ships it with its own names, so there are no intermediary mappings) and `java: 25`. It shares `runtime/modern` and `conductor-mod/modern`. Calls that changed go into small `Version*` classes in its own folder. Its render hook is `LevelExtractorMixin`, because 26.3 extracts the level into a frame before drawing it. See [`live-run-26.3.md`](live-run-26.3.md).
4. **Optional, for travel and combat: `engine-ostinato/<mc>/`.** Check the Ostinato branch has the API Conductor uses (`IMovementEngine`, `IPathingBehavior`, `IPathingControlManager`, `IInputOverrideHandler`): `git diff` those files against `main`. Then `live-harness/<mc>/` (with its `Compat.java`) for evidence runs.
5. **Add the version** to the list in `settings.gradle`. Existing modules need **nothing**: they keep working, because they never touch Minecraft.
6. **Run `./gradlew test`**: the same runtime and engine-layer tests now run on the new version too. Then do a live run with `MC=<version>`.

---

## 9. Adding a new capability

Navigation and combat are the engine capabilities today. Mining, building, farming, elytra, following and others are listed in [`deferred-candidates.md`](deferred-candidates.md). They are **not available yet**, and each must pass a gate first. Combat is the latest to go through it: its audit found the use-key gap that §5.9 describes. This is the process navigation went through; follow it for any new capability:

1. **Audit** how the feature starts, stops and steers, labelling each fact Verified, Reported or Unknown (example: [`stage1-audit.md`](stage1-audit.md)). Pay special attention to: does "stop" really stop? Which keys and processes does it use?
2. **Observe before you act.** Add read-only `ObservationKind`s for the feature's signals and test them live with the harness.
3. **Delegate to the existing engine.** Add a port like `NavigationPort` that calls Ostinato's existing API. Never re-implement or change Ostinato.
4. **Prove stops with evidence.** Use the live harness to find which signals really track control (for navigation: path segment plus forced keys). Release only on those.
5. **Classify interference** (BLOCKED / DETECTED / UNDETECTABLE / NOT_APPLICABLE).

Hard limits, by design: no changes to Ostinato's process scheduling, and no new stop mechanisms that Ostinato doesn't already have. Sharing between modules goes through leases (§11), not through a new engine scheduler.

---

## 10. Limits you should know about

Conductor is young. Here is everything it **cannot** do today, so you can plan around it. The long-term goal of hosting any kind of client mod, and what it would take, is in [VISION-client-mod-host.md](VISION-client-mod-host.md).

**What it can do**
- **Travel and combat are the only engine actions.** No mining, building, farming, elytra or following through the contract yet (see [deferred-candidates.md](deferred-candidates.md)).
- **Combat is all-or-nothing per goal.** You choose whom to fight; the engine chooses weapons and tactics. There is no contract control over individual weapons, and no per-weapon engine settings exist to drive. `content()` tells you what the engine build can do (for example `ostinato:combat/spear`).
- **The Autotune Combat System is engine code.** It lives on Ostinato's `autotune` branch (1.21.11 only). Where that build runs, `ostinato:pvp/autotune` is PRESENT and its tuning happens inside the engine's fight. Conductor can't tune it, and on 1.21.4 there is no Autotune to switch on.
- **No rendering, HUD, chat, keybinds, inventory, packets, camera or GUI.** A module can draw nothing and send nothing through Conductor. It can still use Minecraft directly, but then Conductor's safety rules don't cover that code.
- **No arrival signal.** A trip that ends on its own is `UNCERTAIN` ("outcome not established"). Check the player's position yourself.

**How many modules, and where**
- **Many modules, but trusted ones.** All installed modules run side by side (§11). They share one JVM: a module that ignores leases and touches Minecraft directly can't be stopped by Conductor, only noticed (MOVEMENT) or not at all (the other resources).
- **Only MOVEMENT has evidence-backed hand-over.** LOOK, HOTBAR, INTERACT, SCREEN and CHAT are handed over as soon as the holder's `onLeaseRevoked` returns. Conductor can't check that the holder really let go.
- **Preempting MOVEMENT is as slow as the engine's stop** (up to 17 s seen on 1.21.11). A SAFETY module that must act at once should not depend on MOVEMENT.
- **Modules are version-agnostic only if they stay inside the contract.** A module that imports Minecraft classes works, but is tied to one version and outside Conductor's safety rules.
- **Modules can't read the diagnostic sink at runtime yet** (only in tests).
- **Travel and combat need Ostinato.** Conductor runs without any other mod, but its only movement engine today is Ostinato. Without it, `discover()` lists no navigation or combat capability, and `requestTravel`/`requestCombat` are rejected with `NO_NAVIGATION`/`NO_COMBAT`. With no engine, MOVEMENT is free once the use key is up for the release window (nothing else could be steering).
- **Boots on Fabric only, today.** Modules don't depend on any loader. Conductor's only Fabric code is a 40-line boot shim; its game hooks are plain Mixins, which Fabric, Quilt, Forge and NeoForge all run. Other shims and a loader-free boot are planned: [LOADER-INDEPENDENCE.md](LOADER-INDEPENDENCE.md).
- **Modules in `conductor/modules/` are not transformed.** They get no Mixin and no remapping, which is exactly right for contract modules. Native modules that need Minecraft classes ship inside their own mod and are found on the game's classpath through the same service file.
- **Minecraft 26.3, 1.21.11, 1.21.4 and 1.16.1.** Ostinato (travel and combat) is adapted for 1.21.11 and 1.21.4 only. Ostinato's 26.3 branch (Java 25) has no adapter yet, so 26.3 runs with no engine for now. Ostinato's 1.16.1 branch's API differs too much to reuse as is, so 1.16.1 runs with no engine.
- **No hot reload.** Modules load at game start. Disabling a module stops it logically; its code stays loaded.
- **Block-change events cover what the server sends.** Single-block and section updates come from Conductor's own packet hooks. Changes the client makes itself (before the server confirms them) are not events.
- **Overlays are outlines and lines only.** No filled boxes, text labels or 2D HUD yet.
- **Commands are typed in chat.** No keybinds yet.
- **`content()` covers built-in registries only.** Items, blocks, entity types and effects work. Data-driven things (enchantments, biomes, recipes, damage types) are UNKNOWN, so a feature that needs them stays off.
- **Engine features mean "the code is in this build".** PRESENT for `ostinato:combat/spear` says the Ostinato build has spear code, not that a combat capability exists or is running.

**How the engine behaves**
- **Stops take as long as Ostinato takes.** After `cancel()` the bot usually finishes its current stretch of path. Live, that was up to 344 ticks (17 s) and 122 blocks on a long path. Conductor reports this honestly but cannot make it faster.
- **`cancel()` stops more than your operation.** Ostinato's cancel also stops other processes, such as mining.
- **Something else can take the bot at any time.** For example, Ostinato's drowning protection (`AirProcess`) took over about 10 s after a stop underwater. Conductor detects this and marks the bot busy, but cannot prevent it.
- **Tungsten blocks travel.** With Tungsten installed and preferred, travel requests are refused, because its stop can't be proven.
- **Human input is invisible.** Keys pressed by a person are not seen by Conductor.

**Building and testing**
- **Building the Conductor mod needs local Ostinato checkouts** with compiled classes (for the bundled, optional engine layer). There are no published artifacts yet. Modules need only the `contract` project.
- **Tested on:** flat local test worlds (stairs, trench, water, long paths, mining), the Baritone backend, about 25 live cancel trials, and one full live run per Minecraft version.
- **Not tested:** elytra, boats, heavy parkour, Tungsten, real servers (latency, anticheat), long play sessions.
- **The live harness hangs on loading errors.** If a mod fails to load, Fabric opens an error window that nobody can close in a headless run. The run then waits for the script's timeout instead of failing at once.

---

## 11. Many modules side by side

Every installed module runs at the same time. Each gets its own `ModuleContext`, so Conductor always knows who asked for what. Modules coordinate in three ways.

### 11.1 Leases: who may use a control right now

Shared controls are **resources**, in `Resources`:

| Resource | What it covers | Hand-over |
|---|---|---|
| `MOVEMENT` | pathing and movement keys | waits for **release evidence** (Stage 4 rules), like `requestTravel` |
| `LOOK` | player rotation | immediate, once the holder's `onLeaseRevoked` returns |
| `HOTBAR` | selected slot, inventory moves | same |
| `INTERACT` | attack / use clicks | same |
| `SCREEN` | open GUIs and containers | same |
| `CHAT` | sending messages and commands | same |

```java
LeaseResult r = conductor.acquire(Resources.HOTBAR);                  // at your module's priority()
LeaseResult r = conductor.acquire(Resources.MOVEMENT, Priority.HIGH); // or a chosen one
switch (r.status()) {
    case GRANTED -> useIt(r.lease().orElseThrow());
    case PENDING -> { }        // you outrank the holder; onLeaseGranted is called once it's yours
    case DENIED  -> log("held by %s: %s", r.holder(), r.detail());
}
conductor.release(lease);                                           // when done
```

The rules:
- **Priorities:** `BACKGROUND < NORMAL < HIGH < SAFETY`. Set your module's default with `priority()`.
- **A free resource is granted.** MOVEMENT is free only once evidence shows the bot idle.
- **Equal or lower priority than the holder:** DENIED. The first holder wins.
- **Higher priority:** **preemption.** The holder's `onLeaseRevoked(conductor, lease, by)` is called (stop using it *now*), and its travel operations are cancelled. The lease is then yours: at once for the five non-evidence resources, or, for MOVEMENT, as PENDING until the engine is shown idle.
- **`requestTravel` takes MOVEMENT for you** if it is free, and gives it back when the trip ends. If another module holds MOVEMENT, travel is refused with `LEASE_HELD`. Hold MOVEMENT yourself to keep it across several trips.

### 11.2 Topics and services: talking to other modules

```java
// any module can define a typed topic; share the constant in a small API jar, or just agree on name + type
static final Topic<String> FIGHT = new Topic<>("autotune:fight", String.class);

conductor.publish(FIGHT, "engaged");                                  // delivered at the end of this tick, in order
Subscription s = conductor.subscribe(FIGHT, msg -> log("fight: %s", msg));

// services: one provider, many users
static final ServiceKey<Waypoints> WAYPOINTS = new ServiceKey<>("maps:waypoints", Waypoints.class);
conductor.provide(WAYPOINTS, myWaypoints);                            // in the provider's onReady
conductor.service(WAYPOINTS).ifPresent(w -> w.add("base", here));     // in any user
```

Declare services you can't run without in `requires()`. Conductor then starts you only once they are provided, and stops you (state `BLOCKED`) if the provider goes away.

Conductor's own topics: `Topics.LEASES` (every grant, revoke and release) and `Topics.MODULES` (lifecycle changes).

### 11.3 Management

Each module is `DISCOVERED`, `RUNNING`, `DISABLED`, `BLOCKED` (missing service) or `FAILED` (it threw). An exception in one module stops only that module.

Any module can drive the others through a service. This is how a GUI or chat-command module would offer toggles:

```java
ModuleManagement m = conductor.service(ModuleManagement.KEY).orElseThrow();
m.modules().forEach(i -> log("%s %s", i.id(), i.state()));
m.disable(someId, "user toggled it off");   // its leases are revoked, its travel cancelled, its services withdrawn
m.enable(someId);
```

### 11.4 Modules that use Minecraft directly (native modules)

Big existing mods (TenorClef, Ostinato's own Autotune) won't become contract-only overnight. They can still join as **native modules**: a module that also uses Minecraft or engine classes is tied to one version, but it takes the same leases and uses the same topics and services. If it drives Ostinato processes itself, it declares them while holding MOVEMENT:

```java
conductor.declareEngineProcesses(Set.of("baritone.process.MineProcess"));
```

Conductor then counts that process's activity as yours, not as interference. See [MODULE-PLATFORM.md](MODULE-PLATFORM.md) for the full design.

**Native modules ship as Fabric mods** with a `META-INF/services/conductor.contract.ConductorModule` entry. Conductor finds them on the game's classpath. The worked example is Ostinato's module edition (§11.5).

### 11.5 Ostinato as a module, and using every Ostinato feature

**Ostinato's module edition** makes Ostinato a Conductor module. It is built from the Ostinato branch `conductor-module` as `ostinato-mc1.21.11-unoptimized-fabric-<v>-module.jar`: one jar that is Ostinato and its module (class `baritone.conductor.OstinatoModule`). Without Conductor installed, it behaves like the plain build.

- **Your `#` commands share the controls.** Before a command that moves the bot (`#goto`, `#mine`, `#follow`, `#explore`, `#farm`, `#build`, `#elytra`, ...) runs from chat, the GUI or code, the module takes the MOVEMENT lease for it:
  - **a free bot:** the command runs at once;
  - **held by a lower-priority module:** that module is stopped, the stop is proven, and then your command runs (it is queued);
  - **held by an equal or higher module** (a fight, an escape): the command is refused, with the reason in chat ("Not run: movement is held by …").

  Your commands count as HIGH by default (setting `player-first`).
- **A higher-priority module can take over** an Ostinato task. Ostinato is stopped, and the hand-over waits for proof.
- **`+ostinato`** says what Ostinato is doing and who holds MOVEMENT.
- **Every Ostinato feature is usable from any module** through the `EngineCommands` service, with no Minecraft or Ostinato classes needed:

```java
@Override public Set<ServiceKey<?>> requires() { return Set.of(EngineCommands.KEY); }   // start after Ostinato's module
...
EngineCommands engine = conductor.service(EngineCommands.KEY).orElseThrow();
conductor.acquire(Resources.MOVEMENT);                 // task commands need it
engine.run(conductor, "mine diamond_ore 8");           // Ostinato's own command language, without the #
...
engine.stop(conductor);                                 // then wait for resourceStatus() == AVAILABLE: proof of stop
conductor.release(lease);
```

While you hold MOVEMENT, Ostinato's processes count as yours, not interference. Without MOVEMENT, a task command is refused with `LEASE_REQUIRED`. Worked example: [`examples/engine-commands`](../examples/engine-commands/src/main/java/conductor/example/engine/EngineCommandsDemo.java).

**Limits:**
- The command language is Ostinato's: `engine()` says which engine answers.
- A command started by another mod straight through Ostinato's API (not its command manager) is not gated; it shows up as interference, as before.
- The module edition starts from Ostinato 1.21.0, which added the command execution guard.
- Conductor's Ostinato layer needs Ostinato's **unoptimized** build. The obfuscated `standalone` and `api` builds rename the classes it uses. With them, Conductor runs without an engine (it logs "Conductor engine: none") and the game runs.

---

## 12. One jar, every version: optional features

Minecraft versions differ in what exists. 1.21.11 has spears; 1.21.4 doesn't. A combat module shouldn't need two builds, or a check like `if (version >= 1.21.11)`. Instead, it says **what each part needs**, and Conductor switches that part off where the thing doesn't exist. The rest of the module runs normally.

### 12.1 Ask about things, never versions

```java
ContentResult r = conductor.content(ContentId.item("minecraft:iron_spear"));
// PRESENT: it exists in this game.  ABSENT: Conductor looked, and it doesn't.  UNKNOWN: Conductor couldn't tell.
```

| Kind | Example | Answered from |
|---|---|---|
| `ContentId.item(...)` | `minecraft:mace` | the game's item registry |
| `ContentId.block(...)` | `minecraft:crafter` | block registry |
| `ContentId.entity(...)` | `minecraft:breeze` | entity type registry |
| `ContentId.effect(...)` | `minecraft:wind_charged` | effect registry |
| `ContentId.engine(...)` | `ostinato:combat/spear` | the engine build (discovery's `content` capability lists those present) |

Because you ask about the thing, a module written today keeps working on a version released next year. If that version adds spears, the spear part simply switches on.

### 12.2 Declare features

```java
public final class CombatModule implements ConductorModule {

    @Override
    public List<ModuleFeature> features() {
        return List.of(
            ModuleFeature.of("spear", "lunge with a spear when the target is in reach",
                    ContentId.item("minecraft:iron_spear")),
            ModuleFeature.of("mace", "smash attacks after a fall",
                    ContentId.item("minecraft:mace")),
            ModuleFeature.of("wind", "wind-charge jumps",
                    ContentId.item("minecraft:wind_charge")));
    }

    @Override
    public void onTick(ModuleContext conductor) {
        meleeTick(conductor);                                    // always: every version has swords
        if (conductor.feature("spear").enabled()) {
            spearTick(conductor);                                // only where spears exist
        }
        if (conductor.feature("mace").enabled()) {
            maceTick(conductor);
        }
    }
}
```

Conductor checks every feature **once, before `onReady`**, and logs the decision to the diagnostic sink (`MODULE_FEATURE`, for example `spear OFF: off: item:minecraft:iron_spear ABSENT`). `conductor.features()` returns all of them, for showing in a settings screen.

On 1.21.11 this module runs melee, spear, mace and wind. On 1.21.4 it runs melee, mace and wind. There is no spear code path to configure, and no spear kits to worry about.

The rules:
- **A feature is on only if everything it requires is PRESENT.** UNKNOWN counts as off. A feature is never switched on by a guess.
- **Features with no requirements are always on.** Use them as named switches if you like.
- **Keep the off path clean.** When a feature is off, don't reference its items at all: no kit entries, no settings, no HUD rows.
- **Combine requirements** when a part needs several things, including an engine feature: `ModuleFeature.of("spear", "...", ContentId.item("minecraft:iron_spear"), ContentId.engine("ostinato:combat/spear"))`.

### 12.3 The worked example: [`examples/agnostic-combat`](../examples/agnostic-combat/src/main/java/conductor/example/combat/AgnosticCombat.java)

A complete combat module: one jar for every version. Its spear, mace and wind parts each require **both** the item and the engine's code for it. So on 1.21.4, the spear part is off because spears don't exist, and the mace part is off because Ostinato's 1.21.4 build has no mace code (although the mace item exists there). Its kit is built from the features that are on, and only that kit is checked and reported. It fights at `HIGH` priority, publishes what it's doing on the `agnostic-combat:fight` topic, and stops with proof.

### 12.4 Testing it without Minecraft

Give your fake runtime a content table and run the same module twice. `contract/src/test/java/conductor/contract/VersionFeatureTest.java` does exactly this: a 1.21.11-like runtime turns on `[melee, spear, mace]`, and a 1.21.4-like runtime turns on `[melee]`.


---

## 13. Adding content: items, blocks, dimensions, recipes

Modules so far *use* the game. A **content module** *adds to* it. Like every module it is one plain jar for every Minecraft version, and it needs no loader. The design and its reasoning are in [CONTENT-MODS.md](CONTENT-MODS.md).

### 13.1 Declare items and blocks

```java
public final class RubyContent implements ContentModule {
    public String namespace() { return "ruby"; }

    public void declare(ContentDeclarations content) {
        content.item("ruby").rarity(ItemRarity.UNCOMMON).tab("minecraft:ingredients");
        content.item("ruby_apple").food(6, 1.2f).tab("minecraft:food_and_drinks");
        content.block("ruby_block").strength(5, 6).sound(BlockSound.METAL).requiresTool().light(3)
               .tab("minecraft:building_blocks");
    }
}
```

List the class in `META-INF/services/conductor.contract.ContentModule`.

| Items | Blocks |
|---|---|
| `maxStack`, `durability`, `food(nutrition, saturation)`, `rarity`, `fireResistant`, `tab` | `strength(hardness, resistance)`, `light`, `sound`, `sounds`, `requiresTool`, `noItem`, `tab`, `shape`, `stairsOf`, `material`, `layer`, `box` |

**Block shapes.**
- **Kinds.** `shape(BlockShape.X)` makes the block vanilla's block of that kind: CUBE (the default), PILLAR, STAIRS, SLAB, WALL, FENCE, FENCE_GATE, DOOR, TRAPDOOR, BUTTON, PRESSURE_PLATE, PANE, LEAVES, CARPET or GLASS.
- **Facing cubes.** FACING turns to face the player who places it, in any of six directions, like an observer. HORIZONTAL_FACING turns only to north, east, south or west, like a furnace. Your blockstate file maps `facing=...` to models.
- **Shaped by boxes.** `box(x1, y1, z1, x2, y2, z2)` makes a MODEL block, like a lantern or a candle. Give each box in pixels (0 to 16) with the block facing north; up to 256 boxes.
  - The boxes are its outline and what you collide with; Conductor turns them with the block's `facing`.
  - The block faces the player who places it, can hold water, and lets light through.
  - Copy the boxes from your block model's `elements`; the converter does that for you.
- **Mixed sounds.** `sounds("minecraft:block.lantern.break", step, place, hit, fall)` names each sound by id, for blocks whose sounds no `BlockSound` matches.
- **Vanilla behaviour.** Walls connect, slabs stack, doors are two blocks tall, and so on. Your jar supplies the blockstate and model JSON, as a resource pack would.
- **Stairs.** `stairsOf("ruby_block")` names the block the stairs copy. It must be declared before them, or be a `minecraft:` block.
- **Material.** `material(BlockMaterial.IRON|STONE|WOOD|COPPER)` decides how doors, trapdoors, buttons, pressure plates and fence gates sound and who may use them; iron doors need redstone.
- **Render layer.** See-through shapes (DOOR, TRAPDOOR, PANE, LEAVES, GLASS, MODEL) default to the CUTOUT render layer. `layer(RenderLayer.TRANSLUCENT)` is for tinted glass.
- **Easiest JSON.** Copy vanilla's own JSON for the same kind of block, for example `stone_brick_stairs`, and rename the ids. That is how `examples/ruby-content` got its stairs, slab, wall, pillar, glass and door.

The rules:
- Ids are `namespace:path`, using Minecraft's own characters. The `minecraft` namespace and loader namespaces are refused.
- **Any problem skips the whole module**, with every reason logged. Registering half a module would leave client and server with different content.
- Content is registered **once, while the game starts**, right before Minecraft locks its registries. Modules are taken in namespace order, so every machine numbers them the same way.

### 13.2 Mobs

```java
content.mob("ruby_zombie").like(MobArchetype.ZOMBIE).health(30).attackDamage(5).scale(1.2f);
content.mob("ruby_pig").like(MobArchetype.PIG).speed(0.3);
```

- **What a declared mob is.** It is a new entity type built on a vanilla **archetype**: ZOMBIE, SPIDER or PIG. It keeps the archetype's AI, sounds, body and model. Your module changes:
  - its name (`entity.<namespace>.<path>` in the lang file);
  - its texture (`textures/entity/<path>.png` by default, laid out like the archetype's texture);
  - its size (`scale`, which changes both the model and the hit box);
  - health, speed and attack damage;
  - its drops: the loot table `data/<namespace>/loot_table/entities/<path>.json`.
- **Spawning.** Use `/summon <namespace>:<path>`, spawners, or your own data. There are no spawn eggs or natural spawning rules yet.
- **Archetype quirks carry over.** Babies from breeding a pig-like mob are vanilla pigs, and a zombie-like mob that drowns becomes a vanilla drowned.
- **Fully custom mobs and bosses.** Use `.model(name)` instead of `.like(...)`, and the mob is entirely yours: a model and animations made in Blockbench (Bedrock files), goals, a `MobBehaviour` for special attacks and phases, a boss bar and sounds. The same jar runs on 1.16.1, 1.21.4, 1.21.11 and 26.3:

  ```java
  content.mob("ruby_golem").model("ruby_golem").animations("ruby_golem").size(1.4f, 2.7f).health(200)
          .boss(BossBar.Color.PINK, BossBar.Style.NOTCHED_10).behaviour(new RubyGolemBehaviour());
  ```

  The full guide is [CUSTOM-MOBS.md](CUSTOM-MOBS.md).

### 13.3 Everything else is data in the same jar

Put models, textures and names in `assets/<namespace>/`, and loot, recipes, tags and worldgen in `data/`, exactly as a resource pack or data pack would. Conductor loads the jar as a pack on the client and on the server. There is no `pack.mcmeta` to write: Conductor supplies the pack metadata for each version.

```
assets/ruby/items/ruby.json                 the item's model (1.21.4 and later)
assets/ruby/models/item/ruby.json, textures/item/ruby.png
assets/ruby/blockstates/ruby_block.json, models/block/ruby_block.json, textures/block/ruby_block.png
assets/ruby/lang/en_us.json                 "item.ruby.ruby": "Ruby", "block.ruby.ruby_block": "Block of Ruby"
data/ruby/loot_table/blocks/ruby_block.json what the block drops
data/ruby/recipe/*.json                     crafting
data/minecraft/tags/block/mineable/pickaxe.json, needs_iron_tool.json
data/ruby/dimension/ruby_world.json         a whole new dimension (here: flat, ruby ground)
mc_1.21.4/...                               a file here replaces its normal copy on 1.21.4 only
```

**Dimensions, structures, biomes, worldgen features, enchantments and mob variants are all data.** Anything vanilla reads from a data pack can ship in a content module, with no code at all.

**On Minecraft 1.16.1 the same files are translated.** Write them for 1.21, as above. When Conductor loads the jar on 1.16.1, it changes only what changed mechanically between the versions:

- folder names (`recipe/` → `recipes/`, `loot_table/` → `loot_tables/`, `tags/block/` → `tags/blocks/`, ...);
- recipe JSON (`"ruby:ruby"` → `{"item": "ruby:ruby"}`, `result.id` → `result.item`, plain-id results for smelting and stonecutting);
- `items/<x>.json` item models → `models/item/<x>.json`;
- the vanilla door models renamed in 1.19.3.

Creative tabs map to 1.16's fixed tabs (`ingredients` → Miscellaneous, `food_and_drinks` → Foodstuffs, ...). What 1.16.1 cannot read at all is left out with a warning naming the file: dimensions and worldgen arrived in 1.16.2. A file under `mc_1.16.1/` replaces the translated copy unchanged, for anything the translation gets wrong.

### 13.4 Install

The same jar goes in `conductor/modules/` **on the server and on every client**. Items and blocks travel between them as numbers, so both sides must have the same content modules. A module that only has data (a dimension, recipes for vanilla items) should be needed on the server only, with vanilla clients able to join. That follows from how vanilla syncs data, but it has not been tested yet.

The Conductor mod runs on dedicated servers too. There it hosts content and runs server modules (§14).

### 13.5 Not yet

- **Block entities, custom item or block behaviour (right-click actions, GUIs).** These need code. Mobs with their own model, AI and code are covered (§13.2, [CUSTOM-MOBS.md](CUSTOM-MOBS.md)).
- **Spawn eggs and natural mob spawning.**
- **Block kinds not in `BlockShape`.**
- **An id handshake.** A client with different content modules from the server gets Minecraft's own errors, not a clear "module X is missing".
- **Creative tab placement is not checked live yet.** Items are added to the tab they name; a tab that doesn't exist on this version is logged.

Worked example: [`examples/ruby-content`](../examples/ruby-content). Live run: [live-run-content.md](live-run-content.md).

---

## 14. Server modules: code that runs on the server

Behaviour modules run on the client, for one player. A **server module** runs where the server runs: a dedicated server, or the world a single-player game hosts. It gets:
- server ticks;
- players joining, leaving and dying;
- chat;
- its own `/` commands.

It can:
- message players;
- run console commands;
- read blocks.

Like every module, it is a plain jar against `conductor.contract`, and the same jar runs on 1.16.1, 1.21.4, 1.21.11 and 26.3.

```java
public final class Hello implements ServerModule {
    @Override
    public List<ServerCommand> commands() {
        return List.of(ServerCommand.of("hello", "/hello: say hello", (server, call) -> "Hello, " + call.sender() + "!"));
    }

    @Override
    public void joined(ServerControl server, OnlinePlayer player) {
        server.tell(player.name(), "Welcome! This server runs Minecraft " + server.minecraftVersion());
    }
}
```

**Registering and installing.**
- List the class in `META-INF/services/conductor.contract.ServerModule`.
- Put the jar in the server's `conductor/modules/`. Clients do not need Conductor (SERVER-MODULES §6 says what was checked).

**Safety.** A module that throws is turned off until the game restarts, and the server keeps running.

The full guide is [SERVER-MODULES.md](SERVER-MODULES.md): every event and its order, commands, safety, and how each version runs it. Worked example: [`examples/server-greeter`](../examples/server-greeter). Live run on all four versions: [live-run-server-modules.md](live-run-server-modules.md).
