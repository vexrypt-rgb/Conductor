package conductor.contract;

import java.util.Objects;

/**
 * Identity of one session and world context.
 * <p>
 * Context validity: an observation or result belongs to the current context only if its {@code ContextId} is
 * equal to the current one in all three fields. A change in any field invalidates everything from the old context
 * immediately.
 * <p>
 * A changed {@code ContextId} is an identity and lifecycle signal only. It is never evidence that a prior engine
 * operation has stopped, that input overrides were cleared, or that the movement resource is safe to reuse.
 *
 * @param sessionId Conductor's identifier for one continuous in-world period. It is not a server connection
 *                  identifier.
 * @param epoch     increases on every context change, including inside one session. Never reused.
 * @param dimension the runtime's dimension identifier, as reported by the version adapter.
 */
public final class ContextId {
    private final long sessionId;
    private final long epoch;
    private final String dimension;

    public ContextId(long sessionId, long epoch, String dimension) {
        Objects.requireNonNull(dimension, "dimension");

        this.sessionId = sessionId;
        this.epoch = epoch;
        this.dimension = dimension;
    }

    public long sessionId() { return sessionId; }

    public long epoch() { return epoch; }

    public String dimension() { return dimension; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContextId)) return false;
        ContextId other = (ContextId) o;
        return java.util.Arrays.deepEquals(new Object[] {this.sessionId, this.epoch, this.dimension}, new Object[] {other.sessionId, other.epoch, other.dimension});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.sessionId, this.epoch, this.dimension}); }

    @Override public String toString() { return "ContextId[" + "sessionId=" + sessionId + ", " + "epoch=" + epoch + ", " + "dimension=" + dimension + "]"; }
}
