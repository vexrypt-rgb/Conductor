package conductor.contract;

import java.util.Optional;

/**
 * Outcome of {@link ModuleContext#acquire}.
 *
 * @param lease  present when GRANTED, or when PENDING (not yet usable; {@code onLeaseGranted} follows)
 * @param holder the module holding the resource, when DENIED or PENDING
 */
public record LeaseResult(Status status, Optional<Lease> lease, Optional<ModuleId> holder, String detail) {

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
