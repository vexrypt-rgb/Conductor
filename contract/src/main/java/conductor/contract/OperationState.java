package conductor.contract;

/**
 * Evidence-driven cancellation state machine (normative).
 * <p>
 * Stage 3 reaches only {@link #EXECUTING}, {@link #CANCELLING} and {@link #UNCERTAIN}. {@link #STOPPED_PATHING} and
 * {@link #CANCELLED} need the evidence rules of Stage 4 and are never entered before then. Invoking cancel is not
 * confirmation, and a timeout never proves success.
 */
public enum OperationState {
    /** Accepted. Authority held. Resource in use. */
    EXECUTING,
    /** cancel() invoked. Authority held. Resource not yet safe. */
    CANCELLING,
    /** Candidate pathing-stopped evidence; overrides not shown cleared. Resource not yet safe. (Stage 4.) */
    STOPPED_PATHING,
    /** Pathing stopped and override-clear evidence. Authority released. Terminal. (Stage 4.) */
    CANCELLED,
    /** Outcome cannot be established. Invalid for success. Resource must be treated as unsafe. */
    UNCERTAIN
}
