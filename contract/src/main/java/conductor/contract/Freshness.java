package conductor.contract;

/** Result of evaluating an observation's age against a {@link FreshnessPolicy}. Orthogonal to {@link Availability}. */
public enum Freshness {
    FRESH,
    STALE
}
