package conductor.host;

import conductor.contract.FileSettingsStore;
import conductor.contract.InMemoryDiagnosticSink;
import conductor.runtime.ostinato.ConductorBootstrap;
import conductor.runtime.ostinato.ModuleDiscovery;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fabric boot shim: the only Fabric-specific code in Conductor. It answers two questions Fabric knows the answer
 * to (where the game directory is, and when client code may start) and hands over to loader-free code.
 * <p>
 * Modules are not Fabric mods. They are plain jars with a {@code META-INF/services/conductor.contract.ConductorModule}
 * entry, put in {@code <game dir>/conductor/modules} (or the folder named by {@code -Dconductor.modulesDir}). See
 * {@link ModuleDiscovery}. Module settings are kept in {@code <game dir>/conductor/config} (or
 * {@code -Dconductor.configDir}). A shim for another loader would make the same calls.
 */
public final class ConductorHostMod implements ClientModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger("conductor");

    /**
     * Ostinato classes Conductor links against. Ostinato's obfuscated builds (standalone, api) rename the internal
     * ones, so Conductor needs Ostinato's unoptimized build.
     */
    static final java.util.List<String> NEEDED = java.util.List.of(
            "baritone.api.BaritoneAPI", "baritone.api.event.listener.IGameEventListener",
            "baritone.Baritone", "baritone.movement.MovementBackends", "baritone.utils.IRenderer");

    @Override
    public void onInitializeClient() {
        java.util.List<String> missing = NEEDED.stream().filter(name -> {
            try {
                Class.forName(name, false, ConductorHostMod.class.getClassLoader());
                return false;
            } catch (ClassNotFoundException | LinkageError e) {
                return true;
            }
        }).toList();
        if (!missing.isEmpty()) {
            LOG.error("Conductor is NOT running: Ostinato's classes {} are not there. Conductor needs Ostinato's "
                    + "unoptimized build (ostinato-mc<version>-unoptimized-fabric-*.jar); the standalone and api builds "
                    + "rename them. The game continues without Conductor.", missing);
            return;
        }
        try {
            start();
        } catch (Throwable t) {
            // never take the game down: Conductor is an add-on
            LOG.error("Conductor failed to start and is NOT running; the game continues without it", t);
        }
    }

    private void start() {
        String override = System.getProperty("conductor.modulesDir");
        Path dir = override != null ? Path.of(override) : FabricLoader.getInstance().getGameDir().resolve("conductor/modules");
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            LOG.warn("Conductor cannot create the modules folder {}: {}", dir, e.toString());
        }
        ModuleDiscovery.Found found = ModuleDiscovery.discover(dir, ConductorHostMod.class.getClassLoader());
        found.problems().forEach(p -> LOG.warn("Conductor module problem: {}", p));
        if (found.modules().isEmpty()) {
            LOG.info("Conductor loaded; no modules in {} or on the classpath", dir);
            return;
        }
        LOG.info("Conductor starting {} module(s) from {} and the classpath: {}", found.modules().size(), dir, found.modules().keySet());
        String configOverride = System.getProperty("conductor.configDir");
        Path config = configOverride != null ? Path.of(configOverride) : FabricLoader.getInstance().getGameDir().resolve("conductor/config");
        ConductorBootstrap.start(found.modules(), new InMemoryDiagnosticSink(50_000), new FileSettingsStore(config));
    }
}
