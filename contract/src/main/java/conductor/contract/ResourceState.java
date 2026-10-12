package conductor.contract;

/**
 * Smallest representation of the movement resource, as the specification requires. Anything other than proven
 * available is {@link #QUARANTINED}: in use, possibly still controlled by an earlier operation, controlled by
 * something else, or simply not yet shown quiet.
 */
public enum ResourceState {
    AVAILABLE,
    QUARANTINED
}
