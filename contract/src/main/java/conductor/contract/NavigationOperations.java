package conductor.contract;

import java.util.Optional;

/**
 * Stage 3 navigation rules for one consumer. Game thread only; {@link ContractService} checks the thread first.
 * <ul>
 *   <li>Contract-level exclusivity: at most one operation, and a rejected request makes no engine call.</li>
 *   <li>One grant per installation. Once the engine has been called, the resource is never treated as reusable,
 *       because release needs resource-safety evidence (Stage 4).</li>
 *   <li>A request is rejected before any engine call if it would run on a backend whose stop cannot be observed.</li>
 *   <li>cancel() moves EXECUTING to CANCELLING only. Invocation is not confirmation.</li>
 *   <li>On a context change: the operation is invalidated and loses authority, cancel() is invoked best-effort,
 *       and no new operation is granted.</li>
 * </ul>
 */
final class NavigationOperations {

    private final RuntimeIntegration runtime;
    private final ContextTracker contexts;
    private final DiagnosticSink sink;

    private boolean grantSpent;
    private long nextId = 1;
    private OperationId id;
    private OperationState state;
    private boolean contextValid;
    private String detail = "";

    NavigationOperations(RuntimeIntegration runtime, ContextTracker contexts, DiagnosticSink sink) {
        this.runtime = runtime;
        this.contexts = contexts;
        this.sink = sink;
    }

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
        if (grantSpent) {
            return reject(RejectionReason.RESOURCE_NOT_RELEASED, "requestTravel",
                    "the one Stage 3 grant is spent; reuse needs resource-safety evidence (Stage 4)");
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

        grantSpent = true; // from here the engine is called, so the resource is no longer known to be untouched
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
        contextValid = true;
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

    void onContextInvalidated(ContextId old) {
        if (id == null || !contextValid || !id.context().equals(old)) {
            return;
        }
        contextValid = false;
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

    private void invokeCancel(String why) {
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

    private OperationStatus snapshot() {
        return new OperationStatus(id, state, contextValid, contextValid, detail);
    }

    private OperationResult reject(RejectionReason reason, String subject, String why) {
        record(DiagnosticEvent.Type.OPERATION_REJECTED, subject, reason + ": " + why);
        return OperationResult.rejected(reason, null, why);
    }

    private void record(DiagnosticEvent.Type type, String subject, String why) {
        sink.record(DiagnosticEvent.onGameThread(type, runtime.currentTick(), contexts.current().orElse(null), subject, why));
    }
}
