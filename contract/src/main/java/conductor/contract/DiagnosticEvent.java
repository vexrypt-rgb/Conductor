package conductor.contract;

/**
 * One diagnostic record.
 * <p>
 * Events recorded from a thread other than the game thread carry {@code tick = -1} and {@code context = null}.
 * Reading either would mean touching game-thread state from the wrong thread.
 *
 * @param subject the operation or observation kind the event concerns
 */
public record DiagnosticEvent(Type type, String thread, long tick, ContextId context, String subject, String detail) {

    public enum Type {
        CONTEXT_ESTABLISHED,
        CONTEXT_INVALIDATED,
        CONTEXT_ENDED,
        OBSERVATION_REJECTED,
        /** The runtime failed while sampling; the observation became {@link Availability#UNKNOWN}. */
        PROVIDER_FAULT,
        OFF_THREAD_VIOLATION,
        OPERATION_REJECTED,
        AUTHORITY_GRANTED,
        AUTHORITY_INVALIDATED,
        OPERATION_STATE_CHANGED,
        /** The engine's cancel was invoked. Invocation is not confirmation. */
        CANCEL_INVOKED,
        /** Authority released because the resource was released on evidence. */
        AUTHORITY_RELEASED,
        RESOURCE_STATE_CHANGED,
        /** Subject is the {@link InterferenceClass}; detail names the source. */
        INTERFERENCE
    }

    static DiagnosticEvent onGameThread(Type type, long tick, ContextId context, String subject, String detail) {
        return new DiagnosticEvent(type, Thread.currentThread().getName(), tick, context, subject, detail);
    }

    static DiagnosticEvent offThread(String subject, String detail) {
        return new DiagnosticEvent(Type.OFF_THREAD_VIOLATION, Thread.currentThread().getName(), -1, null, subject, detail);
    }
}
