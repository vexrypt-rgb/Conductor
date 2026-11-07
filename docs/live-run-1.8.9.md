# Live run: Minecraft 1.8.9 (Legacy Fabric): partial, not verified in-world

Status: the 1.8.9 runtime and Conductor mod **build, remap and pass their unit tests**. A production-style live run got
as far as Conductor starting on the client, but **no client has joined a server yet**, so behaviour is unverified.

## What ran

- Client: `scripts/prod-run.sh` with `MC=1.8.9`, loader 0.19.5, the release mod jar
  `conductor-mod-1.8.9-0.6.0.jar`, and the `example-block-highlighter` and `example-player-actions` modules.
- Server: vanilla 1.8.9 `server.jar` (downloaded from Mojang's piston metadata), offline mode, flat world. It reached
  `Done (0.763s)!`.

Evidence from the client log (Java 21):

- `Loading Minecraft 1.8.9 with Fabric Loader 0.19.5`, with `conductor 0.6.0` loaded.
- `LWJGL Version: 2.9.4`, and the textures atlas was created (the window and GL context came up).
- `Conductor starting 2 module(s)`: `example-block-highlighter-0.6.0` and `example-player-actions-0.6.0`.
- The Mixin config loaded when run on Java 21.

## What blocked the join

1. **Netty's epoll transport on Java 21.** The client logs `Unable to access address of buffer` millions of times in
   `io.netty.channel.epoll.Native.read` once it connects. `--add-opens java.base/java.nio=ALL-UNNAMED` and
   `-Dio.netty.transport.noNative=true` did not stop it. Nobody joined.
2. **Java 8 does not load the build.** Running the client on a Java 8 JDK (what 1.8.9 players use) fails with
   `The requested compatibility level JAVA_21 could not be set`. The contract and the mod are compiled with
   `options.release = 21`, so the jar is Java 21 bytecode. A Java 8 player cannot run it.

Item 2 is a design question, not a test fix: 1.8.9 needs Java 8 bytecode for the shared contract and mod, which means
Java 8 compatible source for `contract/`, `runtime/1.8.9` and `conductor-mod/1.8.9`. Until that is decided, this
build is for a Java 21 launcher only.

## Harness change

`scripts/prod-run.sh` now joins 1.8 with a direct `--server` connect, like 1.10 to 1.12. Quick play does not exist
before 1.20, and the `legacy-join` helper uses intermediary names that are not checked for 1.8.9. It also checks for
`xrandr` before 1.8 runs, since 1.8.9 uses LWJGL 2.

## Not covered

- A client joining a server, and the behaviour modules in the world.
- The server modules (`server-greeter`) on 1.8.9.
- Behaviour checks (inventory, effects, blocks, entities) against the 1.8.9 API.
- Unit tests cover the runtime as built (`:runtime-1.8.9:test`, 44 tests, 0 failures). They do not exercise the game.

## Known noise (not failures)

- `Can't find the resource index file: .../indexes/1.8.json` (asset index not in the local cache).
- OpenAL and ALSA errors (no audio device under Xvfb). The game keeps running.
