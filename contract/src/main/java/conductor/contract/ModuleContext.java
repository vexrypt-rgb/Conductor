package conductor.contract;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * What a module receives: everything in {@link Conductor} (observation, discovery, navigation) attributed to this
 * module, plus leases, topics and services for working alongside other modules. Game thread only.
 */
public interface ModuleContext extends Conductor {

    ModuleId self();

    /** Acquire at this module's own {@link ConductorModule#priority()}. */
    LeaseResult acquire(ResourceKey resource);

    /**
     * Acquire {@code resource}.
     * <ul>
     *   <li>Free (and, for MOVEMENT, shown idle by release evidence): GRANTED.</li>
     *   <li>Held at equal or higher priority: DENIED.</li>
     *   <li>Held at lower priority: PENDING. The holder is revoked (told via {@code onLeaseRevoked}, its operations
     *       on the resource cancelled), and you get {@code onLeaseGranted} once the resource is shown free. For
     *       resources without evidence signals that happens right after the holder has been told.</li>
     *   <li>MOVEMENT not held but not shown idle (e.g. other engine activity): PENDING until it is.</li>
     * </ul>
     */
    LeaseResult acquire(ResourceKey resource, Priority priority);

    /** Give a lease back (or withdraw a PENDING request). MOVEMENT stays quarantined until evidence shows it idle. */
    void release(Lease lease);

    /** All leases currently held, by any module. */
    List<Lease> leases();

    /** Delivered to subscribers on the game thread at the end of this tick, in publish order. */
    <T> void publish(Topic<T> topic, T message);

    <T> Subscription subscribe(Topic<T> topic, Consumer<T> handler);

    /**
     * Register a chat command: when the player types the command prefix (default {@code +}) followed by
     * {@code name}, {@code handler} runs on the game thread at the end of that tick, and the line is not sent to the
     * server. Names are case-insensitive and first come, first served.
     *
     * @throws IllegalArgumentException if another module or Conductor already has this name
     */
    Subscription command(String name, String help, Consumer<CommandCall> handler);

    /** Offer a service to other modules. Withdrawn automatically when this module stops. */
    <T> void provide(ServiceKey<T> key, T service);

    <T> Optional<T> service(ServiceKey<T> key);

    /** The current value of one of this module's settings (its default until changed). */
    <T> T get(Setting<T> setting);

    /** Change one of this module's settings; saved at once. */
    <T> void set(Setting<T> setting, T value);

    /** Whether this module's optional feature {@code name} is on (see {@link ConductorModule#features()}). */
    FeatureStatus feature(String name);

    /** All of this module's optional features, as checked when it started. */
    java.util.Map<String, FeatureStatus> features();

    /**
     * Native modules only: the engine processes (as reported by CONTROLLING_PROCESS) this module drives directly.
     * While it holds MOVEMENT, their activity counts as this module's rather than as interference.
     */
    void declareEngineProcesses(java.util.Set<String> processes);
}
