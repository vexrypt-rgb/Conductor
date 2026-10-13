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
public record CapabilityDescriptor(String id, CapabilityAvailability availability, String detail,
                                   List<ObservationKind<?>> observationKinds, List<String> operations) {

    public CapabilityDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(availability, "availability");
        detail = detail == null ? "" : detail;
        observationKinds = List.copyOf(observationKinds);
        operations = List.copyOf(operations);
    }
}
