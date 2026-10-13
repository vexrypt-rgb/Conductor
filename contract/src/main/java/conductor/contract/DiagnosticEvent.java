package conductor.contract;

/**
 * One diagnostic record.
 * <p>
 * Events recorded from a thread other than the game thread carry {@code tick = -1} and {@code context = null}.
 * Reading either would mean touching game-thread state from the wrong thread.
 *
 * @param subject the operation or observation kind the event concerns
 */
public final class DiagnosticEvent {
    private final Type type;
    private final String thread;
    private final long tick;
    private final ContextId context;
    private final String subject;
    private final String detail;

    public DiagnosticEvent(Type type, String thread, long tick, ContextId context, String subject, String detail) {
        this.type = type;
        this.thread = thread;
        this.tick = tick;
        this.context = context;
        this.subject = subject;
        this.detail = detail;
    }

    public Type type() { return type; }

    public String thread() { return thread; }

    public long tick() { return tick; }

    public ContextId context() { return context; }

    public String subject() { return subject; }

    public String detail() { return detail; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiagnosticEvent)) return false;
        DiagnosticEvent other = (DiagnosticEvent) o;
        return java.util.Arrays.deepEquals(new Object[] {this.type, this.thread, this.tick, this.context, this.subject, this.detail}, new Object[] {other.type, other.thread, other.tick, other.context, other.subject, other.detail});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.type, this.thread, this.tick, this.context, this.subject, this.detail}); }

    @Override public String toString() { return "DiagnosticEvent[" + "type=" + type + ", " + "thread=" + thread + ", " + "tick=" + tick + ", " + "context=" + context + ", " + "subject=" + subject + ", " + "detail=" + detail + "]"; }


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
        INTERFERENCE,
        /** Module platform: subject is the module id. */
        MODULE_STATE_CHANGED,
        /** Module platform: subject is the resource. */
        LEASE_CHANGED,
        /** Module platform: a module threw, or broke a platform rule. */
        MODULE_FAULT,
        /** Module platform: an optional module feature was checked; detail says ON or OFF and why. */
        MODULE_FEATURE,
        /** A one-shot action was performed or refused. */
        ACTION,
        /** A module setting was loaded, changed, or could not be read or saved. */
        SETTING
    }

    static DiagnosticEvent onGameThread(Type type, long tick, ContextId context, String subject, String detail) {
        return new DiagnosticEvent(type, Thread.currentThread().getName(), tick, context, subject, detail);
    }

    static DiagnosticEvent offThread(String subject, String detail) {
        return new DiagnosticEvent(Type.OFF_THREAD_VIOLATION, Thread.currentThread().getName(), -1, null, subject, detail);
    }
}
