package conductor.contract;

/**
 * Fixed priority levels for leases. A request preempts the holder only with a strictly higher level; at equal or
 * lower level the holder keeps the resource.
 */
public enum Priority {
    /** Idle or cosmetic work: anything may take over. */
    BACKGROUND,
    /** Ordinary tasks. */
    NORMAL,
    /** Urgent tasks, e.g. a fight starting. */
    HIGH,
    /** Keeping the player alive, e.g. escaping lava or drowning. */
    SAFETY
}
