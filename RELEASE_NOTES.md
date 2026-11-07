**Beta.** Conductor for five Minecraft versions in one release: the first Conductor release on GitHub. Every jar here was built, and its unit tests run, by this release's workflow; the table below says what was run live in a game. Full install guide: **[docs/INSTALL.md](https://github.com/vexrypt-rgb/Conductor/blob/v0.6.0-beta.1/docs/INSTALL.md)**.

## Which file do I download?

Download **one** Conductor jar, the one for your Minecraft version, and put it in `mods/`. Fabric API is not needed.

| Minecraft | File | Loader | Java |
|---|---|---|---|
| 26.3 | `conductor-0.6.0-beta.1-mc26.3.jar` | Fabric Loader 0.19.5 | 25+ |
| 1.21.11 | `conductor-0.6.0-beta.1-mc1.21.11.jar` | Fabric Loader 0.19.5 | 21+ |
| 1.21.4 | `conductor-0.6.0-beta.1-mc1.21.4.jar` | Fabric Loader 0.19.5 | 21+ |
| 1.16.1 | `conductor-0.6.0-beta.1-mc1.16.1.jar` | Fabric Loader 0.19.5 | 21+ (change the profile's Java from 8) |
| 1.0.0 | `conductor-0.6.0-beta.1-mc1.0.0.jar` | Fabric Loader 0.19.5 **through [Ornithe](https://ornithemc.net)** | 21+ (change the profile's Java from 8) |

Modules (`example-*.jar`) go in `conductor/modules/` in the game or server folder. **The same module jar works on every version.** `conductor-contract-0.6.0.jar` is for module developers: don't install it.

## Install in short

**26.3, 1.21.11, 1.21.4, 1.16.1 (Fabric):**
1. Install Fabric Loader 0.19.5 for your version (<https://fabricmc.net/use/installer/>; servers: <https://fabricmc.net/use/server/>).
2. Put the matching Conductor jar in `mods/`.
3. 1.16.1 only: set the profile's Java to 21 or newer (*Installations → Edit → More options → Java executable*).
4. Start the game. `logs/latest.log` shows `Conductor loaded; no modules in …`.

**1.0.0 (Ornithe):**
1. Run the Ornithe installer (0.16.0 checked): Minecraft 1.0.0, Fabric, loader 0.19.5, generation 2. This makes the profile *Ornithe Gen2 Fabric 1.0.0*.
2. Set that profile's Java to 21 or newer.
3. Put `conductor-0.6.0-beta.1-mc1.0.0.jar` in `mods/`.
4. Server: `java -jar ornithe-installer-0.16.0.jar install server 1.0.0 Fabric 0.19.5 --install-dir=<folder> --download-server`, add the jar to `<folder>/mods/`, then `java -jar fabric-server-launch.jar nogui`.

Step-by-step versions, Prism/MultiMC settings and log lines to check: [INSTALL.md](https://github.com/vexrypt-rgb/Conductor/blob/v0.6.0-beta.1/docs/INSTALL.md).

## What each version runs

| | Behaviour modules | Server modules | Content modules and custom mobs | Ostinato travel and combat |
|---|---|---|---|---|
| 26.3 | yes | yes | yes | no |
| 1.21.11 | yes | yes | yes | yes, with Ostinato's module edition |
| 1.21.4 | yes | yes | yes | unit-tested only |
| 1.16.1 | yes | yes | yes (no dimensions) | no |
| 1.0.0 | yes | yes (dedicated server) | no | no |

"yes" means run live on that version with these modules; the evidence is in the [live-run documents](https://github.com/vexrypt-rgb/Conductor/tree/v0.6.0-beta.1/docs).

## Highlights

- **Minecraft 1.0.0** through Ornithe: behaviour modules (player state, inventory, actions, overlays) and server modules, from the same jars as every other version ([live-run-1.0.0.md](https://github.com/vexrypt-rgb/Conductor/blob/v0.6.0-beta.1/docs/live-run-1.0.0.md)).
- **Server modules** on 1.16.1, 1.21.4, 1.21.11 and 26.3: joins, leaves, deaths, chat, `/` commands and console commands.
- Each mod jar declares the Java it needs. On too old a Java, Fabric Loader stops with a clear message instead of a class error.

## Known limits

- Beta: Linux was the only test platform. Windows and macOS were not tried.
- On 1.0.0, single-player was not tried, and `attack` and `useItem` were not shown live. Starting it through the official launcher is the standard Ornithe procedure but was not clicked through in testing.
- Content modules must be on the server and every client.
- The jars report version `0.6.0`. This tag marks them as a beta of 0.6.0.
- More: [HARD-EDGES.md](https://github.com/vexrypt-rgb/Conductor/blob/v0.6.0-beta.1/docs/HARD-EDGES.md).

Checksums: `SHA256SUMS.txt` (check with `sha256sum -c SHA256SUMS.txt`).
