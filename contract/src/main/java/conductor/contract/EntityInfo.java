package conductor.contract;

import java.util.OptionalDouble;

/**
 * One entity near the player, read in one tick. Ids are valid only within the current context and may be reused
 * by the game later; use them right away.
 *
 * @param id      the game's entity id, for {@link Conductor#attack(int)}
 * @param type    entity type id, e.g. {@code minecraft:zombie}
 * @param health  for living entities
 * @param hostile the game classes it as an enemy (monsters, including those not yet angry)
 */
public record EntityInfo(int id, String type, String name, double x, double y, double z, double distance,
                         OptionalDouble health, boolean player, boolean hostile) {
}
