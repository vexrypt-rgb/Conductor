package conductor.runtime;

import conductor.contract.ContextTracker;

import java.lang.ref.WeakReference;

/**
 * Turns Conductor's client-tick hook into context changes: in a world or not, a new world object, a new server
 * connection. Polled on the game thread once per client tick, so it needs no engine and no loader events.
 */
final class Lifecycle {

    private final ContextTracker contexts;
    private final GameAccess game;
    private long tick = -1;
    private WeakReference<Object> lastConnection;
    private WeakReference<Object> lastLevel;
    private Runnable inWorldTick = () -> {};

    Lifecycle(ContextTracker contexts, GameAccess game) {
        this.contexts = contexts;
        this.game = game;
    }

    /** Run on every in-world tick, after the context has been updated (the contract's evidence tick). */
    void onInWorldTick(Runnable hook) {
        this.inWorldTick = hook;
    }

    ContextTracker contexts() {
        return contexts;
    }

    long lastTick() {
        return tick;
    }

    /** Game thread, once per client tick. */
    void tick() {
        tick++;
        Object level = game.levelIdentity();
        if (level == null || !game.hasPlayer()) {
            if (lastLevel != null) {
                contexts.onOutOfWorld(tick);
            }
            lastConnection = null;
            lastLevel = null;
            return;
        }
        Object connection = game.connectionIdentity();
        boolean connectionChanged = lastConnection != null && lastConnection.get() != connection;
        if (lastLevel != null && lastLevel.get() != level && !connectionChanged) {
            // same server, new world object (respawn, dimension change, world reload)
            contexts.onWorldReplaced(game.dimensionId(), tick);
        }
        lastConnection = new WeakReference<>(connection);
        lastLevel = new WeakReference<>(level);
        contexts.onInWorld(game.dimensionId(), connectionChanged, tick);
        inWorldTick.run();
    }
}
