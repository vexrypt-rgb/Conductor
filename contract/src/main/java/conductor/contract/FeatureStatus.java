package conductor.contract;

import java.util.List;

/**
 * Whether one {@link ModuleFeature} is on, and why.
 *
 * @param checks the presence of each required content item, as read when the module started
 */
public record FeatureStatus(String name, boolean enabled, List<ContentResult> checks, String reason) {

    public FeatureStatus {
        checks = List.copyOf(checks);
    }

    static FeatureStatus evaluate(ModuleFeature feature, List<ContentResult> checks) {
        List<String> missing = checks.stream().filter(c -> !c.isPresent())
                .map(c -> c.content() + " " + c.presence()).toList();
        boolean on = missing.isEmpty();
        return new FeatureStatus(feature.name(), on, checks, on ? "all required content present" : "off: " + String.join(", ", missing));
    }
}
