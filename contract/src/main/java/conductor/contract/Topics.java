package conductor.contract;

/** Topics Conductor itself publishes. */
public final class Topics {

    private Topics() {}

    public static final Topic<LeaseEvent> LEASES = new Topic<>("conductor:leases", LeaseEvent.class);
    public static final Topic<ModuleEvent> MODULES = new Topic<>("conductor:modules", ModuleEvent.class);
}
