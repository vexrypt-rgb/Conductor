# Live run: a fully custom boss on 1.16.1, 1.21.4, 1.21.11 and 26.3 (2026-10-13)

**Request (project owner):** "Should also be able to handle fully custom mobs and bosses".

**What this checks:** one module jar adds the Ruby Golem, a boss with its own model, texture, animations, AI, special attacks, boss bar, sounds and drops ([CUSTOM-MOBS.md](CUSTOM-MOBS.md)). Does that same jar give the same fight on four Minecraft versions, five years apart, with a dedicated server and a client?

**Answer:** yes, on all four (Verified, table below):
- the golem's stats;
- its fireballs and ground slams;
- the enrage at half health, with the summoned zombies;
- its chat lines, death and drops;
- the model, animations and boss bar on the client.

No Conductor error on any version, client or server. The only Conductor warning is the known 1.16.1 one: the module's dimension file needs 1.16.2 or later and is left out ([live-run-1.16.1.md](live-run-1.16.1.md)).

## Setup

- **Launch:** production Fabric launch (`scripts/prod-run.sh`), Fabric Loader 0.19.5 on Java 25.
  - A Fabric dedicated server and a client, each with the Conductor mod built from this commit for its version, and nothing else. On 1.16.1, the client also has the test-only `conductor-test-join` mod, which joins the server (1.16.1 has no quick-play option), as in [live-run-1.16.1.md](live-run-1.16.1.md).
  - Mesa llvmpipe software rendering under Xvfb; EGL on 26.3 ([live-run-26.3.md](live-run-26.3.md)).
- **Module:** `example-ruby-content` 0.6.0, the same jar on both sides and all four versions.
- **Fight script:** when the player joins, the server runs these commands:
  1. Normal difficulty, noon, mob spawning off, survival mode.
  2. Resistance IV, fire resistance and regeneration for the player, so the fight runs its course.
  3. Summon the golem 12 blocks away, face it, and read its health and five attributes.
  4. After 14 s, set its health to 90 of 200 (below half). This starts the enrage.
  5. 3 s later, read its health again and have every ruby zombie say `CHECK summoned ruby zombie`.
  6. 16 s later, kill the golem, then the zombies. Have every dropped item say `CHECK dropped item`.
  - The player is turned to face the golem a few times.
  - Screenshots are taken at 8, 13, 22 and 30 s.
- **Attribute names:** 1.16.1's attribute ids carry `generic.` (`minecraft:generic.max_health`), and its game rule is `doMobSpawning`. These are the only differences between the four scripts.
- **Evidence:** the logs only, in `docs/evidence/module-runs/custom-mobs-golem-{client,server}-<version>.log.gz`.
  - The container's proxy line was removed and the scratch path replaced with `<scratch>`.
  - The golem's lines are in the server log (`[ruby:ruby_golem] ...`, from `mob.log`). The chat lines are in the client log.
- **Screenshots** were looked at but not committed: they also show Mojang's HUD and terrain. What they show is described below.

## Results

| Check | 1.16.1 | 1.21.4 | 1.21.11 | 26.3 |
|---|---|---|---|---|
| `registered 7 block(s), 9 item(s) and 4 mob(s)`, both sides; client `renderers for 4 mob(s)` | yes | yes | yes | yes |
| Health 200; max health 200, armor 8, knockback resistance 1, attack damage 12 | yes | yes | yes | yes |
| Follow range 32 plus vanilla's random spawn bonus | 32.19 | 33.31 | 34.24 | 31.06 |
| Fireballs (`fireball at N blocks`, 6–14 blocks) | 6 | 9 | 4 | 9 |
| Slams that hit the player (`slam hit 1`) / that missed (`slam hit 0`) | 5 / 1 | 6 / 0 | 7 / 1 | 6 / 0 |
| Health 90 after the set; enrage: `summoned ruby zombies`, then two `CHECK summoned ruby zombie` | yes | yes | yes | yes |
| Slams after the summon still hit only the player (`slam hit 1`, not 3: the golem's own zombies are safe) | yes | yes | yes | yes |
| Client chat: "The Ruby Golem is enraged!" and "The Ruby Golem shatters." | yes | yes | yes | yes |
| Drops: rubies and a ruby block (`CHECK dropped item`) | 2 ruby stacks, 1 block | 2 + 1 | 3 + 1 | 3 + 1 |
| Conductor errors (`Conductor mob`, exceptions naming Conductor) | none | none | none | none |

A slam that "missed" is one where the player had already been pushed out of its 4.5-block reach. The numbers of fireballs and slams differ because the golem decides by distance each time, and the player is moved by knock-back and pushes.

**Screenshots** (Verified by looking):
- **1.16.1:**
  - 8 s: the golem, with model and texture correct (head with a crest, arms, legs) and the pink notched bar "Ruby Golem".
  - 13 s: the golem walking toward the player, legs apart from the walk animation.
  - 22 s: the bar is red after the enrage, the two summoned ruby zombies are in front of it (burning at noon, as zombies do), and the chat shows the enrage and the two CHECK lines.
  - 30 s: the golem close up, with the player on fire from a fireball (fire resistance keeps them alive).
- **Modern versions:**
  - 1.21.11 (the first run, before the fixes below): the same model and pink bar, both arms raised for the slam, one arm up for the summon, and the red bar after the enrage.
  - 1.21.4: the golem with a fireball in flight.
  - 26.3: the golem, the red bar after the enrage, and the zombies' CHECK lines.

## Found and fixed during these runs

- **The slam hit the golem's own zombies.** The first 1.21.11 run logged `slam hit 3` after the summon. Area effects now leave out other monsters when the mob is hostile ([`MobControl.damageNearby`](../contract/src/main/java/conductor/contract/MobControl.java)). Every run above logs `slam hit 1` after the summon.
- **Fireballs left no evidence.** The behaviour now logs each one (`fireball at N blocks`).
- **1.16.1 had no custom mobs.** Before this, 1.16.1 left custom mobs out with a warning. It now has its own `ConductorMob` (1.16.1's entity API: no `EntitySpawnReason`, `Registry` instead of `BuiltInRegistries`, older projectile constructors). It also has a model built from 1.16.1's `ModelPart` boxes, posed per bone by the same animation code as the other versions.

## Noise in the logs, not caused by Conductor

- **Server, 1.21.x and 26.3:** `No data fixer registered for ruby:...` for each mob, as for every modded entity type.
- **Server, all versions:** `No key layers in MapLike[{}]` and the biome line. This is vanilla world-creation output, present in runs without custom mobs.
- **Client, modern versions:** `Error loading class: net/irisshaders/...BlockMaterialMapping`. That is the optional Iris hook being skipped when Iris is absent ([live-run-shaders.md](live-run-shaders.md)).
- **Client, 1.21.11 and 26.3:** 401 errors from Mojang's account service, because the client is offline.
- **Client, 1.16.1:** 3524 `File minecraft:sounds/... does not exist` warnings, the same count as in the earlier 1.16.1 content run (`legacy-1.16.1-content-client.log.gz`). The test client has no sound assets, and its sound system is off.

## Not checked

- Natural spawning and spawn eggs (not built), riding, and saving and reloading a world with the golem in it.
- Many players, or many golems at once (performance).
- The golem under a shader pack.
