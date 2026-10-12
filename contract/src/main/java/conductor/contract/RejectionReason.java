package conductor.contract;

public enum RejectionReason {
    /** The call did not arrive on the game thread. Nothing was sampled or read. */
    OFF_THREAD,
    /** No freshness policy was supplied. Fails closed; there is no default. */
    NO_FRESHNESS_POLICY,
    /** There is no current context (not in a world). */
    NO_CURRENT_CONTEXT,
    /** The observation belongs to a context that is no longer current. */
    CONTEXT_INVALIDATED,
    /** The observation is older than the policy allows. */
    STALE,
    /** The runtime integration does not provide this kind. */
    UNSUPPORTED_KIND,
    INVALID_ARGUMENT,
    /** The runtime offers no navigation delegation. */
    NO_NAVIGATION,
    /** The movement resource is not AVAILABLE: release evidence has not held for the configured window. */
    RESOURCE_NOT_RELEASED,
    /** This consumer already has an operation holding the resource (contract-level exclusivity). */
    CONFLICTING_OPERATION,
    /** The request would run on a backend whose stop cannot be observed. Rejected before any engine call. */
    BACKEND_UNOBSERVABLE,
    /** The engine did not accept the request. The engine was called, so the grant is spent. */
    ENGINE_DECLINED,
    UNKNOWN_OPERATION,
    /** The operation's context is no longer current, so its authority is gone. */
    AUTHORITY_INVALIDATED
}
