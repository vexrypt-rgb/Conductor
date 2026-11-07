package conductor.contract;

import java.util.List;

/**
 * Whether one {@link ModuleFeature} is on, and why.
 *
 * @param checks the presence of each required content item, as read when the module started
 */
public final class FeatureStatus {
    private final String name;
    private final boolean enabled;
    private final List<ContentResult> checks;
    private final String reason;

    public FeatureStatus(String name, boolean enabled, List<ContentResult> checks, String reason) {
        checks = Jdk8.copyList(checks);

        this.name = name;
        this.enabled = enabled;
        this.checks = checks;
        this.reason = reason;
    }

    public String name() { return name; }

    public boolean enabled() { return enabled; }

    public List<ContentResult> checks() { return checks; }

    public String reason() { return reason; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FeatureStatus)) return false;
        FeatureStatus other = (FeatureStatus) o;
        return java.util.Arrays.deepEquals(new Object[] {this.name, this.enabled, this.checks, this.reason}, new Object[] {other.name, other.enabled, other.checks, other.reason});
    }

    @Override public int hashCode() { return java.util.Arrays.deepHashCode(new Object[] {this.name, this.enabled, this.checks, this.reason}); }

    @Override public String toString() { return "FeatureStatus[" + "name=" + name + ", " + "enabled=" + enabled + ", " + "checks=" + checks + ", " + "reason=" + reason + "]"; }


    static FeatureStatus evaluate(ModuleFeature feature, List<ContentResult> checks) {
        List<String> missing = checks.stream().filter(c -> !c.isPresent())
                .map(c -> c.content() + " " + c.presence()).collect(java.util.stream.Collectors.toList());
        boolean on = missing.isEmpty();
        return new FeatureStatus(feature.name(), on, checks, on ? "all required content present" : "off: " + String.join(", ", missing));
    }
}
