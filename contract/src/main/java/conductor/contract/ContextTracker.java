package conductor.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Holds the current {@link ContextId} and moves it on lifecycle signals from the runtime integration.
 * <p>
 * Confined to the game thread: the runtime integration calls the {@code on*} methods from game-thread events, and
 * the contract reads {@link #current()} only after its own game-thread check. It has no synchronisation of its own.
 * <p>
 * Every transition invalidates the old context. No transition says anything about engine-resource safety or
 * confirmed runtime disappearance; those need separate evidence (Stage 4).
 */
public final class ContextTracker {

    private final DiagnosticSink sink;
    private long lastSessionId;
    private long lastEpoch;
    private ContextId current;
    private final List<Consumer<ContextId>> invalidationListeners = new ArrayList<>();

    public ContextTracker(DiagnosticSink sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** Called on the game thread with the old context whenever a context is invalidated or ended. */
    public void addInvalidationListener(Consumer<ContextId> listener) {
        invalidationListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public Optional<ContextId> current() {
        return Optional.ofNullable(current);
    }

    /**
     * Player and world are present this tick.
     *
     * @param dimension         the adapter's dimension identifier
     * @param connectionChanged the adapter saw a different connection than at the last call
     */
    public void onInWorld(String dimension, boolean connectionChanged, long tick) {
        Objects.requireNonNull(dimension, "dimension");
        if (current == null) {
            establish(dimension, tick, "entered world");
        } else if (connectionChanged) {
            ContextId old = current;
            current = null;
            sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.CONTEXT_ENDED, tick, old, "context", "connection changed"));
            notifyInvalidated(old);
            establish(dimension, tick, "new connection");
        } else if (!current.dimension().equals(dimension)) {
            advanceEpoch(dimension, tick, "dimension changed from " + current.dimension());
        }
    }

    /** The runtime replaced its world object. {@code dimension} is null when no world remains. */
    public void onWorldReplaced(String dimension, long tick) {
        if (current == null) {
            return; // the next in-world tick establishes a context
        }
        if (dimension == null) {
            end(tick, "world unloaded");
        } else {
            advanceEpoch(dimension, tick, "world replaced");
        }
    }

    /** Player or world is absent this tick. */
    public void onOutOfWorld(long tick) {
        if (current != null) {
            end(tick, "left world");
        }
    }

    private void establish(String dimension, long tick, String why) {
        current = new ContextId(++lastSessionId, ++lastEpoch, dimension);
        sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.CONTEXT_ESTABLISHED, tick, current, "context", why));
    }

    private void advanceEpoch(String dimension, long tick, String why) {
        ContextId old = current;
        current = new ContextId(old.sessionId(), ++lastEpoch, dimension);
        sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.CONTEXT_INVALIDATED, tick, old, "context",
                why + "; superseded by epoch " + current.epoch()));
        notifyInvalidated(old);
    }

    private void end(long tick, String why) {
        ContextId old = current;
        current = null;
        sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.CONTEXT_ENDED, tick, old, "context", why));
        notifyInvalidated(old);
    }

    private void notifyInvalidated(ContextId old) {
        for (Consumer<ContextId> listener : invalidationListeners) {
            listener.accept(old);
        }
    }
}
