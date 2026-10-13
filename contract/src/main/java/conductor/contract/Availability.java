package conductor.contract;

/**
 * Whether an observation obtained a usable value. Orthogonal to {@link Freshness}.
 * <p>
 * Missing data never becomes a confirmed negative. Only {@link #OBSERVED} carries a value, and a confirmed negative
 * (for example "no path segment") is {@code OBSERVED} with that value.
 */
public enum Availability {
    /** A value was obtained. */
    OBSERVED,
    /** No usable observation was obtained. */
    NOT_OBSERVED,
    /** The kind is not meaningful in the current context, e.g. a backend that is not bound. */
    NOT_APPLICABLE,
    /** Neither the availability nor the state can be determined. */
    UNKNOWN
}
