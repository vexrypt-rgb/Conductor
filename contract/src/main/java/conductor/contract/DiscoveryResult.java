package conductor.contract;

import java.util.List;
import java.util.Optional;

/**
 * Outcome of {@link Conductor#discover()}.
 * <p>
 * The result describes what was true in {@link #context()} at {@link #tick()}. It does not stay valid across a
 * context change; discover again after one.
 */
public record DiscoveryResult(RejectionReason rejection, String contractVersion, Optional<ContextId> context,
                              long tick, List<CapabilityDescriptor> capabilities) {

    public DiscoveryResult {
        capabilities = List.copyOf(capabilities);
    }

    public boolean isAccepted() {
        return rejection == null;
    }
}
