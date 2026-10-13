package conductor.contract;

import java.util.Objects;

/**
 * Snapshot of one operation.
 *
 * @param contextValid   false once the operation's context is no longer current; it can then never succeed
 * @param authorityHeld  whether this operation may still request movement through the contract
 */
public record OperationStatus(OperationId id, OperationState state, boolean contextValid, boolean authorityHeld, String detail) {

    public OperationStatus {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(state, "state");
        detail = detail == null ? "" : detail;
    }
}
