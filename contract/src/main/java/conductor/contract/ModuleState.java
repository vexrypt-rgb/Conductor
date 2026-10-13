package conductor.contract;

public enum ModuleState {
    /** Registered, not started yet. */
    DISCOVERED,
    /** Waiting for a service it requires. */
    BLOCKED,
    RUNNING,
    /** Stopped by the management service; can be enabled again. */
    DISABLED,
    /** Threw an exception; stopped. Can be enabled again. */
    FAILED
}
