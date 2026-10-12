package conductor.contract;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Enforces the contract rules over a {@link RuntimeIntegration}. Each call checks, in order:
 * <ol>
 *   <li>Game thread. If not, reject before reading anything, and record the violation in the sink, which is safe
 *       from any thread.</li>
 *   <li>Arguments and freshness policy. A missing policy is rejected without sampling.</li>
 *   <li>Current context. Observations from any other context are rejected.</li>
 *   <li>Freshness, evaluated separately from availability.</li>
 * </ol>
 */
public final class ContractService implements Conductor {

    private final RuntimeIntegration runtime;
    private final ContextTracker contexts;
    private final DiagnosticSink sink;
    private final NavigationOperations navigation;

    /** Without a release policy: the resource is never released, so navigation is never granted. */
    public ContractService(RuntimeIntegration runtime, ContextTracker contexts, DiagnosticSink sink) {
        this(runtime, contexts, sink, ReleasePolicy.none());
    }

    public ContractService(RuntimeIntegration runtime, ContextTracker contexts, DiagnosticSink sink, ReleasePolicy policy) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.navigation = new NavigationOperations(runtime, contexts, sink, Objects.requireNonNull(policy, "policy"));
        contexts.addInvalidationListener(navigation::onContextInvalidated);
    }

    /**
     * Called by the runtime integration once per in-world game tick, on the game thread, after the context has
     * been updated. Not part of the consumer binding.
     */
    public void tick() {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("tick", "ignored off the game thread"));
            return;
        }
        navigation.tick();
    }

    @Override
    public ResourceStatus resourceStatus() {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("resourceStatus", "rejected before reading state"));
            return new ResourceStatus(RejectionReason.OFF_THREAD, null, -1, "call was not on the game thread");
        }
        return navigation.resourceStatus();
    }

    @Override
    public OperationResult requestTravel(NavigationGoal goal) {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("requestTravel", "rejected before any engine call"));
            return OperationResult.rejected(RejectionReason.OFF_THREAD, null, "call was not on the game thread");
        }
        return navigation.requestTravel(goal);
    }

    @Override
    public OperationResult cancel(OperationId operation) {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("cancel", "rejected before any engine call"));
            return OperationResult.rejected(RejectionReason.OFF_THREAD, null, "call was not on the game thread");
        }
        return navigation.cancel(operation);
    }

    @Override
    public OperationResult operationStatus(OperationId operation) {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("operationStatus", "rejected before reading state"));
            return OperationResult.rejected(RejectionReason.OFF_THREAD, null, "call was not on the game thread");
        }
        return navigation.status(operation);
    }

    @Override
    public DiscoveryResult discover() {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("discover", "rejected before reading runtime state"));
            return new DiscoveryResult(RejectionReason.OFF_THREAD, CONTRACT_VERSION, Optional.empty(), -1, List.of());
        }
        return new DiscoveryResult(null, CONTRACT_VERSION, contexts.current(), runtime.currentTick(), runtime.capabilities());
    }

    @Override
    public <T> ObservationResult<T> observe(ObservationKind<T> kind, FreshnessPolicy policy) {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("observe " + kind, "rejected before sampling"));
            return ObservationResult.rejected(RejectionReason.OFF_THREAD, null, null, "call was not on the game thread");
        }
        long tick = runtime.currentTick();
        if (kind == null) {
            return reject(RejectionReason.INVALID_ARGUMENT, "observe", null, null, tick, "kind is null");
        }
        if (policy == null) {
            return reject(RejectionReason.NO_FRESHNESS_POLICY, "observe " + kind, null, null, tick, "no freshness policy; not sampled");
        }
        Optional<ContextId> current = contexts.current();
        if (current.isEmpty()) {
            return reject(RejectionReason.NO_CURRENT_CONTEXT, "observe " + kind, null, null, tick, "no current context; not sampled");
        }
        if (!supports(kind)) {
            return reject(RejectionReason.UNSUPPORTED_KIND, "observe " + kind, null, null, tick, "runtime does not provide " + kind);
        }
        long nanos = runtime.nanoTime();
        Observation<T> observation = new Observation<>(kind, sampleSafely(kind, current.get(), tick), current.get(), tick, nanos);
        return evaluate(observation, policy, current.get(), tick, nanos, "observe " + kind);
    }

    @Override
    public <T> ObservationResult<T> revalidate(Observation<T> observation, FreshnessPolicy policy) {
        if (!runtime.isGameThread()) {
            sink.record(DiagnosticEvent.offThread("revalidate", "rejected before reading context"));
            return ObservationResult.rejected(RejectionReason.OFF_THREAD, observation, null, "call was not on the game thread");
        }
        long tick = runtime.currentTick();
        if (observation == null) {
            return reject(RejectionReason.INVALID_ARGUMENT, "revalidate", null, null, tick, "observation is null");
        }
        String subject = "revalidate " + observation.kind();
        if (policy == null) {
            return reject(RejectionReason.NO_FRESHNESS_POLICY, subject, observation, null, tick, "no freshness policy");
        }
        Optional<ContextId> current = contexts.current();
        if (current.isEmpty()) {
            return reject(RejectionReason.CONTEXT_INVALIDATED, subject, observation, null, tick,
                    "no current context; observation was from " + observation.context());
        }
        return evaluate(observation, policy, current.get(), tick, runtime.nanoTime(), subject);
    }

    private <T> ObservationResult<T> evaluate(Observation<T> observation, FreshnessPolicy policy, ContextId current,
                                              long tick, long nanos, String subject) {
        if (!observation.context().equals(current)) {
            return reject(RejectionReason.CONTEXT_INVALIDATED, subject, observation, null, tick,
                    "observation from " + observation.context() + ", current is " + current);
        }
        Freshness freshness = policy.evaluate(observation.sampledTick(), observation.sampledNanos(), tick, nanos);
        if (freshness == Freshness.STALE) {
            return reject(RejectionReason.STALE, subject, observation, freshness, tick,
                    "sampled at tick " + observation.sampledTick() + ", now " + tick + ", policy " + policy);
        }
        return ObservationResult.accepted(observation);
    }

    private boolean supports(ObservationKind<?> kind) {
        for (CapabilityDescriptor capability : runtime.capabilities()) {
            if (capability.observationKinds().contains(kind)) {
                return true;
            }
        }
        return false;
    }

    private <T> Sample<T> sampleSafely(ObservationKind<T> kind, ContextId context, long tick) {
        try {
            Sample<T> sample = runtime.sample(kind);
            if (sample != null) {
                return sample;
            }
            sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.PROVIDER_FAULT, tick, context, kind.name(), "runtime returned no sample"));
            return Sample.unknown("runtime returned no sample");
        } catch (RuntimeException | LinkageError e) {
            sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.PROVIDER_FAULT, tick, context, kind.name(), String.valueOf(e)));
            return Sample.unknown("sampling failed: " + e);
        }
    }

    private <T> ObservationResult<T> reject(RejectionReason reason, String subject, Observation<T> observation,
                                            Freshness freshness, long tick, String detail) {
        sink.record(DiagnosticEvent.onGameThread(DiagnosticEvent.Type.OBSERVATION_REJECTED, tick,
                contexts.current().orElse(null), subject, reason + ": " + detail));
        return ObservationResult.rejected(reason, observation, freshness, detail);
    }
}
