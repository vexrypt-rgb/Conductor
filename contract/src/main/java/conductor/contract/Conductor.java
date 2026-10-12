package conductor.contract;

/**
 * Java binding for consumers: direct calls, no serialisation.
 * <p>
 * Stage 2 surface: discovery and read-only observation only. Nothing here starts, stops or changes movement.
 * <p>
 * Every method must be called on the game thread. A call from another thread is rejected with
 * {@link RejectionReason#OFF_THREAD} before anything is read, and is recorded in the diagnostic sink.
 */
public interface Conductor {

    String CONTRACT_VERSION = "conductor-contract/0.2 (stage 2: discovery + read-only observations)";

    DiscoveryResult discover();

    /** Sample {@code kind} now. Rejected without sampling if {@code policy} is null. */
    <T> ObservationResult<T> observe(ObservationKind<T> kind, FreshnessPolicy policy);

    /**
     * Re-check an observation taken earlier: it must belong to the current context and satisfy {@code policy} now.
     * Observations from an old context are rejected, however recent.
     */
    <T> ObservationResult<T> revalidate(Observation<T> observation, FreshnessPolicy policy);
}
