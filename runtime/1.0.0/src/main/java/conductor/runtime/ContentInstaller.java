package conductor.runtime;

import conductor.contract.ContentPlan;

import java.util.Map;

/**
 * Content modules on Minecraft 1.0.0: not supported. 1.0 numbers its blocks and items in fixed arrays of 256 and 32000
 * and has no data packs, resource packs per mod, or registries to add to, so declared content is reported and left
 * out. Behaviour modules and server modules run as on every other version.
 */
public final class ContentInstaller {

    public static final String MINECRAFT = "1.0.0";

    private static boolean reported;

    private ContentInstaller() {}

    /** Says once, in the log, which content modules are left out. Safe on the client and the dedicated server. */
    public static synchronized void reportUnsupported() {
        if (reported) {
            return;
        }
        reported = true;
        ContentPlan plan = ContentHooks.plan();
        if (!plan.entries().isEmpty()) {
            ConductorLog.warn("Conductor content modules are not supported on Minecraft 1.0.0; left out: {}",
                    plan.entries().stream().map(ContentPlan.Entry::namespace).toList());
        }
    }

    /** No content blocks, so none needs a see-through layer. */
    public static Map<String, Object> renderLayers() {
        reportUnsupported();
        return Map.of();
    }

    /** No content mobs. */
    public static Map<String, Object> mobs() {
        return Map.of();
    }
}
