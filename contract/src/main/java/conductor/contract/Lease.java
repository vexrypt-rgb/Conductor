package conductor.contract;

import java.util.Objects;

/**
 * The right to use one resource.
 *
 * @param scoped true when Conductor took it automatically for one operation (e.g. requestTravel) and gives it back
 *               when that operation ends
 */
public record Lease(long id, ResourceKey resource, ModuleId holder, Priority priority, boolean scoped) {

    public Lease {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(priority, "priority");
    }
}
