package conductor.contract;

import java.util.List;
import java.util.Objects;

/**
 * One discoverable capability.
 *
 * @param availability     whether the backing engine boundary is present, as the runtime integration determined it
 * @param observationKinds the kinds that {@link Conductor#observe} accepts for this capability
 * @param operations       operations a consumer may invoke. Empty in Stage 2: no operation is exposed before Stage 3.
 */
public final class CapabilityDescriptor {
    private final String id;
    private final CapabilityAvailability availability;
    private final String detail;
    private final List<ObservationKind<?>> observationKinds;
    private final List<String> operations;

    public CapabilityDescriptor(String id, CapabilityAvailability availability, String detail, List<ObservationKind<?>> observationKinds, List<String> operations) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(availability, "availability");
        detail = detail == null ? "" : detail;
        observationKinds = Jdk8.copyList(observationKinds);
        operations = Jdk8.copyList(operations);

        this.id = id;
        this.availability = availability;
        this.detail = detail;
        this.observationKinds = observationKinds;
        this.operations = operations;
    }

    public String id() { return id; }

    public CapabilityAvailability availability() { return availability; }

    public String detail() { return detail; }

    public List<ObservationKind<?>> observationKinds() { return observationKinds; }

    public List<String> operations() { return operations; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CapabilityDescriptor)) return false;
        CapabilityDescriptor other = (CapabilityDescriptor) o;
        return java.util.Arrays.deepEquals(new Object[] {this.id, this.availability, this.detail, this.observationKinds, this.operations}, new Object[] {other.id, other.availability, other.detail, other.observationKinds, other.operations});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.id, this.availability, this.detail, this.observationKinds, this.operations}); }

    @Override public String toString() { return "CapabilityDescriptor[" + "id=" + id + ", " + "availability=" + availability + ", " + "detail=" + detail + ", " + "observationKinds=" + observationKinds + ", " + "operations=" + operations + "]"; }
}
