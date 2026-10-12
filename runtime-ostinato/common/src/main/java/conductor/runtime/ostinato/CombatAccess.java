package conductor.runtime.ostinato;

import java.util.Optional;

/**
 * The engine combat calls this runtime makes, kept behind an interface so the port can be tested without Minecraft.
 * Game thread only.
 */
interface CombatAccess {

    void attackPlayer(String name);

    void attackPlayers();

    void attackHostiles();

    /** One mob type by entity id, e.g. {@code minecraft:zombie}. */
    void attackMob(String entityId);

    /** The current target as "type id name", from whichever combat process is running; empty when none. */
    Optional<String> target();
}
