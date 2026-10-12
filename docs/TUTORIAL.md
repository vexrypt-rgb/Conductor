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

---

## 1. What Conductor is, in one minute

**Ostinato** is a Minecraft bot engine (based on Baritone): it finds paths, walks, mines, builds and fights.

**Conductor** sits on top of Ostinato and gives your code a **small, safe, version-independent way to use it**:

```
 your module  ──►  Conductor (the contract)  ──►  runtime adapter for your Minecraft version  ──►  Ostinato
```

- **Your module** only talks to Conductor. It never touches Baritone internals.
- **The contract** (`contract/`) is plain Java with no Minecraft code. It holds the rules.
- **The runtime adapter** (`runtime-ostinato/`) is the only part that knows Minecraft and Ostinato. There is one per Minecraft version (today **1.21.11** and **1.21.4**), built from the same shared code.

So you write a module once, and it builds for every supported Minecraft version.

What Conductor gives you today:

| You can... | With |
|---|---|
| Ask what is available | `discover()` |
| Read what the bot engine is doing | `observe(...)`, `revalidate(...)` |
| Send the bot somewhere | `requestTravel(...)` |
| Stop it, and know when it has *really* stopped | `cancel(...)`, `operationStatus(...)` |
| Know whether the bot is free to use | `resourceStatus()` |
| See why anything happened | the diagnostic sink |

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
- **An Ostinato checkout for each Minecraft version you build for**, with its classes compiled:
  - `main` branch, for 1.21.11: put it next to Conductor as `../Ostinato`
  - `1.21.4` branch: put it next to Conductor as `../Ostinato-1.21.4`, or pass `-PostinatoDir_1_21_4=/path`

```sh
# 1. compile Ostinato's classes (once per checkout)
(cd ../Ostinato && ./gradlew compileJava compileApiJava)
(cd ../Ostinato-1.21.4 && ./gradlew compileJava compileApiJava)

# 2. build and test Conductor
./gradlew test                         # contract tests, plus the adapter tests on both Minecraft versions
./gradlew :example-walk-and-stop-1.21.11:build
```

Project names always end in the Minecraft version: `runtime-ostinato-1.21.4`, `example-walk-and-stop-1.21.4`, and so on. The mod jar you install is the one **without** `-dev` in `<project>/<mc>/build/libs/`.

---

## 4. Your first module, step by step

We'll build the example that ships in [`examples/walk-and-stop`](../examples/walk-and-stop). It waits until the bot is free, walks 20 blocks east, cancels after 2 seconds, and waits until the stop is confirmed.

### Step 1: create the folders

```
examples/my-module/
  common/src/main/java/...            ← your code (shared by every Minecraft version)
  common/src/main/resources/fabric.mod.json
  1.21.11/build.gradle                ← tiny, one per Minecraft version
  1.21.4/build.gradle
```

Each per-version `build.gradle` is three lines:

```groovy
ext.mc = '1.21.11'
ext.commonDir = 'examples/my-module/common'
apply from: rootProject.file('gradle/fabric-module.gradle')
```

Register the projects in `settings.gradle`, **inside** the existing `['1.21.11', '1.21.4'].each { mc -> ... }` loop, next to the example's two lines:

```groovy
    include "my-module-${mc}"
    project(":my-module-${mc}").projectDir = file("examples/my-module/${mc}")
```

### Step 2: describe the mod (`fabric.mod.json`)

`${mc}` and `${version}` are filled in for you at build time:

```json
{
  "schemaVersion": 1,
  "id": "my_module",
  "version": "${version}",
  "name": "My module",
  "environment": "client",
  "entrypoints": { "client": ["my.pkg.MyModuleMod"] },
  "depends": { "fabricloader": "*", "minecraft": "${mc}", "baritone": "*" }
}
```

### Step 3: start your module (the entrypoint)

This is the **only** Ostinato-specific line in your module:

```java
public final class WalkAndStopMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // How long the bot must look idle before Conductor calls it free. No default on purpose;
        // 20 ticks (1 s) covered every stop seen in testing.
        ReleasePolicy policy = new ReleasePolicy(OptionalLong.of(20), OptionalLong.of(1200));
        ConductorBootstrap.start(new WalkAndStop(), policy);
    }
}
```

`ConductorBootstrap` waits until you are in a world, installs Conductor, and then calls your module. **You never have to think about threads:** your module's methods are always called on the game thread.

### Step 4: write the module

A module implements `ConductorModule`, which has two methods:

```java
public interface ConductorModule {
    void onReady(Conductor conductor);              // once, when you're in a world
    default void onTick(Conductor conductor) {}     // every game tick (20 per second)
}
```

Here is the heart of the example (full file: [`WalkAndStop.java`](../examples/walk-and-stop/common/src/main/java/conductor/example/WalkAndStop.java)):

```java
@Override
public void onTick(Conductor conductor) {
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
                LOG.info("stop confirmed after {} ticks", ticksInStep);
                next(Step.DONE);
            }
        }
        case DONE -> { }
    }
}

private void walk(Conductor conductor) {
    BlockPos here = Minecraft.getInstance().player.blockPosition();
    OperationResult result = conductor.requestTravel(NavigationGoal.getToBlock(here.getX() + 20, here.getY(), here.getZ()));
    if (!result.isAccepted()) {
        LOG.info("travel refused: {} ({})", result.rejection().orElseThrow(), result.detail());
        return;                                                       // try again next tick
    }
    operation = result.status().orElseThrow().id();
    next(Step.WALKING);
}
```

The pattern to remember is **a small state machine driven by `onTick`**. Never wait or sleep inside a module: just check again on the next tick.

### Step 5: build and run

```sh
./gradlew :my-module-1.21.11:build
```

Copy the non-`-dev` jar into your Minecraft `mods/` folder, next to Ostinato. Today **only one Conductor module can run per game** (see §10).

---

## 5. Everything Conductor can do

All of these are methods on `Conductor`. Call them only from `onReady` / `onTick`, which is the game thread.

### 5.1 `discover()`: what is available

Returns the contract version, the current context, and a list of **capabilities**:

| Capability id | Availability | Operations | Observation kinds |
|---|---|---|---|
| `navigation` | `AVAILABLE` on Ostinato | `requestTravel`, `cancel`, `operationStatus` | the 6 engine kinds below |
| `navigation.secondary-backend` | `AVAILABLE` only if Tungsten is installed | none | the 3 `SECONDARY_BACKEND_*` kinds |

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

To keep the sink, pass your own: `ConductorBootstrap.start(module, policy, mySink)`.

### 5.8 Interference: what Conductor can and can't see

| Something else... | Class | What Conductor does |
|---|---|---|
| A second request from your own module while one is running | **BLOCKED** | Refused (`CONFLICTING_OPERATION`) |
| A second Conductor installation | **BLOCKED** | Refused |
| Another Ostinato process steering (mining, drowning protection, ...) | **DETECTED** | Bot marked busy; your request is refused, or your running operation becomes `UNCERTAIN` |
| Tungsten installed | **DETECTED** only when it reports `true`, otherwise **UNDETECTABLE** | Bot never counted as free |
| A human pressing keys | **UNDETECTABLE** | Not claimed |

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

---

## 7. Testing your module

**Unit tests without Minecraft.** The contract is plain Java, so you can test module logic against a fake runtime: implement `RuntimeIntegration` and `NavigationPort` with whatever values you want. See `contract/src/test/java/conductor/contract/FakeRuntime.java` and `FakePort.java` for small, complete examples. Build a `ContractService` around them, call `tick()` to simulate game ticks, and drive your module's `onTick` yourself.

**Live tests in a real game.** `scripts/live-run.sh` starts a local server and Ostinato's client under a virtual display, with the live harness mod. It records every reading, plus the player's real position and speed, every tick:

```sh
MC=1.21.4 OSTINATO_DIR=../Ostinato-1.21.4 scripts/live-run.sh /tmp/run   # needs /tmp/run/server/server.jar (vanilla, same version)
python3 scripts/analyze-live.py /tmp/run/harness.jsonl
```

Read the script header for the details.

---

## 8. Supporting another Minecraft version

Thanks to the shared code, a new version is mostly configuration:

1. **Check the Ostinato branch.** It must have the same API Conductor uses: `IMovementEngine`, `IPathingBehavior`, `IPathingControlManager`, `IInputOverrideHandler`, the event bus. `git diff` those files against `main`.
2. **Add the version to `mcTargets`** in `build.gradle`, with its Parchment and Fabric Loader versions (copy them from that branch's build files) and where its Ostinato checkout lives.
3. **Create `runtime-ostinato/<mc>/`** with a 2-line `build.gradle` and its own `MinecraftGameAccess.java`. That class holds every Minecraft call that can differ between versions (thread check, dimension name, connection). For 1.21.4 versus 1.21.11 the only difference was `dimension().location()` versus `dimension().identifier()`.
4. **Create `live-harness/<mc>/`** with a `build.gradle` and `Compat.java`. Do the same for each module (only `build.gradle` is needed if your module has no version-specific code).
5. **Add the version** to the list in `settings.gradle`.
6. **Run `./gradlew test`**: the same adapter tests now run on the new version too. Then do a live run with `MC=<version>`.

---

## 9. Adding a new capability

Navigation is the only capability today. Combat (PvP/PvE, including the `autotune` branch), mining, building, farming, elytra and others are listed in [`deferred-candidates.md`](deferred-candidates.md). They are **not available yet**, and each must pass a gate first. This is the process navigation went through; follow it for any new capability:

1. **Audit** how the feature starts, stops and steers, labelling each fact Verified, Reported or Unknown (example: [`stage1-audit.md`](stage1-audit.md)). Pay special attention to: does "stop" really stop? Which keys and processes does it use?
2. **Observe before you act.** Add read-only `ObservationKind`s for the feature's signals and test them live with the harness.
3. **Delegate to the existing engine.** Add a port like `NavigationPort` that calls Ostinato's existing API. Never re-implement or change Ostinato.
4. **Prove stops with evidence.** Use the live harness to find which signals really track control (for navigation: path segment plus forced keys). Release only on those.
5. **Classify interference** (BLOCKED / DETECTED / UNDETECTABLE / NOT_APPLICABLE).

Hard limits, by design: no universal scheduler, no automatic preemption between features, no changes to Ostinato's process scheduling, and no new stop mechanisms that Ostinato doesn't already have. Combat in particular shares the movement controls, so it needs the project owner's go-ahead.

---

## 10. Limits you should know about

Conductor is young. Here is everything it **cannot** do today, so you can plan around it. The long-term goal of hosting any kind of client mod, and what it would take, is in [VISION-client-mod-host.md](VISION-client-mod-host.md).

**What it can do**
- **Travel is the only action.** No mining, building, farming, combat, elytra or following yet (see [deferred-candidates.md](deferred-candidates.md)).
- **No rendering, HUD, chat, keybinds, inventory, packets, camera or GUI.** A module can draw nothing and send nothing through Conductor. It can still use Minecraft directly, but then Conductor's safety rules don't cover that code.
- **No arrival signal.** A trip that ends on its own is `UNCERTAIN` ("outcome not established"). Check the player's position yourself.

**How many modules, and where**
- **One module per game.** Conductor serves a single consumer; a second install is refused. Each module jar currently bundles its own copy of Conductor, so two modules would also clash.
- **Ostinato only.** Conductor needs Ostinato, a Baritone fork. It is not a general mod loader.
- **Fabric client only.** Forge and NeoForge are untested.
- **Minecraft 1.21.11 and 1.21.4 only.** Ostinato's 26.3 branch (Java 25) has no adapter yet. The 1.16.1 branch's API differs too much to reuse as is.
- **No hot reload.** Modules load at game start and cannot be unloaded.

**How the engine behaves**
- **Stops take as long as Ostinato takes.** After `cancel()` the bot usually finishes its current stretch of path. Live, that was up to 344 ticks (17 s) and 122 blocks on a long path. Conductor reports this honestly but cannot make it faster.
- **`cancel()` stops more than your operation.** Ostinato's cancel also stops other processes, such as mining.
- **Something else can take the bot at any time.** For example, Ostinato's drowning protection (`AirProcess`) took over about 10 s after a stop underwater. Conductor detects this and marks the bot busy, but cannot prevent it.
- **Tungsten blocks travel.** With Tungsten installed and preferred, travel requests are refused, because its stop can't be proven.
- **Human input is invisible.** Keys pressed by a person are not seen by Conductor.

**Building and testing**
- **Building needs local Ostinato checkouts** with compiled classes. There are no published artifacts yet.
- **Tested on:** flat local test worlds (stairs, trench, water, long paths, mining), the Baritone backend, about 25 live cancel trials, and one full live run per Minecraft version.
- **Not tested:** elytra, boats, heavy parkour, Tungsten, real servers (latency, anticheat), long play sessions.
- **The live harness hangs on loading errors.** If a mod fails to load, Fabric opens an error window that nobody can close in a headless run. The run then waits for the script's timeout instead of failing at once.
