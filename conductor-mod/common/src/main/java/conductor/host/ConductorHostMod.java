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

import java.nio.file.Path;

/**
 * Fabric boot shim, part 2 (client): with {@link ConductorPreLaunch}, the only Fabric-specific code in Conductor. It answers two questions Fabric knows the answer
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
            renderLayers();
            start();
        } catch (Throwable t) {
            // never take the game down: Conductor is an add-on
            LOG.error("Conductor failed to start and is NOT running; the game continues without it", t);
        }
    }

    /** Content blocks are registered by now (before the registries froze); give see-through ones their layer. */
    private static void renderLayers() {
        try {
            int n = VersionRenderLayers.apply(conductor.runtime.ContentInstaller.renderLayers());
            if (n > 0) {
                LOG.info("Conductor content: {} block(s) drawn with a see-through render layer", n);
            }
            int m = VersionMobRenderers.apply(conductor.runtime.ContentInstaller.mobs());
            if (m > 0) {
                LOG.info("Conductor content: renderers for {} mob(s)", m);
            }
        } catch (Throwable t) {
            LOG.warn("Conductor could not set up module block layers or mob renderers", t);
        }
    }

    private void start() {
        Path dir = ConductorPreLaunch.modulesDir();
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
