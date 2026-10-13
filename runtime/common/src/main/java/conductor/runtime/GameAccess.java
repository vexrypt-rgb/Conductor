package conductor.runtime;


/**
 * The privileged Minecraft reads Conductor's core runtime needs, kept in one place so they stay behind the runtime boundary and
 * can be replaced in tests without loading Minecraft.
 */
interface GameAccess {

    /** Must be safe from any thread. */
    boolean isGameThread();

    /** Game thread only. Called only on in-world ticks. */
    String dimensionId();

    /** Game thread only. The current client world object, compared by reference; null outside a world. */
    Object levelIdentity();

    /** Game thread only. Whether there is a local player. */
    boolean hasPlayer();

    /** Game thread only. Identity of the current server connection, compared by reference; may be null. */
    Object connectionIdentity();

    /** Game thread only. The block the player stands in, or null without a player. */
    conductor.contract.BlockPoint playerBlock();

    /**
     * Whether a built-in registry has {@code id}. PRESENT or ABSENT after looking; null when {@code kind} has no
     * built-in registry here or the id is malformed (the caller reports UNKNOWN). Built-in registries are frozen at
     * startup, so this is valid in and out of a world.
     */
    /** Game thread only. The name of the player with this id on the current server, or null when not known. */
    String playerName(java.util.UUID id);

    /** Game thread only. Whether the player can reach {@code target} (a game entity) to attack it; only called with a player. */
    boolean canReach(Object target);

    /** Game thread only. The selected hotbar slot (0-8); only called with a player. */
    int selectedSlot();

    /** Game thread only. Select hotbar slot 0-8; only called with a player. */
    void setSelectedSlot(int slot);

    /** Game thread only. Item id to total count over the whole inventory, or null without a player. */
    java.util.Map<String, Integer> inventoryItems();

    /** Game thread only. Whether the game's use key is down (whoever pressed it); null when there are no options yet. */
    Boolean useKeyDown();

    conductor.contract.ContentResult.Presence registryPresence(conductor.contract.ContentId.Kind kind, String id);
}
