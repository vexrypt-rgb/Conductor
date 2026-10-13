package conductor.runtime;

import conductor.contract.ContentPlan;

import java.nio.file.Path;
import java.util.Map;

/**
 * The content plan, handed over by the boot shim before the game starts. This class touches no Minecraft class, so
 * it is safe to use before Minecraft is loaded; {@link ContentInstaller} does the game work later.
 */
public final class ContentHooks {

    private static volatile ContentPlan plan = new ContentPlan(java.util.List.of(), java.util.List.of());
    private static volatile Map<String, Path> jars = Map.of();

    private ContentHooks() {}

    /** Before the game starts (pre-launch). {@code jars}: module id to the jar its assets and data come from. */
    public static void plan(ContentPlan contentPlan, Map<String, Path> moduleJars) {
        plan = contentPlan;
        jars = Map.copyOf(moduleJars);
    }

    public static ContentPlan plan() {
        return plan;
    }

    static Map<String, Path> jars() {
        return jars;
    }
}
