package conductor.contract;

/**
 * Navigation delegation provided by the runtime integration: one held instance of the engine's existing navigation
 * boundary, used by exactly one consumer. Called only on the game thread.
 */
public interface NavigationPort {

    /**
     * Whether a travel request would run on a backend whose stop can be observed (stage1-audit §7.2). Read-only.
     * When false the contract rejects the request without calling the engine.
     */
    boolean routesToObservableBackend();

    /** Whether {@code controller}, as reported by the CONTROLLING_PROCESS observation, is the one travel requests use. */
    boolean isOwnController(String controller);

    /** Delegate one travel request to the engine. May throw. */
    Delegation travel(NavigationGoal goal);

    /**
     * Invoke the engine's existing cancel. Best effort: invocation is not confirmation. The engine's cancel may have
     * effects wider than this operation (on Ostinato it stops every pathing process, stage1-audit B6).
     */
    void cancel();

    /** @param backend the engine's name for the backend that took the request */
    record Delegation(boolean accepted, String backend, boolean backendObservable, String detail) {}
}
