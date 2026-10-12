package conductor.contract;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Navigation rules for one consumer (Stages 3 and 4). Game thread only: {@link ContractService} checks the thread
 * before calling in, and the runtime calls {@link #tick()} once per in-world game tick.
 * <ul>
 *   <li>Contract-level exclusivity: one operation at a time. A conflicting request is rejected (BLOCKED) without an
 *       engine call.</li>
 *   <li>The movement resource is {@link ResourceState#AVAILABLE} only while release evidence has held for
 *       {@link ReleasePolicy#confirmStopWindowTicks()} consecutive ticks. Nothing else releases it: not a cancel
 *       call, not a timeout, not a context change. Every grant, including the first, needs it AVAILABLE.</li>
 *   <li>Cancellation follows the evidence: CANCELLING, then STOPPED_PATHING once pathing has stopped, then CANCELLED
 *       once the resource is released. With no evidence within {@link ReleasePolicy#uncertainAfterTicks()} the
 *       operation becomes UNCERTAIN, and the resource stays quarantined.</li>
 *   <li>Another process taking control during an operation makes it UNCERTAIN (interference DETECTED). Activity
 *       while no operation runs quarantines the resource.</li>
 *   <li>On a context change: the operation is invalidated, cancel() is invoked best-effort, the evidence count
 *       restarts and the resource is quarantined. Identity change grants nothing.</li>
 * </ul>
 */
final class NavigationOperations {

    private final RuntimeIntegration runtime;
    private final ContextTracker contexts;
    private final DiagnosticSink sink;
    private final ReleasePolicy policy;

    private ResourceState resource = ResourceState.QUARANTINED;
    private String resourceDetail = "no release evidence yet";
    private long quietTicks;

    private long nextId = 1;
    private OperationId id;
    private OperationState state;
    private boolean contextValid;
    private boolean authorityHeld;
    private long cancelTick = -1;
    private String detail = "";
    private String lastForeignController;

    NavigationOperations(RuntimeIntegration runtime, ContextTracker contexts, DiagnosticSink sink, ReleasePolicy policy) {
        this.runtime = runtime;
        this.contexts = contexts;
        this.sink = sink;
        this.policy = policy;
    }

    // ---- per-tick evidence ----

    void tick() {
        if (contexts.current().isEmpty()) {
            return;
        }
        Optional<NavigationPort> port = runtime.navigation();
        ReleaseEvidence evidence = ReleaseEvidence.sample(runtime, supportedKinds());
        quietTicks = evidence.quiet() ? quietTicks + 1 : 0;
        boolean releasable = policy.confirmStopWindowTicks().isPresent()
                && quietTicks >= policy.confirmStopWindowTicks().getAsLong();

        noteForeignControl(evidence, port);
        if (id != null) {
            progressOperation(evidence, releasable);
        }

        if (releasable && resource != ResourceState.AVAILABLE) {
            setResource(ResourceState.AVAILABLE, "release evidence held for " + quietTicks + " ticks");
            if (authorityHeld) {
                authorityHeld = false;
                record(DiagnosticEvent.Type.AUTHORITY_RELEASED, "operation " + id.id(), "resource released on evidence");
            }
        } else if (!evidence.quiet() && resource == ResourceState.AVAILABLE) {
            setResource(ResourceState.QUARANTINED, "activity observed: " + evidence.reason());
            // a named controller was already recorded by noteForeignControl
            if (!operationActive() && evidence.controller().isEmpty()) {
                interference(InterferenceClass.DETECTED, "engine activity",
                        "engine became active while no contract operation was running: " + evidence.reason());
            }
        }
    }

    private void progressOperation(ReleaseEvidence evidence, boolean releasable) {
        switch (state) {
            case CANCELLING, STOPPED_PATHING -> {
                if (releasable) {
                    transition(OperationState.CANCELLED, "release evidence held for " + quietTicks + " ticks");
                } else if (state == OperationState.CANCELLING && evidence.pathingStopped()) {
                    transition(OperationState.STOPPED_PATHING, "no segment, next segment or calculation; inputs not yet shown clear");
                } else if (policy.uncertainAfterTicks().isPresent()
                        && runtime.currentTick() - cancelTick >= policy.uncertainAfterTicks().getAsLong()) {
                    transition(OperationState.UNCERTAIN, "no release evidence within " + policy.uncertainAfterTicks().getAsLong()
                            + " ticks of cancel; resource stays quarantined");
                }
            }
            case EXECUTING -> {
                if (releasable) {
                    // the engine went quiet on its own; arrival is not something these signals can show
                    transition(OperationState.UNCERTAIN, "engine quiet without cancel; outcome not established");
                }
            }
            default -> {
            }
        }
    }

    private void noteForeignControl(ReleaseEvidence evidence, Optional<NavigationPort> port) {
        Optional<String> controller = evidence.controller();
        boolean own = controller.isPresent() && port.isPresent() && operationActive()
                && port.get().isOwnController(controller.get());
        if (controller.isEmpty() || own) {
            lastForeignController = null;
            return;
        }
        if (!controller.get().equals(lastForeignController)) {
            lastForeignController = controller.get();
            interference(InterferenceClass.DETECTED, controller.get(),
                    operationActive() ? "another process took control during operation " + id.id() : "process in control");
            if (operationActive() && state != OperationState.UNCERTAIN) {
                transition(OperationState.UNCERTAIN, "another process took control: " + controller.get());
            }
        }
    }

    // ---- consumer calls ----

    OperationResult requestTravel(NavigationGoal goal) {
        if (goal == null) {
            return reject(RejectionReason.INVALID_ARGUMENT, "requestTravel", "goal is null");
        }
        Optional<ContextId> current = contexts.current();
        if (current.isEmpty()) {
            return reject(RejectionReason.NO_CURRENT_CONTEXT, "requestTravel", "no current context");
        }
        Optional<NavigationPort> port = runtime.navigation();
        if (port.isEmpty()) {
            return reject(RejectionReason.NO_NAVIGATION, "requestTravel", "runtime offers no navigation");
        }
        if (operationActive()) {
            interference(InterferenceClass.BLOCKED, "contract request", "operation " + id.id() + " still holds the resource");
            return reject(RejectionReason.CONFLICTING_OPERATION, "requestTravel", "operation " + id.id() + " is " + state);
        }
        if (resource != ResourceState.AVAILABLE) {
            return reject(RejectionReason.RESOURCE_NOT_RELEASED, "requestTravel", resourceDetail
                    + (policy.confirmStopWindowTicks().isEmpty() ? " (no confirmStopWindowTicks configured: never released)" : ""));
        }
        boolean observable;
        try {
            observable = port.get().routesToObservableBackend();
        } catch (RuntimeException | LinkageError e) {
            return reject(RejectionReason.BACKEND_UNOBSERVABLE, "requestTravel", "route check failed: " + e);
        }
        if (!observable) {
            return reject(RejectionReason.BACKEND_UNOBSERVABLE, "requestTravel",
                    "request would run on a backend whose stop cannot be observed");
        }

        // From here the engine is called: the resource is in use until evidence says otherwise.
        quietTicks = 0;
        setResource(ResourceState.QUARANTINED, "in use by a contract request");
        NavigationPort.Delegation delegation;
        try {
            delegation = port.get().travel(goal);
        } catch (RuntimeException | LinkageError e) {
            record(DiagnosticEvent.Type.PROVIDER_FAULT, "requestTravel", "engine call threw: " + e);
            return reject(RejectionReason.ENGINE_DECLINED, "requestTravel", "engine call threw: " + e);
        }
        if (!delegation.accepted()) {
            return reject(RejectionReason.ENGINE_DECLINED, "requestTravel",
                    "engine declined (" + delegation.backend() + "): " + delegation.detail());
        }

        id = new OperationId(nextId++, current.get());
        state = null; // a new operation has no previous state
        contextValid = true;
        authorityHeld = true;
        cancelTick = -1;
        lastForeignController = null;
        record(DiagnosticEvent.Type.AUTHORITY_GRANTED, "operation " + id.id(), goal + " via " + delegation.backend());
        if (delegation.backendObservable()) {
            transition(OperationState.EXECUTING, "accepted by " + delegation.backend());
        } else {
            transition(OperationState.UNCERTAIN, "engine routed to unobservable backend " + delegation.backend());
        }
        return OperationResult.accepted(snapshot());
    }

    OperationResult cancel(OperationId operation) {
        if (operation == null) {
            return reject(RejectionReason.INVALID_ARGUMENT, "cancel", "operation is null");
        }
        if (!operation.equals(id)) {
            return reject(RejectionReason.UNKNOWN_OPERATION, "cancel", "not an operation of this consumer: " + operation);
        }
        if (!contextValid) {
            // cancel() was already invoked best-effort when the context changed
            return OperationResult.rejected(RejectionReason.AUTHORITY_INVALIDATED, snapshot(), "operation's context is no longer current");
        }
        if (!authorityHeld) {
            return OperationResult.rejected(RejectionReason.AUTHORITY_INVALIDATED, snapshot(), "operation no longer holds authority");
        }
        if (state == OperationState.EXECUTING || state == OperationState.UNCERTAIN) {
            invokeCancel("consumer requested cancel");
            if (state == OperationState.EXECUTING) {
                transition(OperationState.CANCELLING, "cancel invoked; not confirmed");
            }
        }
        return OperationResult.accepted(snapshot());
    }

    OperationResult status(OperationId operation) {
        if (operation == null || !operation.equals(id)) {
            return OperationResult.rejected(RejectionReason.UNKNOWN_OPERATION, null, "not an operation of this consumer");
        }
        return OperationResult.accepted(snapshot());
    }

    ResourceStatus resourceStatus() {
        return new ResourceStatus(null, resource, quietTicks, resourceDetail);
    }

    void onContextInvalidated(ContextId old) {
        quietTicks = 0;
        if (resource == ResourceState.AVAILABLE) {
            setResource(ResourceState.QUARANTINED, "context changed; evidence restarts");
        }
        if (id == null || !contextValid || !id.context().equals(old)) {
            return;
        }
        contextValid = false;
        authorityHeld = false;
        record(DiagnosticEvent.Type.AUTHORITY_INVALIDATED, "operation " + id.id(),
                "context " + old + " is no longer current; no new authority granted");
        if (state == OperationState.EXECUTING || state == OperationState.UNCERTAIN) {
            if (runtime.navigation().isPresent()) {
                invokeCancel("context invalidated");
            }
            if (state == OperationState.EXECUTING) {
                transition(OperationState.CANCELLING, "context invalidated; cancel invoked best-effort; not confirmed");
            }
        }
    }

    // ---- helpers ----

    /** An operation that may still be driving the engine through the contract. */
    private boolean operationActive() {
        return id != null && authorityHeld
                && (state == OperationState.EXECUTING || state == OperationState.CANCELLING
                || state == OperationState.STOPPED_PATHING || state == OperationState.UNCERTAIN);
    }

    private Set<ObservationKind<?>> supportedKinds() {
        Set<ObservationKind<?>> kinds = new HashSet<>();
        for (CapabilityDescriptor capability : runtime.capabilities()) {
            kinds.addAll(capability.observationKinds());
        }
        return kinds;
    }

    private void invokeCancel(String why) {
        cancelTick = runtime.currentTick();
        try {
            runtime.navigation().orElseThrow().cancel();
            record(DiagnosticEvent.Type.CANCEL_INVOKED, "operation " + id.id(), why + "; invocation is not confirmation");
        } catch (RuntimeException | LinkageError e) {
            record(DiagnosticEvent.Type.PROVIDER_FAULT, "operation " + id.id(), "cancel threw: " + e);
            if (state == OperationState.EXECUTING || state == OperationState.CANCELLING) {
                transition(OperationState.UNCERTAIN, "cancel threw: " + e);
            }
        }
    }

    private void transition(OperationState next, String why) {
        OperationState previous = state;
        state = next;
        detail = why;
        record(DiagnosticEvent.Type.OPERATION_STATE_CHANGED, "operation " + id.id(), previous + " -> " + next + ": " + why);
    }

    private void setResource(ResourceState next, String why) {
        ResourceState previous = resource;
        resource = next;
        resourceDetail = why;
        if (previous != next) {
            record(DiagnosticEvent.Type.RESOURCE_STATE_CHANGED, "movement resource", previous + " -> " + next + ": " + why);
        }
    }

    private void interference(InterferenceClass kind, String source, String why) {
        record(DiagnosticEvent.Type.INTERFERENCE, kind.name(), source + ": " + why);
    }

    private OperationStatus snapshot() {
        return new OperationStatus(id, state, contextValid, authorityHeld, detail);
    }

    private OperationResult reject(RejectionReason reason, String subject, String why) {
        record(DiagnosticEvent.Type.OPERATION_REJECTED, subject, reason + ": " + why);
        return OperationResult.rejected(reason, null, why);
    }

    private void record(DiagnosticEvent.Type type, String subject, String why) {
        sink.record(DiagnosticEvent.onGameThread(type, runtime.currentTick(), contexts.current().orElse(null), subject, why));
    }
}
