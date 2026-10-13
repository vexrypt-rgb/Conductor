package conductor.runtime;

import java.util.function.Consumer;

/**
 * The entry points Conductor's own game hooks (mixins in the Conductor mod) call. Everything else in Conductor
 * reaches the game through these and through plain Minecraft reads, so Conductor needs no engine and no loader API
 * beyond the hooks themselves. Each hook is cheap and does nothing until a runtime is installed.
 */
public final class Hooks {

    private static volatile Runnable clientTick = () -> {};
    private static volatile HookEventFeed feed;
    private static volatile Consumer<Object> render = pose -> {};
    private static volatile LineDrawer lineDrawer;

    private Hooks() {}

    static void onClientTick(Runnable tick) {
        clientTick = tick;
    }

    static void events(HookEventFeed events) {
        feed = events;
    }

    static void renderer(Consumer<Object> renderer) {
        render = renderer;
    }

    /** The Conductor mod's per-version line drawer; without one, overlays are not offered. */
    public static void lineDrawer(LineDrawer drawer) {
        lineDrawer = drawer;
    }

    static LineDrawer lineDrawer() {
        return lineDrawer;
    }

    /** End of every client tick (game thread). */
    public static void clientTick() {
        try {
            clientTick.run();
        } catch (RuntimeException | LinkageError e) {
            // never take the game down; the host already isolates module faults
            org.apache.logging.log4j.LogManager.getLogger("conductor").error("Conductor tick failed", e);
        }
    }

    /** A chat line the player sends. Returns true when Conductor takes it (a {@code +} command): don't send it. */
    public static boolean outgoingChat(String message) {
        HookEventFeed f = feed;
        return f != null && f.outgoingChat(message);
    }

    /** One block the server changed. */
    public static void blockChanged(int x, int y, int z, String block) {
        HookEventFeed f = feed;
        if (f != null) {
            f.blockChanged(x, y, z, block);
        }
    }

    public static void chunkLoaded(int chunkX, int chunkZ, boolean loaded) {
        HookEventFeed f = feed;
        if (f != null) {
            f.chunk(chunkX, chunkZ, loaded);
        }
    }

    public static void chatReceived(String text, java.util.UUID sender) {
        HookEventFeed f = feed;
        if (f != null) {
            f.chat(text, sender);
        }
    }

    public static void playerDied() {
        HookEventFeed f = feed;
        if (f != null) {
            f.death();
        }
    }

    /** After the world is drawn; {@code pose} is the per-version render context (see OverlayRenderer). */
    public static void renderWorld(Object pose) {
        try {
            render.accept(pose);
        } catch (RuntimeException | LinkageError e) {
            org.apache.logging.log4j.LogManager.getLogger("conductor").error("Conductor overlay rendering failed", e);
            render = p -> {}; // stop trying every frame
        }
    }
}
