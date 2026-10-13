package conductor.contract;

import java.util.List;

/** A module as the management service sees it. */
public final class ModuleInfo {
    private final ModuleId id;
    private final ModuleState state;
    private final Priority priority;
    private final List<Lease> leases;
    private final List<String> provides;
    private final List<String> requires;
    private final String detail;

    public ModuleInfo(ModuleId id, ModuleState state, Priority priority, List<Lease> leases, List<String> provides, List<String> requires, String detail) {
        leases = Jdk8.copyList(leases);
        provides = Jdk8.copyList(provides);
        requires = Jdk8.copyList(requires);

        this.id = id;
        this.state = state;
        this.priority = priority;
        this.leases = leases;
        this.provides = provides;
        this.requires = requires;
        this.detail = detail;
    }

    public ModuleId id() { return id; }

    public ModuleState state() { return state; }

    public Priority priority() { return priority; }

    public List<Lease> leases() { return leases; }

    public List<String> provides() { return provides; }

    public List<String> requires() { return requires; }

    public String detail() { return detail; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModuleInfo)) return false;
        ModuleInfo other = (ModuleInfo) o;
        return java.util.Arrays.deepEquals(new Object[] {this.id, this.state, this.priority, this.leases, this.provides, this.requires, this.detail}, new Object[] {other.id, other.state, other.priority, other.leases, other.provides, other.requires, other.detail});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.id, this.state, this.priority, this.leases, this.provides, this.requires, this.detail}); }

    @Override public String toString() { return "ModuleInfo[" + "id=" + id + ", " + "state=" + state + ", " + "priority=" + priority + ", " + "leases=" + leases + ", " + "provides=" + provides + ", " + "requires=" + requires + ", " + "detail=" + detail + "]"; }
}
