# Minecraft beta 1.7.3 (Babric): status

Status: **not built into Conductor yet.** A build probe resolved the mappings; the runtime does not compile yet.

## What works

- Mojang metadata for `b1.7.3` ships a client jar only (no server jar), and it targets Java 8 (`jre-legacy`).
- Babric's loader is on `maven.glass-launcher.net/babric/babric/fabric-loader`; `0.13.3-babric.4` is the 0.13 line for this version.
- Unimined's `babric { }` DSL exists, and so does `feather(int)`. Feather (Ornithe's named mappings) needs
  `side 'client'` and `calamus()` as the intermediary. `babricIntermediary()` alone does not resolve with Feather.
- With those settings, Gradle resolves the mapping chain for `runtime-b1.7.3`.

## What blocks it

1. **Java 8 bytecode.** The beta client runs only on Java 8. `contract/`, `runtime/common` and the mod are all Java 21
   bytecode (root `build.gradle` sets `options.release = 21`). This is the same decision as 1.8.9.
2. **No log4j.** Beta 1.7.3 does not bundle log4j, but `runtime/common` (`ConductorRuntime`, `Hooks`,
   `HookEventFeed`, `ServerModules`, ...) logs through `org.apache.logging.log4j`. Options: bundle log4j-api in the
   mod, or move the common code to a logging facade the runtime provides per version. The second touches shared code.
3. **Fabric loader symbols.** `ConductorBootstrap` and `ConductorRuntime` use `FabricLoader`; the runtime compile
   classpath does not include the Babric loader yet (add it as `compileOnly`).
4. **No server jar in the metadata.** A live server run needs a beta 1.7.3 server jar from outside Mojang's
   metadata. Until one is verified, the live evidence for this version is client-only.

## Probe patch

The probe changes (mapping and loader wiring in `gradle/`, `settings.gradle`, `build.gradle`) are not committed,
so the pushed build stays green. They are kept outside the repo for reference.
