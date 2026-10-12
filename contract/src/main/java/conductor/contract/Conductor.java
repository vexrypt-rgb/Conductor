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

    String CONTRACT_VERSION = "conductor-contract/0.4 (stage 4: evidence-based release, interference classification)";

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
     * Granted only while the movement resource is {@link ResourceState#AVAILABLE}: release evidence has held for the
     * configured {@link ReleasePolicy#confirmStopWindowTicks()}. Without that setting nothing is ever granted. A
     * context change never grants anything.
     */
    OperationResult requestTravel(NavigationGoal goal);

    /**
     * Invoke the engine's cancel for an operation this consumer holds. The result is at most
     * {@link OperationState#CANCELLING}: cancel requested, not confirmed. The engine's cancel may stop more than this
     * operation; see {@link NavigationPort#cancel()}.
     */
    OperationResult cancel(OperationId operation);

    OperationResult operationStatus(OperationId operation);

    ResourceStatus resourceStatus();

    /**
     * Does {@code content} exist in the running game or engine? Use this instead of checking version numbers.
     * ABSENT means "looked, not there"; anything uncertain is UNKNOWN.
     */
    ContentResult content(ContentId content);
}
