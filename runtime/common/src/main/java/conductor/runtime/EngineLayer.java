package conductor.runtime;

import conductor.contract.CapabilityDescriptor;
import conductor.contract.CombatPort;
import conductor.contract.NavigationPort;
import conductor.contract.ObservationKind;
import conductor.contract.Sample;

import java.util.List;
import java.util.Optional;

/**
 * An optional movement engine (Ostinato, or any other bot engine) that adds travel, combat and its own signals to
 * Conductor's core runtime. Conductor runs without one; then there is no navigation or combat, and nothing to wait
 * for before the movement resource is free. Game thread only.
 */
public interface EngineLayer {

    /** Engine name for discovery and diagnostics, e.g. {@code Ostinato}. */
    String name();

    /** The capabilities this engine adds (navigation, combat, its signals). */
    List<CapabilityDescriptor> capabilities();

    /** A sample for a kind this engine provides, or null when the kind is not the engine's. */
    <T> Sample<T> sample(ObservationKind<T> kind);

    Optional<NavigationPort> navigation();

    Optional<CombatPort> combat();
}
