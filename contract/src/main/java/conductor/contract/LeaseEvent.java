package conductor.contract;

/** Published on {@link Topics#LEASES} whenever a lease changes hands. */
public record LeaseEvent(Kind kind, Lease lease, ModuleId cause, String detail) {

    public enum Kind { GRANTED, PENDING, REVOKED, RELEASED, PENDING_DROPPED }
}
