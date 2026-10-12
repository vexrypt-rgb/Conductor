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
}
