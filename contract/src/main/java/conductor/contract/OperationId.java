package conductor.contract;

import java.util.Objects;

/** Handle for one navigation operation. Bound to the context it was granted in. */
public final class OperationId {
    private final long id;
    private final ContextId context;

    public OperationId(long id, ContextId context) {
        Objects.requireNonNull(context, "context");

        this.id = id;
        this.context = context;
    }

    public long id() { return id; }

    public ContextId context() { return context; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OperationId)) return false;
        OperationId other = (OperationId) o;
        return java.util.Arrays.deepEquals(new Object[] {this.id, this.context}, new Object[] {other.id, other.context});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.id, this.context}); }

    @Override public String toString() { return "OperationId[" + "id=" + id + ", " + "context=" + context + "]"; }
}
