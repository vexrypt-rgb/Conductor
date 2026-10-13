# Installing Conductor

This page covers the **0.6.0 beta** release: which file to download, and how to install it on each Minecraft version Conductor supports. Each step says how it was checked:
- **Verified:** done in a test run, with logs.
- **Reported:** the normal Fabric or Ornithe procedure, which these tests did not go through. For example, the official launcher was never clicked through.

All tests ran on Linux. Windows and macOS were not tried.

## 1. Which file you need

Conductor is one Fabric mod per Minecraft version, plus any number of **modules**. A module is a plain jar, and the same module jar works on every version.

| Minecraft | Conductor mod | Mod loader | Java | What it can run |
|---|---|---|---|---|
| **26.3** | `conductor-<v>-mc26.3.jar` | Fabric Loader | **25** or newer | behaviour, content and server modules; custom mobs |
| **1.21.11** | `conductor-<v>-mc1.21.11.jar` | Fabric Loader | **21** or newer | the same, plus Ostinato travel and combat if Ostinato is installed |
| **1.21.4** | `conductor-<v>-mc1.21.4.jar` | Fabric Loader | **21** or newer | the same as 1.21.11 |
| **1.16.1** | `conductor-<v>-mc1.16.1.jar` | Fabric Loader | **21** or newer (the launcher picks Java 8: change it, §3) | behaviour, content and server modules; custom mobs; no Ostinato |
| **1.0.0** | `conductor-<v>-mc1.0.0.jar` | Fabric Loader **through Ornithe** (§4) | **21** or newer (the launcher picks Java 8: change it) | behaviour and server modules only; no content modules, no Ostinato |

Every version was tested with **Fabric Loader 0.19.5**. 1.21.4 was also tested with 0.18.5. The Java versions tested were:
- Java 21 and 25 on 1.16.1, 1.21.4 and 1.21.11;
- Java 25 on 26.3;
- Java 21 on 1.0.0.

Conductor needs **no other mod**: Fabric API is not required.

The mod checks its Java version at start-up. On too old a Java, Fabric Loader stops and says so: "Mod 'Conductor' requires version 21 or later of … (java)". It does not crash with a class error. This was verified on a 1.0.0 server with a test copy of the mod that asks for Java 99.

## 2. Fabric versions (26.3, 1.21.11, 1.21.4, 1.16.1)

### Client

1. **Install Fabric Loader** for your Minecraft version with the Fabric installer (<https://fabricmc.net/use/installer/>). Choose loader 0.19.5. *(Reported: standard Fabric.)*
2. Start that Fabric profile once, then close the game. This creates the `mods` folder in your game directory (`.minecraft`, or the instance folder in Prism or MultiMC).
3. **Put the Conductor jar for your version in `mods/`.** Use only one Conductor jar, the one for your version.
4. **1.16.1 only:** Minecraft's launcher runs 1.16.1 on Java 8, but Conductor needs Java 21. Open the profile's settings (*Installations → Edit → More options → Java executable*) and point it at a Java 21 or newer `java`/`javaw`. Prism and MultiMC have the same setting under the instance's *Settings → Java*.
5. Start the game. The log (`logs/latest.log`) should show `Conductor loaded; no modules in …/conductor/modules` the first time.

Steps 3 to 5 are **Verified** on every version in production Fabric launches: a real Fabric Loader start with the jars in `mods/`, as a player has them.

### Dedicated server

1. Get Fabric's server launcher for your version with loader 0.19.5 from <https://fabricmc.net/use/server/>, or let the Fabric installer's *Server* tab set the server up.
2. Put the Conductor jar for your version in the server's `mods/` folder.
3. Start the server with Java 21 or newer (Java 25 for 26.3), for example `java -Xmx2G -jar fabric-server-launch.jar nogui`. Use the launcher's real file name.
4. The log should show `Conductor server modules found: […]` once you have added server modules (§5).

Conductor on a Fabric dedicated server is **Verified** on all four versions. The server's `mods/` folder held the same jar as the client's.

Players do **not** need Conductor to join a server that runs server modules. This was checked with a client that had no mods, on 1.16.1 and 1.21.11. **Content modules** (new items and blocks) are the exception: they must be installed on the server and on every client ([TUTORIAL §13.4](TUTORIAL.md#134-install)).

## 3. Java for 1.16.1 and 1.0.0

These two versions are older than Conductor's Java requirement. Both were made for Java 8, and Mojang's launcher still picks Java 8 for them. Conductor is built for Java 21, so these profiles must run on Java 21 or newer:
- **Official launcher:** *Installations → (the profile) → Edit → More options → Java executable*. Enter the path to a Java 21+ `javaw.exe` (Windows) or `java` (macOS and Linux). Any Java 21 build works, for example Eclipse Temurin.
- **Prism Launcher and MultiMC:** *Edit instance → Settings → Java → Java installation*.

Both versions ran on Java 21 in the tests (**Verified**, Linux). Running them on Java 21 on Windows or macOS was not tried.

## 4. Minecraft 1.0.0 (through Ornithe)

Minecraft 1.0.0 has no official mod loader. [Ornithe](https://ornithemc.net) brings Fabric Loader to it, together with mappings for old versions. Conductor's 1.0.0 jar is built against Ornithe's **generation 2** mappings (Calamus gen2), the installer's default.

### Client

1. Download the **Ornithe installer** from <https://ornithemc.net>. 0.16.0 was the version checked.
2. In the installer:
   - choose *Minecraft 1.0.0*;
   - loader type *Fabric*, loader version *0.19.5*;
   - intermediary generation *2* (the default);
   - and either the official launcher or MultiMC (*MultiMC* also works for Prism Launcher).

   With the official launcher this creates the installation **"Ornithe Gen2 Fabric 1.0.0"**, version `fabric-loader-0.19.5-1.0.0-ornithe-gen2`. *(Verified: the installer's command-line mode, `install client 1.0.0 Official Fabric 0.19.5`, created exactly this.)*
3. **Set that installation's Java to Java 21 or newer** (§3). Ornithe's 1.0.0 profile asks for Java 8 (`jre-legacy`), which cannot load Conductor.
4. Start it once so the game folder has a `mods/` folder. Put `conductor-<v>-mc1.0.0.jar` in it.
5. Start the game.

Ornithe's profile uses the same pieces as the 1.0.0 test runs: Fabric Loader 0.19.5, Calamus gen2 mappings, and LWJGL `2.9.4+legacyfabric.15`. Those runs started the game from that classpath directly, not from the launcher. The behaviour modules are **Verified** in a 1.0.0 client joining a 1.0.0 server ([live-run-1.0.0.md](live-run-1.0.0.md)). Starting through the official launcher is **Reported**.

**Single-player on 1.0.0 was not tried** (Unknown). In 1.0 single-player there is no server, and so there is no chat either. Conductor's `sendChat` reports this rather than sending anything.

### Dedicated server

1. Run the Ornithe installer's *server* install for 1.0.0, Fabric, 0.19.5, and tick "download server". From the command line:
   ```
   java -jar ornithe-installer-0.16.0.jar install server 1.0.0 Fabric 0.19.5 --install-dir=<server folder> --download-server
   ```
   This gives `fabric-server-launch.jar`, `server.jar` and `libraries/`.
2. Put `conductor-<v>-mc1.0.0.jar` in `<server folder>/mods/`, and server modules in `<server folder>/conductor/modules/`.
3. Start it with Java 21 or newer: `java -Xmx1G -jar fabric-server-launch.jar nogui`.

**Verified** with exactly these steps on Linux, using this release's 1.0.0 jar and the `server-greeter` module:
- the log showed `Conductor server modules started on a dedicated server: [example-server-greeter-0.6.0]`;
- the module's console command `greet` answered `Hello, CONSOLE!`.

The two `log4j ... ERROR` lines at the top of that server's log come from Ornithe's logging setup. They also appear with no mods installed.

## 5. Modules

Modules go in **`conductor/modules/`** in the game folder (client) or the server folder (server). The folder is created on first start. A module is a plain jar: **the same file works on every version**, so do not look for a per-version module.

The release includes Conductor's example modules:

| Module | What it does | Needs | Works on |
|---|---|---|---|
| `example-player-actions` | reads the player, inventory, nearby entities; tests hotbar, look and chat actions | nothing | all five (attack and use-item were not shown live on 1.0.0) |
| `example-block-highlighter` | draws boxes and lines to a chosen block; chat commands `+highlight`, `+set`, `+settings` | nothing | all five |
| `example-server-greeter` | **server module**: welcomes players, counts deaths, `/greet`, `/whereami`, `/online`, `/deaths` | install on the **server** | all five (dedicated servers; single-player worlds on 1.21.4, 1.21.11 and 26.3) |
| `example-ruby-content` | **content module**: ruby items and blocks, recipes, a dimension, the Ruby Golem boss | install on the server **and** every client | 26.3, 1.21.11, 1.21.4, 1.16.1 (not 1.0.0; 1.16.1 has no dimension) |
| `example-walk-and-stop` | walks 20 blocks east and stops | Ostinato (§6) | 1.21.11, 1.21.4 |
| `example-agnostic-combat` | fights through the engine when it can | Ostinato for combat | 1.21.11, 1.21.4 |
| `example-engine-commands` | runs Ostinato commands under Conductor's movement lease | Ostinato module edition (§6) | 1.21.11 |

`conductor-contract-<v>.jar` is for **module developers**: compile against it (`compileOnly`), and do not install it. Start with [TUTORIAL.md](TUTORIAL.md).

Module settings are saved in `config/conductor/<module>.properties`. A module's name is its jar's file name without `.jar`, so keep the file names as downloaded.

## 6. Ostinato (optional)

[Ostinato](https://github.com/vexrypt-rgb/Ostinato) is a movement engine (a Baritone fork). With it in `mods/`, Conductor adds travel and combat on **1.21.11 and 1.21.4**. Without it, Conductor logs `Conductor engine: none` and everything else works. This was verified on both versions.

What was run with Ostinato in this release:
- **1.21.11:** run with Ostinato 1.21.0's *module edition*. That build comes from the Ostinato branch `conductor-module` and is not yet in a published Ostinato release.
- **1.21.4:** the engine layer is unit-tested against Ostinato's 1.21.4 code but was not run live with it.
- **Other combinations:** Unknown, including the published Ostinato releases up to v1.20.0 and the plain (non-module) builds.

## 7. Checking it worked

| You should see in the log | Meaning |
|---|---|
| `Conductor starting N module(s) from … : [names]` | the client found your modules |
| `Conductor loaded; no modules in …` | Conductor runs; `conductor/modules/` is empty |
| `Conductor engine: none` / `Conductor engine: Ostinato` | without / with Ostinato |
| `Conductor server modules started on a dedicated server: [names]` | the server runs your server modules |
| `Conductor server module X threw in …; it is off until the game restarts` | a module failed; Conductor switched it off and the game keeps running |

To remove Conductor, delete its jar from `mods/`. Content modules add items and blocks to worlds. A world that used them will lose those items and blocks if it is opened without the module.

## 8. Beta limits

- One Conductor jar per game. Conductor refuses to start a second copy.
- Fabric only. Quilt, Forge and NeoForge were not tried.
- Content modules need the same modules on the server and on every client. If they differ, Minecraft's own errors appear, not a clear "module X is missing".
- On 1.0.0, attack and use-item were not shown live, and single-player was not tried.
- The jars report version `0.6.0`. The release tag (for example `v0.6.0-beta.1`) marks this build as a beta of 0.6.0.
- More limits: [HARD-EDGES.md](HARD-EDGES.md), and the "not yet" sections of [TUTORIAL.md](TUTORIAL.md).
