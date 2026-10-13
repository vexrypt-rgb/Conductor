package conductor.contract;

/**
 * A player on the server, read in one tick.
 *
 * @param name      the in-game name
 * @param uuid      the account's id, as text; stays the same across deaths and rejoins
 * @param dimension e.g. {@code minecraft:overworld}
 * @param health    0 while dead
 * @param spectator in spectator mode
 */
public record OnlinePlayer(String name, String uuid, String dimension, double x, double y, double z, double health,
                           boolean spectator) {

    public boolean alive() {
        return health > 0;
    }
}
