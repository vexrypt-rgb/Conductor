package conductor.contract;

import java.util.Objects;

/**
 * The right to use one resource.
 *
 * @param scoped true when Conductor took it automatically for one operation (e.g. requestTravel) and gives it back
 *               when that operation ends
 */
public final class Lease {
    private final long id;
    private final ResourceKey resource;
    private final ModuleId holder;
    private final Priority priority;
    private final boolean scoped;

    public Lease(long id, ResourceKey resource, ModuleId holder, Priority priority, boolean scoped) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(priority, "priority");

        this.id = id;
        this.resource = resource;
        this.holder = holder;
        this.priority = priority;
        this.scoped = scoped;
    }

    public long id() { return id; }

    public ResourceKey resource() { return resource; }

    public ModuleId holder() { return holder; }

    public Priority priority() { return priority; }

    public boolean scoped() { return scoped; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Lease)) return false;
        Lease other = (Lease) o;
        return java.util.Arrays.deepEquals(new Object[] {this.id, this.resource, this.holder, this.priority, this.scoped}, new Object[] {other.id, other.resource, other.holder, other.priority, other.scoped});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.id, this.resource, this.holder, this.priority, this.scoped}); }

    @Override public String toString() { return "Lease[" + "id=" + id + ", " + "resource=" + resource + ", " + "holder=" + holder + ", " + "priority=" + priority + ", " + "scoped=" + scoped + "]"; }
}
