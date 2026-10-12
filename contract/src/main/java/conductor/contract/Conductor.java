package conductor.contract;

/**
 * Java binding for consumers: direct calls, no serialisation.
 * <p>
 * Surface: discovery and read-only observation (Stage 2), plus single-consumer navigation delegation (Stage 3).
 * A {@code Conductor} instance is one consumer; it is not a shared service.
 * <p>
 * Every method must be called on the game thread. A call from another thread is rejected with
 * {@link RejectionReason#OFF_THREAD} before anything is read, and is recorded in the diagnostic sink.
 */
public interface Conductor {

    String CONTRACT_VERSION = "conductor-contract/0.3 (stage 3: single-consumer navigation delegation)";

    DiscoveryResult discover();

    /** Sample {@code kind} now. Rejected without sampling if {@code policy} is null. */
    <T> ObservationResult<T> observe(ObservationKind<T> kind, FreshnessPolicy policy);

    /**
     * Re-check an observation taken earlier: it must belong to the current context and satisfy {@code policy} now.
     * Observations from an old context are rejected, however recent.
     */
    <T> ObservationResult<T> revalidate(Observation<T> observation, FreshnessPolicy policy);

    /**
     * Delegate one travel request to the engine's existing navigation boundary.
     * <p>
     * Stage 3 grants at most one operation per installation: reusing the movement resource needs resource-safety
     * evidence, which only Stage 4 defines. Later requests are rejected with
     * {@link RejectionReason#RESOURCE_NOT_RELEASED}. A context change never grants a new operation.
     */
    OperationResult requestTravel(NavigationGoal goal);

    /**
     * Invoke the engine's cancel for an operation this consumer holds. The result is at most
     * {@link OperationState#CANCELLING}: cancel requested, not confirmed. The engine's cancel may stop more than this
     * operation; see {@link NavigationPort#cancel()}.
     */
    OperationResult cancel(OperationId operation);

    OperationResult operationStatus(OperationId operation);
}
