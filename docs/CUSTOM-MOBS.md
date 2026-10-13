# Custom mobs and bosses

**Project owner (2026-10-13):** "Should also be able to handle fully custom mobs and bosses".

A content module can add a mob that is entirely its own: its own model, texture and animations, its own AI made from vanilla goals, its own code for special attacks and phases, a boss bar, sounds and drops. Everything is declared once and plain data. The **same module jar** runs on Minecraft 1.16.1, 1.21.4, 1.21.11 and 26.3: the files are read by Conductor's own code, and the mob is built from each version's vanilla classes at load. This was verified live on all four versions with the example boss, the Ruby Golem ([live-run-custom-mobs.md](live-run-custom-mobs.md)).

For mobs that only need a vanilla body with a new texture, size and stats, `like(MobArchetype.ZOMBIE)` (or `SPIDER`, `PIG`) is simpler ([CONTENT-MODS.md](CONTENT-MODS.md) §4).

## 1. The parts of a custom mob

| Part | Where | Made with |
|---|---|---|
| Declaration: id, size, stats, hostile or not, goals, boss bar, sounds | `ContentModule.declare`, `content.mob("name").model(...)` | Java, the contract only |
| Model | `assets/<ns>/conductor/geo/<name>.geo.json` | Blockbench, "Bedrock Entity" model, exported as Bedrock geometry |
| Animations (optional) | `assets/<ns>/conductor/animations/<name>.animation.json` | Blockbench, exported as Bedrock animations |
| Texture | `assets/<ns>/textures/entity/<name>.png` (or `.texture(...)`) | any image editor; laid out as the model's box UV |
| Name | `assets/<ns>/lang/en_us.json`: `"entity.<ns>.<name>"` | JSON |
| Drops | `data/<ns>/loot_table/entities/<name>.json` | vanilla loot table |
| Special attacks, phases, summons | a `MobBehaviour` class | Java, the contract only |

A file under `mc_<version>/` in the jar replaces the plain one on that version, as for the jar's other assets.

## 2. Declaring it

The Ruby Golem, from [`examples/ruby-content`](../examples/ruby-content/src/main/java/conductor/example/ruby/RubyContent.java):

```java
content.mob("ruby_golem").model("ruby_golem").animations("ruby_golem").size(1.4f, 2.7f)
        .health(200).attackDamage(12).speed(0.25).armor(8).knockbackResistance(1).followRange(32).fireImmune()
        .boss(BossBar.Color.PINK, BossBar.Style.NOTCHED_10)
        .goals(MobGoal.swim(), MobGoal.meleeAttack(1.0), MobGoal.wander(0.6), MobGoal.lookAtPlayers(16),
                MobGoal.lookAround(), MobGoal.retaliate(), MobGoal.targetPlayers())
        .sounds(null, "minecraft:entity.iron_golem.hurt", "minecraft:entity.iron_golem.death")
        .stepSound("minecraft:entity.iron_golem.step")
        .behaviour(new RubyGolemBehaviour());
```

- **`model(name)`** makes the mob custom. `animations(name)` names the animation file.
- **`size(width, height)`** is the hit box in blocks; the default is a zombie's 0.6 × 1.8. `scale(k)` scales both the hit box and the drawn model.
- **`hostile()`** (the default) makes a monster: it attacks, and it is removed in peaceful. **`passive()`** makes a creature.
- **Stats** start from a zombie's (20 health, 0.25 speed, 3 attack damage, 32 follow range) and each one can be set.
- **`goals(...)`** is the mob's AI in priority order. Each goal maps to the vanilla goal of the same kind on every version:

  | Goal | Vanilla goal |
  |---|---|
  | `swim()` | `FloatGoal` |
  | `meleeAttack(speed)` | `MeleeAttackGoal` |
  | `rangedAttack(projectile, interval, range)` | `RangedAttackGoal`; projectiles are `ARROW`, `SMALL_FIREBALL` and `SNOWBALL` |
  | `wander(speed)` | `WaterAvoidingRandomStrollGoal` |
  | `lookAtPlayers(range)`, `lookAround()` | `LookAtPlayerGoal`, `RandomLookAroundGoal` |
  | `panic(speed)` | `PanicGoal` |
  | `targetPlayers()`, `retaliate()` | `NearestAttackableTargetGoal<Player>`, `HurtByTargetGoal` |

  With no goals, a hostile mob gets swim, melee, wander, look and target players; a passive one gets swim, panic, wander and look.
- **`boss(color, style)`** shows a boss bar, filled by its health, to players near it. A boss is never despawned.
- **`sounds(ambient, hurt, death)`** and **`stepSound(step)`** take vanilla sound ids or ones from the module's own `sounds.json`. Any of them may be null.

The contract checks the declaration when the module loads: ids, the model and animation names, size (0.1–8 blocks each way), scale (0.25–4), goal speeds (up to 4) and ranges (up to 64). A module with an invalid declaration is reported and left out as a whole.

## 3. The model and animations

**Model.** Make it in Blockbench as a "Bedrock Entity" and export the geometry. Format 1.12.0 and later is read, and the older 1.8.0 layout too.
- Conductor turns Bedrock's model space (y up, feet at 0, pivots absolute) into Java's (y down, feet at 24, offsets from the parent's pivot). The unit test checks the result against vanilla's player arm and pig body, which both editions ship (`MobFilesTest`).
- A cube with its own rotation becomes a child part holding that cube.
- **Box UV only.** A per-face UV is refused with a clear message, because vanilla's cube builder only does box UV.
- A bone named `head` turns with where the mob looks.

**Animations.** Export them from Blockbench as Bedrock animations. Names are matched by their last part, so `animation.ruby_golem.walk` is `walk`.
- `idle` and `walk` play by themselves: the mob fades from idle into walk as it walks faster.
- `attack` plays when it hits in melee, and `shoot` when it shoots.
- Any other animation plays when the behaviour calls `mob.animate("name")`. The server picks it, and every client that sees the mob plays it.
- **Read:**
  - `loop`: true, false or `"hold_on_last_frame"`; `animation_length`;
  - rotation, position and scale per bone, as constants, arrays, keyframes (with `pre`/`post`) or Molang.
- **Molang:**
  - numbers, `+ - * /`, comparisons, `&& || !`, `?:` and parentheses;
  - `math.` `sin cos asin acos atan abs sqrt floor ceil round trunc exp ln min max pow mod clamp lerp pi` (angles in degrees, as in Molang);
  - `query.` `anim_time`, `life_time`, `modified_distance_moved` and `modified_move_speed` (`q.` works too).
- **Not supported:**
  - `variable.*` and other queries are refused when the file loads, with the line, rather than read as zero every frame;
  - `catmullrom` keyframes play linearly;
  - sound and particle keyframes are ignored (the behaviour plays those).
- An animation that moves a bone the model lacks is warned about once.

**When a file is wrong**, Conductor logs the reason once (`Conductor mob ruby:ruby_golem: cannot use ...: <reason>`). The mob then exists and fights, but is drawn with no model; the game keeps running.

## 4. Behaviour: special attacks, phases, summons

A `MobBehaviour` is the mob's own code, on top of its goals. It runs on the server, on the game thread, once per tick per mob:

| Method | When |
|---|---|
| `spawned(mob)` | the first tick the mob exists on this server (after spawning, or after its chunk loaded again) |
| `tick(mob)` | every tick while it is alive |
| `hurt(mob, amount)` | the tick after it lost health |
| `died(mob)` | once, when it dies |

`MobControl` is what it can do:
- **Reads:** position, health, `healthFraction()`, `age()`, `onGround()`, `target()` (with its distance) and `playersWithin(radius)`.
- **Per-mob state:** `memory()`, a map kept with the mob. One behaviour instance serves every mob of the type, so all state goes there.
- **Actions:**
  - `animate`, `shoot(projectile)`, `leap(horizontal, vertical)`;
  - `damageNearby(radius, amount)`, `pushNearby(radius, strength)`, `effectNearby(radius, effect, seconds, amplifier)`;
  - `summon(type, dx, dy, dz)`, `teleport`, `heal`;
  - `sound`, `particles`, `say(message, radius)` (chat to players near it);
  - `bossBar(color)` to recolour the bar, and `log`.
- **Who the area actions hit:** never the mob itself or mobs of its own type, nor spectators. A hostile mob never hits other monsters, so its summoned minions are safe from its own slam. Radii are capped at 64 blocks, and `summon` and `teleport` refuse places more than 64 blocks away.

The Ruby Golem's fight ([`RubyGolemBehaviour`](../examples/ruby-content/src/main/java/conductor/example/ruby/RubyGolemBehaviour.java)) uses these:
- **Close up:** it plays `slam`, then 0.8 s later, when the arms hit the ground, deals damage within 4.5 blocks, pushes back what is near, and makes particles and a sound.
- **Farther away (6–24 blocks):** it throws small fireballs.
- **At half health it is enraged:**
  - it plays `enrage`, turns its bar red, roars and says so in chat;
  - it attacks faster;
  - it summons two ruby zombies, and again every 20 seconds.
- **On death:** "The Ruby Golem shatters." Its loot table drops 8–16 rubies and 1–2 ruby blocks.

**Safety.** The behaviour never runs inside a vanilla callback: hurt and death are noticed in the mob's own tick. If any method throws, Conductor logs it with the mob type and stops calling that behaviour for the type until the game restarts. The mobs keep their goals, and the game keeps running.

## 5. Client and server

- **The module must be on both sides**, as for all built-in content ([CONTENT-MODS.md](CONTENT-MODS.md) §3.3). The server needs it for the mob and its behaviour; clients need it to draw it.
- On a dedicated server, the server also reads the animation file, to know which animations exist before it tells clients to play one.
- Spawn it with `/summon <ns>:<name>`, spawners or other data. Natural spawning and spawn eggs are not built yet.

## 6. How each version builds it

| | 1.16.1 | 1.21.4 | 1.21.11 | 26.3 |
|---|---|---|---|---|
| Entity class | `ConductorMob` (own copy for 1.16.1's API) | `ConductorMob` (shared modern code) | same | same |
| Entity type | `EntityType` constructor | `EntityType.Builder` | same | same |
| Model on the client | `ModelPart` boxes built at load, posed and drawn per bone | `ModelPart` tree from a `LayerDefinition`, render state | same | same |
| Animation playback | Conductor's `MobAnimations`, the same code everywhere | same | same | same |
| Live-verified | yes | yes | yes | yes |

Only the entity and model classes differ per version. Reading the files, Molang, animation playback and the behaviour API are one shared copy.

## 7. Limits (today)

- Box UV only, and only the Molang listed above.
- No natural spawning, spawn eggs, riding, flying or swimming movement, custom projectiles or custom AI goals in the contract. A behaviour can do much with the actions above, but not everything.
- Hit box is one box, scaled with the mob. Bedrock's `locators`, `bind_pose` and per-bone `texture_meshes` are not read.
- The mob is drawn with one texture and no emissive or overlay layer.
- Shader packs see custom mobs as plain entities; this was not separately checked.
