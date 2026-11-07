package conductor.contract;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Rules for operations that steer the movement resource: travel (Stages 3 and 4) and combat (Stage 5). One operation
 * at a time, whichever kind. Game thread only: {@link ContractService} checks the thread
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
    private ModuleId owner;
    private java.util.function.Predicate<String> attributed = controller -> false;
    private OperationState state;
    private boolean contextValid;
    private boolean authorityHeld;
    private long cancelTick = -1;
    private String detail = "";
    private String lastForeignController;
    /** The running operation's own engine process, and how to stop it; set when an operation is accepted. */
    private java.util.function.Predicate<String> ownController = controller -> false;
    private Runnable engineCancel;
    /** Final status of earlier operations, so an owner can still read how its operation ended after a new one began. */
    private final java.util.LinkedHashMap<OperationId, Finished> finished = new java.util.LinkedHashMap<OperationId, Finished>() {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<OperationId, Finished> eldest) {
            return size() > 64;
        }
    };

    private static final class Finished {
        private final ModuleId owner;
        private final OperationStatus status;

        public Finished(ModuleId owner, OperationStatus status) {
            this.owner = owner;
            this.status = status;
        }

        public ModuleId owner() { return owner; }

        public OperationStatus status() { return status; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Finished)) return false;
            Finished other = (Finished) o;
            return java.util.Arrays.deepEquals(new Object[] {this.owner, this.status}, new Object[] {other.owner, other.status});
        }

        @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.owner, this.status}); }

        @Override public String toString() { return "Finished[" + "owner=" + owner + ", " + "status=" + status + "]"; }
    }

    NavigationOperations(RuntimeIntegration runtime, ContextTracker contexts, DiagnosticSink sink, ReleasePolicy policy) {
        this.runtime = runtime;
        this.contexts = contexts;
        this.sink = sink;
        this.policy = policy;
    }

    // ---- per-tick evidence ----

    void tick() {
        if (!contexts.current().isPresent()) {
            return;
        }
        ReleaseEvidence evidence = ReleaseEvidence.sample(runtime, supportedKinds());
        quietTicks = evidence.quiet() ? quietTicks + 1 : 0;
        boolean releasable = policy.confirmStopWindowTicks().isPresent()
                && quietTicks >= policy.confirmStopWindowTicks().getAsLong();

        noteForeignControl(evidence);
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
            if (!operationActive() && !evidence.controller().isPresent()) {
                interference(InterferenceClass.DETECTED, "engine activity",
                        "engine became active while no contract operation was running: " + evidence.reason());
            }
        }
    }

    private void progressOperation(ReleaseEvidence evidence, boolean releasable) {
        switch (state) {
            case CANCELLING: case STOPPED_PATHING: {
                if (releasable) {
                    transition(OperationState.CANCELLED, "release evidence held for " + quietTicks + " ticks");
                } else if (state == OperationState.CANCELLING && evidence.pathingStopped()) {
                    transition(OperationState.STOPPED_PATHING, "no segment, next segment or calculation; inputs not yet shown clear");
                } else if (policy.uncertainAfterTicks().isPresent()
                        && runtime.currentTick() - cancelTick >= policy.uncertainAfterTicks().getAsLong()) {
                    transition(OperationState.UNCERTAIN, "no release evidence within " + policy.uncertainAfterTicks().getAsLong()
                            + " ticks of cancel; resource stays quarantined");
                }
                break;
            }
            case EXECUTING: {
                if (releasable) {
                    // the engine went quiet on its own; arrival is not something these signals can show
                    transition(OperationState.UNCERTAIN, "engine quiet without cancel; outcome not established");
                }
                break;
            }
            default:
                break;
        }
    }

    private void noteForeignControl(ReleaseEvidence evidence) {
        Optional<String> controller = evidence.controller();
        boolean own = controller.isPresent() && operationActive() && ownController.test(controller.get());
        boolean declared = controller.isPresent() && attributed.test(controller.get());
        if (!controller.isPresent() || own || declared) {
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

    // ---- host hooks (module platform) ----

    /** Engine processes declared by the current MOVEMENT holder: their activity is that module's, not interference. */
    void setControllerAttribution(java.util.function.Predicate<String> attributed) {
        this.attributed = attributed == null ? controller -> false : attributed;
    }

    ResourceState resourceState() {
        return resource;
    }

    /** The module owning the current operation while it still holds authority; empty otherwise. */
    Optional<ModuleId> activeOwner() {
        return operationActive() ? Optional.of(owner) : Optional.empty();
    }

    /** Best-effort cancel of {@code module}'s active operation (lease revoked). Not confirmation. */
    void cancelOwnedBy(ModuleId module, String why) {
        if (operationActive() && module.equals(owner) && contextValid
                && (state == OperationState.EXECUTING || state == OperationState.UNCERTAIN)) {
            invokeCancel(why);
            if (state == OperationState.EXECUTING) {
                transition(OperationState.CANCELLING, why + "; cancel invoked; not confirmed");
            }
        }
    }

    // ---- consumer calls ----

    OperationResult requestTravel(NavigationGoal goal) {
        return requestTravel(ModuleId.CORE, goal);
    }

    OperationResult requestTravel(ModuleId requester, NavigationGoal goal) {
        if (goal == null) {
            return reject(RejectionReason.INVALID_ARGUMENT, "requestTravel", "goal is null");
        }
        Optional<NavigationPort> port = runtime.navigation();
        OperationResult refused = precheck("requestTravel", port.isPresent(), RejectionReason.NO_NAVIGATION, "navigation");
        if (refused != null) {
            return refused;
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
        NavigationPort p = port.get();
        return begin(requester, "requestTravel", goal.toString(), () -> p.travel(goal), p::isOwnController, p::cancel);
    }

    OperationResult requestCombat(ModuleId requester, CombatGoal goal) {
        if (goal == null) {
            return reject(RejectionReason.INVALID_ARGUMENT, "requestCombat", "goal is null");
        }
        Optional<CombatPort> port = runtime.combat();
        OperationResult refused = precheck("requestCombat", port.isPresent(), RejectionReason.NO_COMBAT, "combat");
        if (refused != null) {
            return refused;
        }
        CombatPort p = port.get();
        return begin(requester, "requestCombat", goal.toString(), () -> p.engage(goal), p::isOwnController, p::cancel);
    }

    /** Checks shared by every movement operation, in order; null when the request may go to the engine. */
    private OperationResult precheck(String subject, boolean portPresent, RejectionReason noPort, String what) {
        RejectionReason reason = null;
        String why = null;
        if (!contexts.current().isPresent()) {
            reason = RejectionReason.NO_CURRENT_CONTEXT;
            why = "no current context";
        } else if (!portPresent) {
            reason = noPort;
            why = "runtime offers no " + what;
        } else if (operationActive()) {
            interference(InterferenceClass.BLOCKED, "contract request", "operation " + id.id() + " still holds the resource");
            reason = RejectionReason.CONFLICTING_OPERATION;
            why = "operation " + id.id() + " is " + state;
        } else if (resource != ResourceState.AVAILABLE) {
            reason = RejectionReason.RESOURCE_NOT_RELEASED;
            why = resourceDetail + (!policy.confirmStopWindowTicks().isPresent()
                    ? " (no confirmStopWindowTicks configured: never released)" : "");
        }
        return reason == null ? null : reject(reason, subject, why);
    }

    private OperationResult begin(ModuleId requester, String subject, String goal,
                                  java.util.function.Supplier<NavigationPort.Delegation> delegate,
                                  java.util.function.Predicate<String> own, Runnable cancel) {
        // From here the engine is called: the resource is in use until evidence says otherwise.
        quietTicks = 0;
        setResource(ResourceState.QUARANTINED, "in use by a contract request");
        NavigationPort.Delegation delegation;
        try {
            delegation = delegate.get();
        } catch (RuntimeException | LinkageError e) {
            record(DiagnosticEvent.Type.PROVIDER_FAULT, subject, "engine call threw: " + e);
            return reject(RejectionReason.ENGINE_DECLINED, subject, "engine call threw: " + e);
        }
        if (!delegation.accepted()) {
            return reject(RejectionReason.ENGINE_DECLINED, subject,
                    "engine declined (" + delegation.backend() + "): " + delegation.detail());
        }

        if (id != null) {
            finished.put(id, new Finished(owner, snapshot()));
        }
        id = new OperationId(nextId++, contexts.current().orElseThrow(java.util.NoSuchElementException::new));
        owner = requester;
        ownController = own;
        engineCancel = cancel;
        state = null; // a new operation has no previous state
        contextValid = true;
        authorityHeld = true;
        cancelTick = -1;
        lastForeignController = null;
        record(DiagnosticEvent.Type.AUTHORITY_GRANTED, "operation " + id.id(), goal + " via " + delegation.backend() + " for " + requester);
        if (delegation.backendObservable()) {
            transition(OperationState.EXECUTING, "accepted by " + delegation.backend());
        } else {
            transition(OperationState.UNCERTAIN, "engine routed to unobservable backend " + delegation.backend());
        }
        return OperationResult.accepted(snapshot());
    }

    OperationResult cancel(OperationId operation) {
        return cancel(ModuleId.CORE, operation);
    }

    OperationResult cancel(ModuleId caller, OperationId operation) {
        if (operation == null) {
            return reject(RejectionReason.INVALID_ARGUMENT, "cancel", "operation is null");
        }
        Finished done = finished.get(operation);
        if (done != null && done.owner().equals(caller)) {
            return OperationResult.rejected(RejectionReason.AUTHORITY_INVALIDATED, done.status(), "operation already ended");
        }
        if (!operation.equals(id) || !caller.equals(owner)) {
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
        return status(ModuleId.CORE, operation);
    }

    OperationResult status(ModuleId caller, OperationId operation) {
        Finished done = operation == null ? null : finished.get(operation);
        if (done != null && done.owner().equals(caller)) {
            return OperationResult.accepted(done.status());
        }
        if (operation == null || !operation.equals(id) || !caller.equals(owner)) {
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
            if (engineCancel != null) {
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
            engineCancel.run();
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
