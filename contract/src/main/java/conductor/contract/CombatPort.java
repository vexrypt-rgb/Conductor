package conductor.contract;

/**
 * The runtime's delegation point for combat (Stage 5). Implemented by a runtime adapter around the engine's existing
 * combat processes; Conductor never fights itself.
 * <p>
 * A combat operation steers the same movement resource as travel, so it follows the same rules: granted only while
 * the resource is AVAILABLE, stopped only on release evidence, and exclusive with travel.
 */
public interface CombatPort {

    /** Whether {@code controller} (as reported by {@link ObservationKinds#CONTROLLING_PROCESS}) is this port's own combat process. */
    boolean isOwnController(String controller);

    /** Start fighting. Must not block. */
    NavigationPort.Delegation engage(CombatGoal goal);

    /**
     * Ask the engine to stop fighting. Invocation is not confirmation. The engine's stop may stop more than combat
     * (for Ostinato it is the engine-wide cancel).
     */
    void cancel();
}
