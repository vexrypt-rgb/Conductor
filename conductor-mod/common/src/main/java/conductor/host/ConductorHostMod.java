package conductor.host;

import conductor.contract.FileSettingsStore;
import conductor.contract.InMemoryDiagnosticSink;
import conductor.runtime.ConductorBootstrap;
import conductor.runtime.Hooks;
import conductor.runtime.ModuleDiscovery;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fabric boot shim: the only Fabric-specific code in Conductor. It answers two questions Fabric knows the answer
 * to (where the game directory is, and when client code may start) and hands over to loader-free code. Conductor's
 * game hooks are Mixins (conductor.mixins.json), which Fabric, Quilt, NeoForge and Forge all load. Conductor needs no
 * other mod; a movement engine (Ostinato) is used when installed.
 * <p>
 * Modules are not Fabric mods. They are plain jars with a {@code META-INF/services/conductor.contract.ConductorModule}
 * entry, put in {@code <game dir>/conductor/modules} (or the folder named by {@code -Dconductor.modulesDir}). See
 * {@link ModuleDiscovery}. Module settings are kept in {@code <game dir>/conductor/config} (or
 * {@code -Dconductor.configDir}). A shim for another loader would make the same calls.
 */
public final class ConductorHostMod implements ClientModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger("conductor");

    @Override
    public void onInitializeClient() {
        try {
            Hooks.lineDrawer(new VersionLineDrawer()); // overlays, drawn by this version's render hook
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
