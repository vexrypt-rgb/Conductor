package conductor.contract;

import java.util.List;
import java.util.Optional;

/**
 * Outcome of {@link Conductor#discover()}.
 * <p>
 * The result describes what was true in {@link #context()} at {@link #tick()}. It does not stay valid across a
 * context change; discover again after one.
 */
public final class DiscoveryResult {
    private final RejectionReason rejection;
    private final String contractVersion;
    private final Optional<ContextId> context;
    private final long tick;
    private final List<CapabilityDescriptor> capabilities;

    public DiscoveryResult(RejectionReason rejection, String contractVersion, Optional<ContextId> context, long tick, List<CapabilityDescriptor> capabilities) {
        capabilities = Jdk8.copyList(capabilities);

        this.rejection = rejection;
        this.contractVersion = contractVersion;
        this.context = context;
        this.tick = tick;
        this.capabilities = capabilities;
    }

    public RejectionReason rejection() { return rejection; }

    public String contractVersion() { return contractVersion; }

    public Optional<ContextId> context() { return context; }

    public long tick() { return tick; }

    public List<CapabilityDescriptor> capabilities() { return capabilities; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiscoveryResult)) return false;
        DiscoveryResult other = (DiscoveryResult) o;
        return java.util.Arrays.deepEquals(new Object[] {this.rejection, this.contractVersion, this.context, this.tick, this.capabilities}, new Object[] {other.rejection, other.contractVersion, other.context, other.tick, other.capabilities});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.rejection, this.contractVersion, this.context, this.tick, this.capabilities}); }

    @Override public String toString() { return "DiscoveryResult[" + "rejection=" + rejection + ", " + "contractVersion=" + contractVersion + ", " + "context=" + context + ", " + "tick=" + tick + ", " + "capabilities=" + capabilities + "]"; }


    public boolean isAccepted() {
        return rejection == null;
    }
}
