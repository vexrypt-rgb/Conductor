package conductor.contract;

import java.util.Optional;

/**
 * Outcome of {@link ModuleContext#acquire}.
 *
 * @param lease  present when GRANTED, or when PENDING (not yet usable; {@code onLeaseGranted} follows)
 * @param holder the module holding the resource, when DENIED or PENDING
 */
public final class LeaseResult {
    private final Status status;
    private final Optional<Lease> lease;
    private final Optional<ModuleId> holder;
    private final String detail;

    public LeaseResult(Status status, Optional<Lease> lease, Optional<ModuleId> holder, String detail) {
        this.status = status;
        this.lease = lease;
        this.holder = holder;
        this.detail = detail;
    }

    public Status status() { return status; }

    public Optional<Lease> lease() { return lease; }

    public Optional<ModuleId> holder() { return holder; }

    public String detail() { return detail; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LeaseResult)) return false;
        LeaseResult other = (LeaseResult) o;
        return java.util.Arrays.deepEquals(new Object[] {this.status, this.lease, this.holder, this.detail}, new Object[] {other.status, other.lease, other.holder, other.detail});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.status, this.lease, this.holder, this.detail}); }

    @Override public String toString() { return "LeaseResult[" + "status=" + status + ", " + "lease=" + lease + ", " + "holder=" + holder + ", " + "detail=" + detail + "]"; }


    public enum Status {
        /** The lease is yours now. */
        GRANTED,
        /** You will get it once the current holder has been preempted and the resource is shown free. */
        PENDING,
        /** Someone with equal or higher priority holds it (or a call rule was broken; see detail). */
        DENIED
    }

    public boolean isGranted() {
        return status == Status.GRANTED;
    }
}
