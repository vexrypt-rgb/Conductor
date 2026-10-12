package conductor.runtime.ostinato;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * The privileged Minecraft reads this runtime needs, kept in one place so they stay behind the runtime boundary and
 * can be replaced in tests without loading Minecraft.
 */
interface GameAccess {

    /** Must be safe from any thread. */
    boolean isGameThread();

    /** Game thread only. Called only on in-world ticks. */
    String dimensionId();

    /** Game thread only. */
    String dimensionOf(ClientLevel level);

    /** Game thread only. Identity of the current server connection, compared by reference; may be null. */
    Object connectionIdentity();

    /** Game thread only. The block the player stands in, or null without a player. */
    conductor.contract.BlockPoint playerBlock();

    /**
     * Whether a built-in registry has {@code id}. PRESENT or ABSENT after looking; null when {@code kind} has no
     * built-in registry here or the id is malformed (the caller reports UNKNOWN). Built-in registries are frozen at
     * startup, so this is valid in and out of a world.
     */
    /** Game thread only. Item id to total count over the whole inventory, or null without a player. */
    java.util.Map<String, Integer> inventoryItems();

    /** Game thread only. Whether the game's use key is down (whoever pressed it); null when there are no options yet. */
    Boolean useKeyDown();

    conductor.contract.ContentResult.Presence registryPresence(conductor.contract.ContentId.Kind kind, String id);
}
