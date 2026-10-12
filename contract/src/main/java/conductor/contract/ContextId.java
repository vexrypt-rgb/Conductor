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
public record ContextId(long sessionId, long epoch, String dimension) {

    public ContextId {
        Objects.requireNonNull(dimension, "dimension");
    }
}
