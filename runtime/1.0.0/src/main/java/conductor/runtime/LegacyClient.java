package conductor.runtime;

import net.minecraft.client.Minecraft;

/**
 * The game, as Minecraft 1.0 has no getter for it: the Conductor mod's hook on {@code Minecraft.init()} hands it over,
 * on the game thread.
 */
public final class LegacyClient {

    private static volatile Minecraft minecraft;
    private static volatile Thread gameThread;

    private LegacyClient() {
    }

    /** Game thread, as the game starts. */
    public static void started(Minecraft game) {
        minecraft = game;
        gameThread = Thread.currentThread();
    }

    static Minecraft minecraft() {
        return minecraft;
    }

    static boolean isGameThread() {
        return Thread.currentThread() == gameThread;
    }
}
