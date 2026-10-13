package conductor.contract;

/** Published on {@link Topics#LEASES} whenever a lease changes hands. */
public final class LeaseEvent {
    private final Kind kind;
    private final Lease lease;
    private final ModuleId cause;
    private final String detail;

    public LeaseEvent(Kind kind, Lease lease, ModuleId cause, String detail) {
        this.kind = kind;
        this.lease = lease;
        this.cause = cause;
        this.detail = detail;
    }

    public Kind kind() { return kind; }

    public Lease lease() { return lease; }

    public ModuleId cause() { return cause; }

    public String detail() { return detail; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LeaseEvent)) return false;
        LeaseEvent other = (LeaseEvent) o;
        return java.util.Arrays.deepEquals(new Object[] {this.kind, this.lease, this.cause, this.detail}, new Object[] {other.kind, other.lease, other.cause, other.detail});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.kind, this.lease, this.cause, this.detail}); }

    @Override public String toString() { return "LeaseEvent[" + "kind=" + kind + ", " + "lease=" + lease + ", " + "cause=" + cause + ", " + "detail=" + detail + "]"; }


    public enum Kind { GRANTED, PENDING, REVOKED, RELEASED, PENDING_DROPPED }
}
