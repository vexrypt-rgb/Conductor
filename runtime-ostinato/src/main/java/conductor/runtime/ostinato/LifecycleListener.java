package conductor.runtime.ostinato;

import baritone.api.event.events.TickEvent;
import baritone.api.event.events.WorldEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.event.listener.AbstractGameEventListener;
import conductor.contract.ContextTracker;
import conductor.contract.DiagnosticEvent;
import conductor.contract.DiagnosticSink;
import net.minecraft.client.multiplayer.ClientLevel;

import java.lang.ref.WeakReference;

/**
 * Turns Ostinato lifecycle events into context transitions. It only reads the events it receives and the game
 * access; it never calls into pathing, processes or inputs.
 * <p>
 * Ostinato raises tick events from {@code MixinMinecraft.runTick}, IN when player and world are present and OUT
 * otherwise, and world events from {@code Minecraft.setLevel} (primary Baritone only).
 */
final class LifecycleListener extends AbstractGameEventListener {

    private final ContextTracker contexts;
    private final GameAccess game;
    private final DiagnosticSink sink;
    private long lastTick = -1;
    private WeakReference<Object> lastConnection;

    LifecycleListener(ContextTracker contexts, GameAccess game, DiagnosticSink sink) {
        this.contexts = contexts;
        this.game = game;
        this.sink = sink;
    }

    long lastTick() {
        return lastTick;
    }

    @Override
    public void onTick(TickEvent event) {
        if (!game.isGameThread()) {
            offThread("tick");
            return;
        }
        lastTick = event.getCount();
        if (event.getType() == TickEvent.Type.IN) {
            Object connection = game.connectionIdentity();
            boolean changed = lastConnection != null && lastConnection.get() != connection;
            lastConnection = new WeakReference<>(connection);
            contexts.onInWorld(game.dimensionId(), changed, lastTick);
        } else {
            lastConnection = null;
            contexts.onOutOfWorld(lastTick);
        }
    }

    @Override
    public void onWorldEvent(WorldEvent event) {
        if (event.getState() != EventState.POST) {
            return;
        }
        if (!game.isGameThread()) {
            offThread("world event");
            return;
        }
        ClientLevel world = event.getWorld();
        contexts.onWorldReplaced(world == null ? null : game.dimensionOf(world), lastTick);
    }

    private void offThread(String what) {
        // The tracker is confined to the game thread, so it is not touched here. The next in-world tick still
        // catches a dimension change, but not a same-dimension world replacement; the event records that gap.
        sink.record(new DiagnosticEvent(DiagnosticEvent.Type.OFF_THREAD_VIOLATION, Thread.currentThread().getName(),
                -1, null, what, "Ostinato " + what + " arrived off the game thread; context not updated"));
    }
}
