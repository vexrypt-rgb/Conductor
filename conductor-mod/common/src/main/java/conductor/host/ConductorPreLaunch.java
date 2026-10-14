package conductor.host;

import conductor.contract.ContentPlan;
import conductor.runtime.ContentHooks;
import conductor.runtime.ModuleDiscovery;
import conductor.runtime.ServerModules;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fabric boot shim, part 1 (client and dedicated server): finds content modules before the game starts, so they can
 * be registered before Minecraft locks its registries, and tells the runtime where server modules are. Loads no
 * Minecraft class.
 */
public final class ConductorPreLaunch implements PreLaunchEntrypoint {

    private static final Logger LOG = LogManager.getLogger("conductor");
    @Override
    public void onPreLaunch() {
        try {
            ServerModules.configure(modulesDir(), ConductorPreLaunch.class.getClassLoader()); // found when a server first starts
        } catch (Throwable t) {
            LOG.error("Conductor could not set up server modules; continuing without them", t);
        }
        try {
            ModuleDiscovery.Found found = discoverContent();
            ContentPlan plan = ContentPlan.of(found.content());
            ContentHooks.plan(plan, found.jars());
            if (!found.content().isEmpty()) {
                LOG.info("Conductor content modules: {} found, {} accepted {}", found.content().size(), plan.entries().size(),
                        plan.entries().stream().map(ContentPlan.Entry::namespace).collect(java.util.stream.Collectors.toList()));
            }
        } catch (Throwable t) {
            LOG.error("Conductor could not look for modules; continuing without them", t);
        }
    }

    static Path modulesDir() {
        String override = System.getProperty("conductor.modulesDir");
        return override != null ? Path.of(override) : FabricLoader.getInstance().getGameDir().resolve("conductor/modules");
    }

    static ModuleDiscovery.Found discoverContent() {
        Path dir = modulesDir();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            LOG.warn("Conductor cannot create the modules folder {}: {}", dir, e.toString());
        }
        ModuleDiscovery.Found f = ModuleDiscovery.discoverContent(dir, ConductorPreLaunch.class.getClassLoader());
        f.problems().forEach(p -> LOG.warn("Conductor module problem: {}", p));
        return f;
    }
}
