package conductor.contract;

import java.util.Objects;

/**
 * Snapshot of one operation.
 *
 * @param contextValid   false once the operation's context is no longer current; it can then never succeed
 * @param authorityHeld  whether this operation may still request movement through the contract
 */
public final class OperationStatus {
    private final OperationId id;
    private final OperationState state;
    private final boolean contextValid;
    private final boolean authorityHeld;
    private final String detail;

    public OperationStatus(OperationId id, OperationState state, boolean contextValid, boolean authorityHeld, String detail) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(state, "state");
        detail = detail == null ? "" : detail;

        this.id = id;
        this.state = state;
        this.contextValid = contextValid;
        this.authorityHeld = authorityHeld;
        this.detail = detail;
    }

    public OperationId id() { return id; }

    public OperationState state() { return state; }

    public boolean contextValid() { return contextValid; }

    public boolean authorityHeld() { return authorityHeld; }

    public String detail() { return detail; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OperationStatus)) return false;
        OperationStatus other = (OperationStatus) o;
        return java.util.Arrays.deepEquals(new Object[] {this.id, this.state, this.contextValid, this.authorityHeld, this.detail}, new Object[] {other.id, other.state, other.contextValid, other.authorityHeld, other.detail});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.id, this.state, this.contextValid, this.authorityHeld, this.detail}); }

    @Override public String toString() { return "OperationStatus[" + "id=" + id + ", " + "state=" + state + ", " + "contextValid=" + contextValid + ", " + "authorityHeld=" + authorityHeld + ", " + "detail=" + detail + "]"; }
}
