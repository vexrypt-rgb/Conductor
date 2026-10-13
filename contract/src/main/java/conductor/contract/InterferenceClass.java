package conductor.contract;

/** Stage 4 interference classification. */
public enum InterferenceClass {
    /** The contract prevents it. */
    BLOCKED,
    /** The contract cannot prevent it but observes it and fails closed. */
    DETECTED,
    /** Neither prevented nor observable through the contract's signals. */
    UNDETECTABLE,
    /** The source does not exist in this runtime. */
    NOT_APPLICABLE
}
