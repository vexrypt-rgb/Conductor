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
    INVALID_ARGUMENT
}
