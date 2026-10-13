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

---

## 1. What Conductor is, in one minute

**Ostinato** is a Minecraft bot engine (based on Baritone): it finds paths, walks, mines, builds and fights.

**Conductor** sits on top of Ostinato and gives your code a **small, safe, version-independent way to use it**:

```
 your module (one jar, any version)  ──►  Conductor contract  ──►  Conductor mod for this Minecraft version  ──►  Ostinato
```

- **Your module** only talks to the Conductor contract. It never imports Minecraft, mod-loader (Fabric, Forge, NeoForge) or Baritone classes. It isn't even a Fabric mod: it's a plain jar ([LOADER-INDEPENDENCE.md](LOADER-INDEPENDENCE.md)).
- **The contract** (`contract/`) is plain Java with no Minecraft code. It holds the rules.
- **The Conductor mod** (`conductor-mod/`, built on `runtime-ostinato/`) is the only part that knows Minecraft and Ostinato. There is one per Minecraft version (today **1.21.11** and **1.21.4**), built from the same shared code. It finds your module and runs it.

So **your module is one jar that works on every supported Minecraft version**. You install the Conductor mod that matches your game, plus your module. The same example jar was run live on both 1.21.11 and 1.21.4.

What Conductor gives you today:

| You can... | With |
|---|---|
| Ask what is available | `discover()` |
| Read what the bot engine is doing | `observe(...)`, `revalidate(...)` |
| Send the bot somewhere | `requestTravel(...)` |
| Make it fight players or mobs (Ostinato's own combat) | `requestCombat(...)` (§5.9) |
| Read the player, inventory, effects and nearby entities | `observe(PLAYER_STATUS / PLAYER_MOTION / INVENTORY_SLOTS / ACTIVE_EFFECTS / NEARBY_ENTITIES ...)` (§5.10) |
| Read blocks, or find blocks around the player | `blockAt(...)`, `findBlocks(...)` (§5.10) |
| Switch hotbar slot, turn, attack, use an item, chat | `selectHotbar`, `look`, `attack`, `useItem`, `sendChat` (§5.10) |
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
- **An Ostinato checkout for each Minecraft version you build the Conductor mod for**, with its classes compiled:
  - `main` branch, for 1.21.11: put it next to Conductor as `../Ostinato`
  - `1.21.4` branch: put it next to Conductor as `../Ostinato-1.21.4`, or pass `-PostinatoDir_1_21_4=/path`

  Writing a **module** needs none of this: a module only needs the `contract` project.

```sh
# 1. compile Ostinato's classes (once per checkout)
(cd ../Ostinato && ./gradlew compileJava compileApiJava)
(cd ../Ostinato-1.21.4 && ./gradlew compileJava compileApiJava)

# 2. build and test Conductor
./gradlew test                                # contract tests, plus the adapter tests on both Minecraft versions
./gradlew :conductor-mod-1.21.11:build :conductor-mod-1.21.4:build
./gradlew :example-walk-and-stop:build        # a module: one jar for every version
```

What to install:

| Jar | Where it goes | Where it's built | Per version? |
|---|---|---|---|
| Ostinato | `mods/` | Ostinato's own build | yes |
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

1. **Check the Ostinato branch.** It must have the same API Conductor uses: `IMovementEngine`, `IPathingBehavior`, `IPathingControlManager`, `IInputOverrideHandler`, the event bus. `git diff` those files against `main`.
2. **Add the version to `mcTargets`** in `build.gradle`, with its Parchment and Fabric Loader versions (copy them from that branch's build files) and where its Ostinato checkout lives.
3. **Create `runtime-ostinato/<mc>/`** with a 2-line `build.gradle` and its own `MinecraftGameAccess.java`. That class holds every Minecraft call that can differ between versions (thread check, dimension name, connection). For 1.21.4 versus 1.21.11 the only difference was `dimension().location()` versus `dimension().identifier()`.
4. **Create `conductor-mod/<mc>/` and `live-harness/<mc>/`**, each with a 3-line `build.gradle` (copy an existing one and change `mc`). The harness also needs its `Compat.java`.
5. **Add the version** to the list in `settings.gradle`. Existing modules need **nothing**: they keep working, because they never touch Minecraft.
6. **Run `./gradlew test`**: the same adapter tests now run on the new version too. Then do a live run with `MC=<version>`.

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
- **Ostinato only.** Conductor needs Ostinato, a Baritone fork. It is not a general mod loader.
- **Boots on Fabric only, today.** Modules don't depend on any loader. Conductor's only Fabric code is a 40-line boot shim, but Ostinato is a Fabric mod, so the game runs Fabric anyway. Forge/NeoForge shims and a loader-free boot are planned: [LOADER-INDEPENDENCE.md](LOADER-INDEPENDENCE.md).
- **Modules in `conductor/modules/` are not transformed.** They get no Mixin and no remapping, which is exactly right for contract modules. Native modules that need Minecraft classes ship inside their own mod and are found on the game's classpath through the same service file.
- **Minecraft 1.21.11 and 1.21.4 only.** Ostinato's 26.3 branch (Java 25) has no adapter yet. The 1.16.1 branch's API differs too much to reuse as is.
- **No hot reload.** Modules load at game start. Disabling a module stops it logically; its code stays loaded.
- **`content()` covers built-in registries only.** Items, blocks, entity types and effects work. Data-driven things (enchantments, biomes, recipes, damage types) are UNKNOWN, so a feature that needs them stays off.
- **Engine features mean "the code is in this build".** PRESENT for `ostinato:combat/spear` says the Ostinato build has spear code, not that a combat capability exists or is running.

**How the engine behaves**
- **Stops take as long as Ostinato takes.** After `cancel()` the bot usually finishes its current stretch of path. Live, that was up to 344 ticks (17 s) and 122 blocks on a long path. Conductor reports this honestly but cannot make it faster.
- **`cancel()` stops more than your operation.** Ostinato's cancel also stops other processes, such as mining.
- **Something else can take the bot at any time.** For example, Ostinato's drowning protection (`AirProcess`) took over about 10 s after a stop underwater. Conductor detects this and marks the bot busy, but cannot prevent it.
- **Tungsten blocks travel.** With Tungsten installed and preferred, travel requests are refused, because its stop can't be proven.
- **Human input is invisible.** Keys pressed by a person are not seen by Conductor.

**Building and testing**
- **Building the Conductor mod needs local Ostinato checkouts** with compiled classes. There are no published artifacts yet. Modules need only the `contract` project.
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

